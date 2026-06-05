package org.example.API;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

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

    private final JwtService jwtService;
    private final BackendConfigProperties backendConfigProperties;

    public JwtAuthFilter(JwtService jwtService, BackendConfigProperties backendConfigProperties) {
        this.jwtService = jwtService;
        this.backendConfigProperties = backendConfigProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        List<String> publicPaths = backendConfigProperties.getJwtAuth().getPublicPaths();
        return publicPaths.stream().anyMatch(publicPath -> matchesPublicPath(publicPath, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String bearerPrefix = backendConfigProperties.getJwtAuth().getBearerPrefix();
        if (authHeader == null || !authHeader.startsWith(bearerPrefix)) {
            // No token — pass through with no identity. Downstream controllers
            // that require identity should check for the "userId" attribute.
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(bearerPrefix.length()).trim();

        try {
            logger.info("Received token: " + previewToken(token) + " (length=" + token.length() + ")"); // for debugging
            Claims claims = jwtService.parseAndValidate(token);
            UUID userId = UUID.fromString(claims.getSubject());
            request.setAttribute("userId", userId);
            request.setAttribute("username", claims.get("username"));
            request.setAttribute("role", claims.get("role"));
            request.setAttribute("tokenJti", claims.getId());
        } catch (Exception e) {
            logger.warn("Rejected JWT on "
                + request.getMethod()
                + " "
                + request.getRequestURI()
                + ": "
                + e.getClass().getSimpleName()
                + " - "
                + e.getMessage()
                + " (tokenPreview="
                + previewToken(token)
                + ", tokenLength="
                + token.length()
                + ")");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Invalid or expired token\",\"data\":null}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String previewToken(String token) {
        if (token == null || token.isBlank()) {
            return "<empty>";
        }
        int previewLength = Math.min(16, token.length());
        String preview = token.substring(0, previewLength);
        return token.length() > previewLength ? preview + "..." : preview;
    }

    private boolean matchesPublicPath(String publicPath, String requestPath) {
        if (publicPath.contains("{eventId}")) {
            String pattern = publicPath.replace("{eventId}", "[0-9a-fA-F-]{36}");
            return Pattern.matches(pattern, requestPath);
        }
        return publicPath.equals(requestPath);
    }
}
