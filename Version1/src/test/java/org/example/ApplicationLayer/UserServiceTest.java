package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.LoginRequest;
import org.example.ApplicationLayer.dto.RegisterRequest;
import org.example.DomainLayer.IAuthenticationGateway;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class UserServiceTest {

    private IUserRepository userRepositoryMock;
    private IAuthenticationGateway authGatewayMock;
    private UserService userService;

    // מופעל לפני כל טסט לאיפוס המצב
    @Before
    public void setUp() {
        userRepositoryMock = mock(IUserRepository.class);
        authGatewayMock = mock(IAuthenticationGateway.class);
        userService = new UserService(userRepositoryMock, authGatewayMock);
    }

    // ==========================================
    // בדיקות עבור פעולת Register (רישום)
    // ==========================================

    @Test
    public void testRegister_Success() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.username = "JohnDoe";
        request.email = "john@example.com";
        request.plainPassword = "Password123";
        request.age = 25.0f;

        when(userRepositoryMock.existsByEmail(request.email)).thenReturn(false);
        when(authGatewayMock.verifyUserDetails(request.email, request.plainPassword, request.age, request.username)).thenReturn(true);
        when(authGatewayMock.hashPassword(request.plainPassword)).thenReturn("hashed_password");

        // Act
        AuthResponse response = userService.register(request);

        // Assert
        assertTrue("Expected registration to be successful", response.isSuccess);
        assertEquals("Register Successfully", response.message);
        assertNotNull("User ID should be generated", response.userId);

        // מוודאים שפונקציית add נקראה בדיוק פעם אחת
        verify(userRepositoryMock, times(1)).add(any(User.class));
    }

    @Test
    public void testRegister_MissingDetails() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.email = ""; // אימייל ריק
        request.plainPassword = "Password123";

        // Act
        AuthResponse response = userService.register(request);

        // Assert
        assertFalse(response.isSuccess);
        assertEquals("Missing details.", response.message);
        verify(userRepositoryMock, never()).add(any(User.class));
    }

    @Test
    public void testRegister_EmailAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.email = "taken@example.com";
        request.plainPassword = "Password123";

        when(userRepositoryMock.existsByEmail(request.email)).thenReturn(true);

        // Act
        AuthResponse response = userService.register(request);

        // Assert
        assertFalse(response.isSuccess);
        assertEquals("User Email is already exist.", response.message);
        verify(userRepositoryMock, never()).add(any(User.class));
    }

    // ==========================================
    // בדיקות עבור פעולת Login (התחברות)
    // ==========================================

    @Test
    public void testLogin_Success() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.email = "john@example.com";
        request.plainPassword = "Password123";

        UUID mockId = UUID.randomUUID();
        User existingUser = new User(mockId, "JohnDoe", request.email, "hashed_password", 25.0f);

        // שימו לב לשימוש ב-Optional בגלל שהקוד שלכם משתמש ב-orElse
        when(userRepositoryMock.findByEmail(request.email)).thenReturn(Optional.of(existingUser));
        when(authGatewayMock.verifyPassword(request.plainPassword, "hashed_password")).thenReturn(true);

        // Act
        AuthResponse response = userService.login(request);

        // Assert
        assertTrue(response.isSuccess);
        assertEquals("Login successfully", response.message);
        assertEquals(mockId, response.userId);
        assertEquals("User status should be updated to LOGGED_IN", UserStatus.LOGGED_IN, existingUser.getStatus());

        verify(userRepositoryMock, times(1)).add(existingUser); // מוודא שמרנו את הסטטוס החדש
    }

    @Test
    public void testLogin_UserNotFound() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.email = "ghost@example.com";
        request.plainPassword = "Password123";

        when(userRepositoryMock.findByEmail(request.email)).thenReturn(Optional.empty());

        // Act
        AuthResponse response = userService.login(request);

        // Assert
        assertFalse(response.isSuccess);
        assertEquals("incorrect email or password.", response.message);
        verify(authGatewayMock, never()).verifyPassword(anyString(), anyString());
    }

    @Test
    public void testLogin_WrongPassword() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.email = "john@example.com";
        request.plainPassword = "WrongPassword";

        User existingUser = new User(UUID.randomUUID(), "JohnDoe", request.email, "hashed_password", 25.0f);

        when(userRepositoryMock.findByEmail(request.email)).thenReturn(Optional.of(existingUser));
        when(authGatewayMock.verifyPassword(request.plainPassword, "hashed_password")).thenReturn(false);

        // Act
        AuthResponse response = userService.login(request);

        // Assert
        assertFalse(response.isSuccess);
        assertEquals("incorrect email or password", response.message);
        assertEquals("Status must remain NOT_LOGGED_IN", UserStatus.NOT_LOGGED_IN, existingUser.getStatus());
        verify(userRepositoryMock, never()).add(any(User.class));
    }

    // ==========================================
    // בדיקות עבור פעולת Logout (התנתקות)
    // ==========================================

    @Test
    public void testLogout_Success() {
        // Arrange
        UUID memberId = UUID.randomUUID();
        User loggedInUser = new User(memberId, "JohnDoe", "john@example.com", "hashed_password", 25.0f);
        // "מחברים" את המשתמש בכוח כדי לבדוק את ההתנתקות
        loggedInUser.login();

        when(userRepositoryMock.getUser(memberId)).thenReturn(Optional.of(loggedInUser));

        // Act
        AuthResponse response = userService.logout(memberId);

        // Assert
        assertTrue(response.isSuccess);
        assertEquals("logout successfully", response.message);
        assertEquals("Status must be updated to NOT_LOGGED_IN", UserStatus.NOT_LOGGED_IN, loggedInUser.getStatus());
        verify(userRepositoryMock, times(1)).add(loggedInUser);
    }

    @Test
    public void testLogout_UserDoesNotExist() {
        // Arrange
        UUID fakeId = UUID.randomUUID();
        when(userRepositoryMock.getUser(fakeId)).thenReturn(Optional.empty());

        // Act
        AuthResponse response = userService.logout(fakeId);

        // Assert
        assertFalse(response.isSuccess);
        assertEquals("Request denied: user does not exist.", response.message);
    }
}