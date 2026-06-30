package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Category D — Persistence fidelity tests for {@link JpaUserRepository}.
 *
 * <h2>D1 — Display username lost on persist / reload</h2>
 * <p>The {@code UserEntity.username} column currently stores the login
 * identifier (email address). When rehydrating a {@link User}, both the
 * {@code username} and {@code email} fields are set to that same value, so
 * the human-readable display name (e.g. "alice") is lost.
 *
 * <h2>D2 — Age not persisted</h2>
 * <p>{@code UserEntity} has no {@code age} column. {@code toDomain()} hardcodes
 * {@code age = 0}, so every user is reloaded with age 0 regardless of what
 * was registered.
 *
 * <h2>D3 — {@code existsByUsername} is a no-op</h2>
 * <p>{@code JpaUserRepository.existsByUsername("alice")} calls
 * {@code userJpa.existsByUsername("alice")}, but the DB column stores
 * {@code "alice@example.com"} (the email), so the query never matches a
 * registered user by display name.
 */
@ExtendWith(MockitoExtension.class)
class JpaUserRepositoryTest {

    @Mock SpringDataUserRepository userJpa;
    @Mock SpringDataAdminRepository adminJpa;
    @Mock SpringDataCompanyMemberRepository companyMemberJpa;
    @Mock SpringDataInvitationRepository invitationJpa;

    private JpaUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaUserRepository(userJpa, adminJpa, companyMemberJpa, invitationJpa);
    }

    // ================================================================
    // D1 — add() stores email and display-username separately
    // ================================================================

    /**
     * D1a: {@code add()} must save the display username in its own field and
     * the email in a separate field, so that a subsequent load can restore
     * both correctly. Before the fix, both fields map to the same identifier.
     */
    @Test
    void add_savesEmailAndDisplayUsernameAsSeparateFields() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "alice", "alice@example.com", "hash", 25);
        user.login();

        when(userJpa.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        // deleteByApointeeUsernameIn is void — Mockito stubs it as a no-op by default

        repository.add(user);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userJpa).save(captor.capture());
        UserEntity saved = captor.getValue();

        assertEquals("alice@example.com", saved.getEmail(),
                "D1: email must be stored in the email field");
        assertEquals("alice", saved.getUsername(),
                "D1: display username must be stored in the username field (not the email)");
        assertNotEquals(saved.getEmail(), saved.getUsername(),
                "D1: email and display username must be stored in different fields");
    }

    // ================================================================
    // D1 — getUser() restores email and display-username correctly
    // ================================================================

    /**
     * D1b: {@code getUser()} must restore the {@link User}'s {@code email}
     * and {@code username} to their original distinct values. Before the fix,
     * both are set to the stored identifier (email), losing the display name.
     */
    @Test
    void getUser_restoresEmailAndUsernameDistinctly() {
        UUID id = UUID.randomUUID();
        UserEntity entity = new UserEntity(
                id, "alice", "alice@example.com", "hash", UserStatus.LOGGED_IN, 25);
        when(userJpa.findById(id)).thenReturn(Optional.of(entity));
        when(companyMemberJpa.findByIdUsernameIn(anyList())).thenReturn(List.of());
        when(invitationJpa.findByApointeeUsernameIn(anyList())).thenReturn(List.of());

        User loaded = repository.getUser(id).orElseThrow();

        assertEquals("alice@example.com", loaded.getEmail(),
                "D1: email must round-trip through persist/load");
        assertEquals("alice", loaded.getUsername(),
                "D1: display username must round-trip through persist/load");
        assertNotEquals(loaded.getEmail(), loaded.getUsername(),
                "D1: email and display username must be distinct after load");
    }

    // ================================================================
    // D2 — age is persisted and reloaded correctly
    // ================================================================

    /**
     * D2a: {@code add()} must include the user's age in the saved entity.
     * Before the fix, {@code UserEntity} has no age column and the value is
     * silently dropped.
     */
    @Test
    void add_persistsAge() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "alice", "alice@example.com", "hash", 30);
        user.login();

        when(userJpa.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        // deleteByApointeeUsernameIn is void — Mockito stubs it as a no-op by default

        repository.add(user);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userJpa).save(captor.capture());

        assertEquals(30, captor.getValue().getAge(),
                "D2: age must be stored in the entity");
    }

    /**
     * D2b: {@code getUser()} must restore the user's age from the stored
     * entity. Before the fix, {@code toDomain()} hardcodes {@code age = 0}.
     */
    @Test
    void getUser_restoresAge() {
        UUID id = UUID.randomUUID();
        UserEntity entity = new UserEntity(
                id, "alice", "alice@example.com", "hash", UserStatus.LOGGED_IN, 30);
        when(userJpa.findById(id)).thenReturn(Optional.of(entity));
        when(companyMemberJpa.findByIdUsernameIn(anyList())).thenReturn(List.of());
        when(invitationJpa.findByApointeeUsernameIn(anyList())).thenReturn(List.of());

        User loaded = repository.getUser(id).orElseThrow();

        assertEquals(30, loaded.getAge(),
                "D2: age must round-trip through persist/load (not always return 0)");
    }

    // ================================================================
    // D3 — existsByUsername checks the display username, not the email
    // ================================================================

    /**
     * D3a: {@code existsByUsername("alice")} must return {@code true} when a
     * user with display name "alice" is registered. Before the fix, the DB
     * {@code username} column stores the email so the query for "alice" never
     * matches.
     */
    @Test
    void existsByUsername_returnsTrueForRegisteredDisplayName() {
        when(userJpa.existsByUsername("alice")).thenReturn(true);

        assertTrue(repository.existsByUsername("alice"),
                "D3: existsByUsername must find a user by display username");
    }

    /**
     * D3b: {@code existsByEmail} must use the email column (not the username
     * column) so that display-username lookups and email lookups are routed to
     * their respective fields. Before the fix, both methods call
     * {@code existsByUsername(identifier)}.
     */
    @Test
    void existsByEmail_usesEmailColumn() {
        when(userJpa.existsByEmail("alice@example.com")).thenReturn(true);

        assertTrue(repository.existsByEmail("alice@example.com"),
                "D3: existsByEmail must query the email column, not the username column");
    }

    /**
     * D3c: After a user is added with username="alice" and email="alice@example.com",
     * existsByUsername("alice") must find the user and existsByEmail("alice") must
     * not, and vice-versa. The two identifiers are independent.
     */
    @Test
    void existsByUsername_andExistsByEmail_areIndependent() {
        when(userJpa.existsByUsername("alice")).thenReturn(true);
        when(userJpa.existsByEmail("alice")).thenReturn(false);
        when(userJpa.existsByUsername("alice@example.com")).thenReturn(false);
        when(userJpa.existsByEmail("alice@example.com")).thenReturn(true);

        assertTrue(repository.existsByUsername("alice"),
                "D3: display username lookup must use username column");
        assertFalse(repository.existsByEmail("alice"),
                "D3: email lookup of a display name must return false");
        assertFalse(repository.existsByUsername("alice@example.com"),
                "D3: username lookup of an email address must return false");
        assertTrue(repository.existsByEmail("alice@example.com"),
                "D3: email lookup must use email column");
    }
}
