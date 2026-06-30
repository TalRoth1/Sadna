package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.dto.TokenClaims;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class JjwtTokenServiceTest {

    private static final String SECRET =
            "test-secret-please-change-this-is-32+bytes-aaaaaaaaaa";
    private static final String ISSUER = "sadna-events-test";

    private InMemoryTokenBlacklist blacklist;
    private JjwtTokenService tokens;
    private User member;

    @Before
    public void setUp() {
        blacklist = new InMemoryTokenBlacklist();
        tokens = new JjwtTokenService(SECRET, ISSUER, 3600, 600, blacklist);
        member = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash", 30);
    }

    @Test
    public void issueAccessToken_roundtripsAllClaims() {
        String jwt = tokens.issueAccessToken(member, "MEMBER");
        TokenClaims claims = tokens.parseAndVerify(jwt).orElseThrow();

        assertEquals(member.getId(), claims.getUserId());
        assertEquals("alice@example.com", claims.getEmail());
        assertEquals("alice", claims.getUsername());
        assertEquals("MEMBER", claims.getRole());
        assertEquals(TokenClaims.TokenType.ACCESS, claims.getTokenType());
        assertNotNull(claims.getJti());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiresAt());
        assertTrue(claims.getExpiresAt().isAfter(claims.getIssuedAt()));
    }

    @Test
    public void issueAccessToken_supportsAdminRoleClaim() {
        String jwt = tokens.issueAccessToken(member, "ADMIN");
        assertEquals("ADMIN", tokens.parseAndVerify(jwt).orElseThrow().getRole());
    }

    @Test
    public void issueAccessToken_defaultsRoleWhenBlank() {
        String jwt = tokens.issueAccessToken(member, "");
        assertEquals("MEMBER", tokens.parseAndVerify(jwt).orElseThrow().getRole());
    }

    @Test
    public void issueGuestToken_carriesGuestRoleAndType() {
        TokenClaims claims = tokens.parseAndVerify(tokens.issueGuestToken()).orElseThrow();
        assertEquals("GUEST", claims.getRole());
        assertEquals(TokenClaims.TokenType.GUEST, claims.getTokenType());
        assertNull("Guest tokens have no real userId", claims.getUserId());
    }

    @Test
    public void parseAndVerify_rejectsNullOrBlank() {
        assertTrue(tokens.parseAndVerify(null).isEmpty());
        assertTrue(tokens.parseAndVerify("").isEmpty());
        assertTrue(tokens.parseAndVerify("   ").isEmpty());
    }

    @Test
    public void parseAndVerify_rejectsTamperedToken() {
        String jwt = tokens.issueAccessToken(member, "MEMBER");
        // flip one character in the signature segment
        String tampered = jwt.substring(0, jwt.length() - 2) + "AA";
        assertTrue(tokens.parseAndVerify(tampered).isEmpty());
    }

    @Test
    public void parseAndVerify_rejectsTokenSignedByDifferentSecret() {
        JjwtTokenService other = new JjwtTokenService(
                "totally-different-secret-also-32+bytes-bbbbbbbbbb",
                ISSUER, 3600, 600, blacklist);
        String alien = other.issueAccessToken(member, "MEMBER");
        assertTrue(tokens.parseAndVerify(alien).isEmpty());
    }

    @Test
    public void parseAndVerify_rejectsTokenWithWrongIssuer() {
        JjwtTokenService wrongIss = new JjwtTokenService(
                SECRET, "some-other-issuer", 3600, 600, blacklist);
        String alien = wrongIss.issueAccessToken(member, "MEMBER");
        assertTrue(tokens.parseAndVerify(alien).isEmpty());
    }

    @Test
    public void parseAndVerify_rejectsExpiredToken() throws InterruptedException {
        JjwtTokenService shortLived =
                new JjwtTokenService(SECRET, ISSUER, 1, 1, blacklist);
        String jwt = shortLived.issueAccessToken(member, "MEMBER");
        // jjwt allows a small clock skew, so wait a bit beyond TTL.
        Thread.sleep(1500);
        assertTrue(shortLived.parseAndVerify(jwt).isEmpty());
    }

    @Test
    public void parseAndVerify_rejectsBlacklistedJti() {
        String jwt = tokens.issueAccessToken(member, "MEMBER");
        TokenClaims claims = tokens.parseAndVerify(jwt).orElseThrow();
        blacklist.revoke(claims.getJti(), claims.getExpiresAt());
        assertTrue("Revoked jti must be rejected on next verify",
                tokens.parseAndVerify(jwt).isEmpty());
    }

    @Test
    public void constructor_rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JjwtTokenService("too-short", ISSUER, 3600, 600, null));
    }

    @Test
    public void parseAndVerify_worksWithoutBlacklist() {
        JjwtTokenService noBlacklist =
                new JjwtTokenService(SECRET, ISSUER, 3600, 600, null);
        String jwt = noBlacklist.issueAccessToken(member, "MEMBER");
        Optional<TokenClaims> opt = noBlacklist.parseAndVerify(jwt);
        assertTrue(opt.isPresent());
    }

    @Test
    public void getAccessTokenTtlSeconds_matchesConfig() {
        assertEquals(3600L, tokens.getAccessTokenTtlSeconds());
    }
}
