package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.LoginRequest;
import org.example.ApplicationLayer.dto.TokenClaims;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;
import org.example.InfrastructureLayer.InMemoryTokenBlacklist;
import org.example.InfrastructureLayer.JjwtTokenService;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end-ish tests for the JWT bits of UserService, wiring real
 * JjwtTokenService + InMemoryTokenBlacklist (mocking only the repo and
 * authentication gateway).
 */
public class UserServiceJwtTest {

    private static final String SECRET =
            "test-secret-please-change-this-is-32+bytes-aaaaaaaaaa";

    private IUserRepository userRepoMock;
    private IAuthenticationGateway authGatewayMock;
    private InMemoryTokenBlacklist blacklist;
    private JjwtTokenService tokens;
    private UserService userService;

    private User existing;
    private LoginRequest goodLogin;

    @Before
    public void setUp() {
        userRepoMock = mock(IUserRepository.class);
        authGatewayMock = mock(IAuthenticationGateway.class);
        blacklist = new InMemoryTokenBlacklist();
        tokens = new JjwtTokenService(SECRET, "sadna-events-test", 3600, 600, blacklist);
        userService = new UserService(userRepoMock, authGatewayMock, tokens, blacklist);

        existing = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash", 30f);
        goodLogin = new LoginRequest();
        goodLogin.email = "alice@example.com";
        goodLogin.plainPassword = "pw";
        when(userRepoMock.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(authGatewayMock.verifyPassword("pw", "hash")).thenReturn(true);
    }

    @Test
    public void login_success_returnsBearerTokenWithMemberRoleClaim() {
        when(userRepoMock.existsAdmin(existing.getId())).thenReturn(false);

        AuthResponse resp = userService.login(goodLogin);

        assertTrue(resp.isSuccess);
        assertNotNull(resp.token);
        assertEquals("Bearer", resp.tokenType);
        assertEquals(3600L, resp.expiresInSeconds);
        assertEquals(existing.getId(), resp.userId);

        TokenClaims claims = userService.validateToken(resp.token).orElseThrow();
        assertEquals(existing.getId(), claims.getUserId());
        assertEquals("MEMBER", claims.getRole());
        assertEquals(TokenClaims.TokenType.ACCESS, claims.getTokenType());
    }

    @Test
    public void login_admin_issuesAdminRoleClaim() {
        when(userRepoMock.existsAdmin(existing.getId())).thenReturn(true);

        AuthResponse resp = userService.login(goodLogin);

        TokenClaims claims = userService.validateToken(resp.token).orElseThrow();
        assertEquals("ADMIN", claims.getRole());
    }

    @Test
    public void login_failure_returnsNoToken() {
        when(authGatewayMock.verifyPassword("pw", "hash")).thenReturn(false);

        AuthResponse resp = userService.login(goodLogin);

        assertFalse(resp.isSuccess);
        assertNull(resp.token);
        assertNull(resp.tokenType);
        assertEquals(0L, resp.expiresInSeconds);
    }

    @Test
    public void validateToken_acceptsValidJwt() {
        AuthResponse resp = userService.login(goodLogin);
        assertTrue(userService.validateToken(resp.token).isPresent());
    }

    @Test
    public void validateToken_rejectsTamperedJwt() {
        AuthResponse resp = userService.login(goodLogin);
        String tampered = resp.token.substring(0, resp.token.length() - 2) + "XX";
        assertTrue(userService.validateToken(tampered).isEmpty());
    }

    @Test
    public void logoutWithToken_revokesJti_subsequentValidateFails() {
        AuthResponse login = userService.login(goodLogin);
        assertTrue(userService.validateToken(login.token).isPresent());

        AuthResponse logout = userService.logoutWithToken(login.token);

        assertTrue(logout.isSuccess);
        assertEquals(existing.getId(), logout.userId);
        assertTrue("Token must no longer validate after revocation",
                userService.validateToken(login.token).isEmpty());
    }

    @Test
    public void logoutWithToken_invalidToken_returnsFailure() {
        AuthResponse logout = userService.logoutWithToken("not.a.real.jwt");
        assertFalse(logout.isSuccess);
    }

    @Test
    public void logoutWithToken_withoutTokenServiceWired_returnsFailure() {
        UserService legacy = new UserService(userRepoMock, authGatewayMock); // 2-arg
        AuthResponse logout = legacy.logoutWithToken("anything");
        assertFalse(logout.isSuccess);
    }

    @Test
    public void legacyConstructor_loginWorksButIssuesNoToken() {
        UserService legacy = new UserService(userRepoMock, authGatewayMock);
        AuthResponse resp = legacy.login(goodLogin);
        assertTrue(resp.isSuccess);
        assertNull(resp.token);
        assertEquals(0L, resp.expiresInSeconds);
    }
}
