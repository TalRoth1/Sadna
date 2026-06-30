package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.UserAggregate.User;
import org.example.InfrastructureLayer.InMemoryKeyedLock;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Category C — Infrastructure & Concurrency hardening tests for
 * {@link UserService#register(RegisterRequest)}.
 *
 * <h2>C1 — DB-level unique-constraint violation → 409 Conflict</h2>
 * <p>Even when the application-level existence checks pass, the database's
 * unique constraints are the final guard.  If JPA fires
 * {@link DataIntegrityViolationException} (e.g. two concurrent registrations
 * on different JVM nodes, or a race between the existence check and the
 * INSERT), {@code register()} must catch it and re-throw as
 * {@link RegistrationConflictException} so the caller receives a 409 rather
 * than an unhandled 500.
 *
 * <h2>C2 — Multi-node safety net</h2>
 * <p>The in-memory {@link InMemoryKeyedLock} prevents races within a single
 * JVM but offers no protection across multiple nodes.  On a clustered
 * deployment, two instances can each acquire their own lock for the same
 * e-mail key, both pass the existence check, and then race to INSERT.  The
 * database unique constraint is the only shared guard; this test verifies
 * that its {@code DataIntegrityViolationException} is never exposed as a 500.
 *
 * <h2>C3 — Transactional boundary</h2>
 * <p>{@code UserService} must carry {@code @Transactional} at class level so
 * that the existence check and the INSERT happen in one transaction.  A
 * {@code DataIntegrityViolationException} then triggers a full rollback,
 * leaving the database in a consistent state.
 */
public class UserRegistrationInfrastructureTest {

    private IUserRepository repositoryMock;
    private IAuthenticationGateway gatewayMock;
    private UserService userService;

    @Before
    public void setUp() {
        repositoryMock = mock(IUserRepository.class);
        gatewayMock    = mock(IAuthenticationGateway.class);
        INotifier notifierMock = mock(INotifier.class);

        // Default: validation passes, no pre-existing conflict, password hashed trivially.
        when(gatewayMock.verifyUserDetails(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(true);
        when(gatewayMock.hashPassword(anyString())).thenReturn("hash");
        when(repositoryMock.existsByEmail(anyString())).thenReturn(false);
        when(repositoryMock.existsByUsername(anyString())).thenReturn(false);

        userService = new UserService(repositoryMock, gatewayMock, notifierMock,
                new InMemoryKeyedLock<>(), new EventPublisher());
    }

    // ================================================================
    // C1 — DataIntegrityViolationException → RegistrationConflictException
    // ================================================================

    /**
     * C1a: When {@code userRepository.add()} throws
     * {@link DataIntegrityViolationException} (unique constraint on e-mail or
     * username), {@code register()} must convert it to
     * {@link RegistrationConflictException} so the controller returns 409.
     */
    @Test
    public void register_whenRepositoryThrowsDataIntegrityViolation_throwsRegistrationConflictException() {
        doThrow(new DataIntegrityViolationException("uk_users_email"))
                .when(repositoryMock).add(any(User.class));

        RegisterRequest req = new RegisterRequest("alice", "alice@example.com", "Password1!", 25);

        assertThrows(
                "DataIntegrityViolationException from repository.add() must be " +
                "converted to RegistrationConflictException (C1)",
                RegistrationConflictException.class,
                () -> userService.register(req)
        );
    }

    /**
     * C1b: The conflict message exposed to the caller must be the same generic
     * string regardless of which column caused the violation, to prevent
     * e-mail/username enumeration via error messages.
     */
    @Test
    public void register_whenRepositoryThrowsDataIntegrityViolation_messageIsGeneric() {
        doThrow(new DataIntegrityViolationException("uk_users_email"))
                .when(repositoryMock).add(any(User.class));

        RegisterRequest req = new RegisterRequest("alice", "alice@example.com", "Password1!", 25);

        RegistrationConflictException ex = assertThrows(
                RegistrationConflictException.class,
                () -> userService.register(req)
        );
        assertEquals("An account with these details already exists.", ex.getMessage());
    }

    // ================================================================
    // C2 — Multi-node race: DB constraint is the last line of defence
    // ================================================================

    /**
     * C2: Both nodes pass their local existence check (no app-level lock
     * protection across JVMs); the second to INSERT gets a
     * {@link DataIntegrityViolationException}.  The service must surface this
     * as {@link RegistrationConflictException}, not as an unhandled 500.
     */
    @Test
    public void register_multiNodeRace_dbConstraintFires_returnsConflictNotServerError() {
        // Both nodes see no conflict locally…
        when(repositoryMock.existsByEmail(anyString())).thenReturn(false);
        when(repositoryMock.existsByUsername(anyString())).thenReturn(false);
        // …but the DB rejects the second INSERT.
        doThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_users_email\""))
                .when(repositoryMock).add(any(User.class));

        RegisterRequest req = new RegisterRequest("bob", "bob@example.com", "Password1!", 30);

        RegistrationConflictException ex = assertThrows(
                RegistrationConflictException.class,
                () -> userService.register(req)
        );
        assertNotNull("Conflict exception must carry a message", ex.getMessage());
    }

    // ================================================================
    // C3 — @Transactional boundary
    // ================================================================

    /**
     * C3: {@link UserService} must be annotated {@code @Transactional} at
     * class level so that every public method (including {@code register()})
     * runs inside a transaction.  This guarantees that a
     * {@link DataIntegrityViolationException} triggers a full rollback and
     * that the existence check and the INSERT are never torn apart across two
     * different transactions.
     */
    @Test
    public void userService_isAnnotatedTransactional_atClassLevel() {
        Transactional annotation = UserService.class.getAnnotation(Transactional.class);
        assertNotNull(
                "UserService must carry @Transactional at class level so that " +
                "register() runs in a transaction (C3)",
                annotation
        );
    }
}
