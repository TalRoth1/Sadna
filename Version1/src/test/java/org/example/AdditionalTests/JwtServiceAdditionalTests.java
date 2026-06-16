package org.example.AdditionalTests;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.example.API.BackendConfigProperties;
import org.example.ApplicationLayer.ITokenBlacklist;
import org.example.ApplicationLayer.JwtService;
import org.junit.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JwtServiceAdditionalTests {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    public void mintedTokenRecord_validatesAllRequiredFields() {
        Instant expiresAt = Instant.now().plusSeconds(60);

        JwtService.MintedToken token = new JwtService.MintedToken("token", "jti", expiresAt);

        assertEquals("token", token.token());
        assertEquals("jti", token.jti());
        assertEquals(expiresAt, token.expiresAt());

        assertThrows(IllegalArgumentException.class, () -> new JwtService.MintedToken(null, "jti", expiresAt));
        assertThrows(IllegalArgumentException.class, () -> new JwtService.MintedToken(" ", "jti", expiresAt));
        assertThrows(IllegalArgumentException.class, () -> new JwtService.MintedToken("token", null, expiresAt));
        assertThrows(IllegalArgumentException.class, () -> new JwtService.MintedToken("token", " ", expiresAt));
        assertThrows(IllegalArgumentException.class, () -> new JwtService.MintedToken("token", "jti", null));
    }

    @Test
    public void mintParseExtractAndValidate_coverHappyPathAndNullUsername() {
        ITokenBlacklist blacklist = mock(ITokenBlacklist.class);
        JwtService service = service(60_000, blacklist);
        UUID userId = UUID.randomUUID();

        JwtService.MintedToken minted = service.mintSession(userId, null, "MEMBER");

        assertNotNull(minted.token());
        assertNotNull(minted.jti());
        assertTrue(minted.expiresAt().isAfter(Instant.now()));

        Claims claims = service.parseAndValidate(minted.token());
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("MEMBER", claims.get("role"));
        assertNull(claims.get("username"));
        assertEquals(minted.jti(), claims.getId());

        assertEquals(userId, service.extractUserId(minted.token()));
        assertNull(service.extractUsername(minted.token()));
        assertEquals("MEMBER", service.extractRole(minted.token()));
        assertTrue(service.isValid(minted.token()));

        verify(blacklist, atLeastOnce()).isRevoked(minted.jti());
    }

    @Test
    public void mintParseExtractAndValidate_coverUsernamePathAndNoBlacklistBean() {
        JwtService service = service(60_000, null);
        UUID userId = UUID.randomUUID();

        JwtService.MintedToken minted = service.mintSession(userId, "alice", "ADMIN");

        assertEquals("alice", service.extractUsername(minted.token()));
        assertEquals("ADMIN", service.extractRole(minted.token()));
        assertTrue(service.isValid(minted.token()));
        assertNotNull(service.parseAllowingExpired(minted.token()));
    }

    @Test
    public void validationFailuresAndInvalidTokens_coverFalseAndNullReturnBranches() {
        JwtService service = service(60_000, mock(ITokenBlacklist.class));

        assertThrows(IllegalArgumentException.class, () -> service.mintSession(null, "alice", "MEMBER"));
        assertThrows(IllegalArgumentException.class, () -> service.mintSession(UUID.randomUUID(), "alice", null));
        assertThrows(IllegalArgumentException.class, () -> service.mintSession(UUID.randomUUID(), "alice", "   "));

        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate(null));
        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate("   "));

        assertFalse(service.isValid(null));
        assertFalse(service.isValid("not-a-token"));
        assertNull(service.parseAllowingExpired(null));
        assertNull(service.parseAllowingExpired("   "));
        assertNull(service.parseAllowingExpired("not-a-token"));
    }

    @Test
    public void revokedTokens_areRejectedByValidationAndValidityChecks() {
        ITokenBlacklist blacklist = mock(ITokenBlacklist.class);
        JwtService service = service(60_000, blacklist);
        JwtService.MintedToken minted = service.mintSession(UUID.randomUUID(), "alice", "MEMBER");

        when(blacklist.isRevoked(minted.jti())).thenReturn(true);

        assertThrows(JwtException.class, () -> service.parseAndValidate(minted.token()));
        assertFalse(service.isValid(minted.token()));
        assertNull(service.parseAllowingExpired(minted.token()));
    }

    @Test
    public void expiredTokens_areInvalidButCanBeParsedWhenNotRevoked() throws Exception {
        ITokenBlacklist blacklist = mock(ITokenBlacklist.class);
        JwtService service = service(1, blacklist);
        JwtService.MintedToken minted = service.mintSession(UUID.randomUUID(), "alice", "MEMBER");
        Thread.sleep(1_100);

        assertThrows(ExpiredJwtException.class, () -> service.parseAndValidate(minted.token()));
        assertFalse(service.isValid(minted.token()));

        Claims claims = service.parseAllowingExpired(minted.token());

        assertNotNull(claims);
        assertEquals(minted.jti(), claims.getId());

        when(blacklist.isRevoked(minted.jti())).thenReturn(true);
        assertNull(service.parseAllowingExpired(minted.token()));
    }

    private JwtService service(long expirationMs, ITokenBlacklist blacklist) {
        BackendConfigProperties properties = mock(BackendConfigProperties.class, RETURNS_DEEP_STUBS);
        when(properties.getJwt().getSecret()).thenReturn(SECRET);
        when(properties.getJwt().getExpirationMs()).thenReturn(expirationMs);
        return new JwtService(properties, blacklist);
    }
}
