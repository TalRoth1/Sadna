package org.example.API;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.example.ApplicationLayer.JwtService;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JwtAuthFilter
 *
 * Runs once per HTTP request. If an Authorization: Bearer header is present,
 * it validates the token via JwtService and attaches the user identity
 * (userId, username, role) as request attributes that downstream controllers
 * can read via HttpServletRequest#getAttribute.
 *
 * Public endpoints (guest entry, login, register) are skipped — they must work
 * without a token, since that's where tokens are obtained.
 *
 * On an invalid/expired token the filter responds 401 immediately and the
 * downstream controller is never invoked. On a missing token the filter just
 * lets the request through with no identity attached; whether the controller
 * requires identity is the controller's call.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Endpoints that must be reachable without a token. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/users/guest",
            "/api/users/login",
            "/api/users/register"
    );

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // OPTIONS preflight requests have no Authorization header — let them through.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return PUBLIC_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No token — pass through with no identity. Downstream controllers
            // that require identity should check for the "userId" attribute.
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = jwtService.parseAndValidate(token);
            UUID userId = UUID.fromString(claims.getSubject());
            request.setAttribute("userId", userId);
            request.setAttribute("username", claims.get("username"));
            request.setAttribute("role", claims.get("role"));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Invalid or expired token\",\"data\":null}");
            return;
        }

        chain.doFilter(request, response);
    }
}
