package org.example.API;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global MVC config — for now only opens CORS for the local Vite dev
 * server so the React frontend at http://localhost:5173 can call the
 * REST endpoints under /api/** while developing.
 *
 * Tighten {@code allowedOrigins} (and re-enable credentials only over
 * HTTPS) before deploying anywhere outside of localhost.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
