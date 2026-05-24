package org.example.ApplicationLayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.InfrastructureLayer.InMemoryKeyedLock;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;


public class UserServiceTest {

    private IUserRepository userRepositoryMock;
    private IAuthenticationGateway authGatewayMock;
    private UserService userService;
    private INotifier notifier;

    @Before
    public void setUp() {
        userRepositoryMock = mock(IUserRepository.class);
        authGatewayMock = mock(IAuthenticationGateway.class);
        // FIX: Mock the notifier so a valid mock instance is injected into the service 
        notifier = mock(INotifier.class); 
        userService = new UserService(userRepositoryMock, authGatewayMock, notifier,
                new InMemoryKeyedLock<>());
    }

    // ================================================================
    // Register
    // ================================================================

    @Test
    public void testRegister_Success() {
        RegisterRequest request = new RegisterRequest();
        request.username = "JohnDoe";
        request.email = "john@example.com";
        request.plainPassword = "Password123";
        request.age = 25;

        when(userRepositoryMock.existsByEmail(request.email)).thenReturn(false);
        when(authGatewayMock.verifyUserDetails(
                request.email,
                request.plainPassword,
                request.age,
                request.username
        )).thenReturn(true);
        when(authGatewayMock.hashPassword(request.plainPassword)).thenReturn("hashed_password");

        UserResponse response = userService.register(request);

        assertNotNull(response);
        assertNotNull(userId(response));
        assertEquals("JohnDoe", username(response));
        assertEquals("john@example.com", email(response));
        assertEquals(25, age(response));

        verify(userRepositoryMock, times(1)).existsByEmail(request.email);
        verify(authGatewayMock, times(1)).verifyUserDetails(
                request.email,
                request.plainPassword,
                request.age,
                request.username
        );
        verify(authGatewayMock, times(1)).hashPassword(request.plainPassword);
        verify(userRepositoryMock, times(1)).add(any(User.class));
    }

    @Test
    public void testRegister_RequestIsNull_ThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register((RegisterRequest) null)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(authGatewayMock);
    }

    @Test
    public void testRegister_MissingEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.username = "JohnDoe";
        request.email = "";
        request.plainPassword = "Password123";
        request.age = 25;

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(request)
        );

        verify(userRepositoryMock, never()).add(any(User.class));
    }

    @Test
    public void testRegister_MissingPassword_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.username = "JohnDoe";
        request.email = "john@example.com";
        request.plainPassword = "";
        request.age = 25;

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(request)
        );

        verify(userRepositoryMock, never()).add(any(User.class));
    }

    @Test
    public void testRegister_MissingUsername_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.username = "";
        request.email = "john@example.com";
        request.plainPassword = "Password123";
        request.age = 25;

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(request)
        );

        verify(userRepositoryMock, never()).add(any(User.class));
    }

    @Test
    public void testRegister_EmailAlreadyExists_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.username = "JohnDoe";
        request.email = "taken@example.com";
        request.plainPassword = "Password123";
        request.age = 25;

        when(userRepositoryMock.existsByEmail(request.email)).thenReturn(true);
        when(authGatewayMock.verifyUserDetails(
            request.email,
            request.plainPassword,
            request.age,
            request.username
        )).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(request)
        );

        verify(userRepositoryMock, times(1)).existsByEmail(request.email);
        verify(authGatewayMock, never()).verifyUserDetails(any(), any(), anyInt(), any());
        verify(userRepositoryMock, never()).add(any(User.class));
    }

    @Test
    public void testRegister_InvalidUserDetails_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.username = "JohnDoe";
        request.email = "john@example.com";
        request.plainPassword = "Password123";
        request.age = 25;

        when(userRepositoryMock.existsByEmail(request.email)).thenReturn(false);
        when(authGatewayMock.verifyUserDetails(
                request.email,
                request.plainPassword,
                request.age,
                request.username
        )).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(request)
        );

        verify(userRepositoryMock, never()).add(any(User.class));
    }

    // ================================================================
    // Login
    // ================================================================

    @Test
    public void testLogin_Success() {
        LoginRequest request = new LoginRequest();
        request.email = "john@example.com";
        request.plainPassword = "Password123";

        UUID mockId = UUID.randomUUID();
        User existingUser = new User(
                mockId,
                "JohnDoe",
                request.email,
                "hashed_password",
                25
        );

        when(userRepositoryMock.findByEmail(request.email)).thenReturn(Optional.of(existingUser));
        when(authGatewayMock.verifyPassword(request.plainPassword, "hashed_password")).thenReturn(true);

        UserResponse response = userService.login(request);

        assertNotNull(response);
        assertEquals(mockId, userId(response));
        assertEquals("JohnDoe", username(response));
        assertEquals("john@example.com", email(response));
        assertEquals(UserStatus.LOGGED_IN, existingUser.getStatus());
        assertEquals(UserStatus.LOGGED_IN.toString(), status(response));

        verify(userRepositoryMock, times(1)).findByEmail(request.email);
        verify(authGatewayMock, times(1)).verifyPassword(request.plainPassword, "hashed_password");
        verify(userRepositoryMock, never()).add(any(User.class));
    }

    @Test
    public void testLogin_RequestIsNull_ThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.login((LoginRequest) null)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(authGatewayMock);
    }

    @Test
    public void testLogin_MissingEmail_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.email = "";
        request.plainPassword = "Password123";

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.login(request)
        );

        verify(userRepositoryMock, never()).findByEmail(anyString());
    }

    @Test
    public void testLogin_MissingPassword_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.email = "john@example.com";
        request.plainPassword = "";

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.login(request)
        );

        verify(userRepositoryMock, never()).findByEmail(anyString());
    }

    @Test
    public void testLogin_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.email = "ghost@example.com";
        request.plainPassword = "Password123";

        when(userRepositoryMock.findByEmail(request.email)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.login(request)
        );

        verify(userRepositoryMock, times(1)).findByEmail(request.email);
        verify(authGatewayMock, times(1)).verifyPassword(anyString(), anyString());
    }

    @Test
    public void testLogin_WrongPassword_ThrowsExceptionAndKeepsUserLoggedOut() {
        LoginRequest request = new LoginRequest();
        request.email = "john@example.com";
        request.plainPassword = "WrongPassword";

        User existingUser = new User(
                UUID.randomUUID(),
                "JohnDoe",
                request.email,
                "hashed_password",
                25
        );

        when(userRepositoryMock.findByEmail(request.email)).thenReturn(Optional.of(existingUser));
        when(authGatewayMock.verifyPassword(request.plainPassword, "hashed_password")).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.login(request)
        );

        assertEquals(UserStatus.NOT_LOGGED_IN, existingUser.getStatus());
        verify(userRepositoryMock, never()).add(any(User.class));
    }

    // ================================================================
    // Logout
    // ================================================================

    @Test
    public void testLogout_Success() {
        UUID memberId = UUID.randomUUID();

        User loggedInUser = new User(
                memberId,
                "JohnDoe",
                "john@example.com",
                "hashed_password",
                25
        );
        loggedInUser.login();

        when(userRepositoryMock.getUser(memberId)).thenReturn(Optional.of(loggedInUser));

        UserResponse response = userService.logout(memberId);

        assertNotNull(response);
        assertEquals(memberId, userId(response));
        assertEquals("JohnDoe", username(response));
        assertEquals("john@example.com", email(response));
        assertEquals(UserStatus.NOT_LOGGED_IN, loggedInUser.getStatus());
        assertEquals(UserStatus.NOT_LOGGED_IN.toString(), status(response));

        verify(userRepositoryMock, times(1)).getUser(memberId);
        verify(userRepositoryMock, never()).add(any(User.class));
    }

    @Test
    public void testLogout_NullMemberId_ThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.logout(null)
        );

        verifyNoInteractions(userRepositoryMock);
    }

    @Test
    public void testLogout_UserDoesNotExist_ThrowsException() {
        UUID fakeId = UUID.randomUUID();

        when(userRepositoryMock.getUser(fakeId)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.logout(fakeId)
        );

        verify(userRepositoryMock, times(1)).getUser(fakeId);
        verify(userRepositoryMock, never()).add(any(User.class));
    }

    @Test
    public void testLogoutThenLogin_SucceedsAfterStatusReset() {
        UUID memberId = UUID.randomUUID();

        User user = new User(
                memberId,
                "JohnDoe",
                "john@example.com",
                "hashed_password",
                25
        );
        user.login();

        when(userRepositoryMock.getUser(memberId)).thenReturn(Optional.of(user));

        UserResponse logoutResponse = userService.logout(memberId);

        assertNotNull(logoutResponse);
        assertEquals(UserStatus.NOT_LOGGED_IN, user.getStatus());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.email = "john@example.com";
        loginRequest.plainPassword = "Password123";

        when(userRepositoryMock.findByEmail(loginRequest.email)).thenReturn(Optional.of(user));
        when(authGatewayMock.verifyPassword(loginRequest.plainPassword, "hashed_password"))
                .thenReturn(true);

        UserResponse loginResponse = userService.login(loginRequest);

        assertNotNull(loginResponse);
        assertEquals(UserStatus.LOGGED_IN, user.getStatus());
        verify(userRepositoryMock, times(1)).getUser(memberId);
        verify(userRepositoryMock, never()).add(any(User.class));
        verify(userRepositoryMock, times(1)).findByEmail(loginRequest.email);
        verify(authGatewayMock, times(1)).verifyPassword(loginRequest.plainPassword, "hashed_password");
    }

    // ================================================================
    // Helpers
    // Works with either public-field DTOs or Java records.
    // ================================================================

    private UUID userId(UserResponse response) {
        return (UUID) read(response, "userId");
    }

    private String username(UserResponse response) {
        return (String) read(response, "username");
    }

    private String email(UserResponse response) {
        return (String) read(response, "email");
    }

    private String status(UserResponse response) {
        return (String) read(response, "status");
    }

    private int age(UserResponse response) {
        Object value = read(response, "age");
        return ((Number) value).intValue();
    }

    private Object read(Object target, String propertyName) {
        try {
            Field field = target.getClass().getField(propertyName);
            return field.get(target);
        } catch (NoSuchFieldException ignored) {
            try {
                Method method = target.getClass().getMethod(propertyName);
                return method.invoke(target);
            } catch (Exception e) {
                throw new AssertionError("Could not read property: " + propertyName, e);
            }
        } catch (Exception e) {
            throw new AssertionError("Could not read property: " + propertyName, e);
        }
    }
}