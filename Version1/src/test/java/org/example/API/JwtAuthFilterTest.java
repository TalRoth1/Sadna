package org.example.API;

import java.util.UUID;

import org.example.ApplicationLayer.ITokenBlacklist;
import org.example.ApplicationLayer.JwtService;
import org.example.InfrastructureLayer.InMemoryTokenBlacklist;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

public class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-please-change-this-is-32-bytes-long";

    private JwtService jwtService;
    private ITokenBlacklist tokenBlacklist;
    private JwtAuthFilter filter;

    @Before
    public void setUp() {
        tokenBlacklist = new InMemoryTokenBlacklist();
        jwtService = new JwtService(SECRET, 86_400_000L, tokenBlacklist);
        filter = new JwtAuthFilter(jwtService);
    }

    @Test
    public void logoutRequestWithValidTokenReachesController() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "alice", "MEMBER");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/logout");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(userId, request.getAttribute("userId"));
        verify(chain).doFilter(request, response);
    }

    @Test
    public void logoutRequestWithInvalidTokenIsRejectedBeforeController() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtService otherJwtService = new JwtService(
                "another-test-secret-please-change-this-is-32-bytes",
            86_400_000L,
            tokenBlacklist);
        String invalidToken = otherJwtService.generateToken(userId, "alice", "MEMBER");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/logout");
        request.addHeader("Authorization", "Bearer " + invalidToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertEquals("{\"success\":false,\"message\":\"Invalid or expired token\",\"data\":null}",
                response.getContentAsString());
        assertNull(request.getAttribute("userId"));
        verifyNoInteractions(chain);
    }

    @Test
    public void logoutRequestWithRevokedTokenIsRejectedBeforeController() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "alice", "MEMBER");

        String jti = jwtService.parseAndValidate(token).getId();
        tokenBlacklist.revoke(jti, jwtService.parseAndValidate(token).getExpiration().toInstant());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/logout");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(request.getAttribute("userId"));
        verifyNoInteractions(chain);
    }
}