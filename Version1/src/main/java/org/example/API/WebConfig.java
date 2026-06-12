package org.example.API;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global MVC config — opens CORS for local Vite dev servers so the React frontend
 * can call the REST endpoints under /api/** while developing.
 *
 * Tighten allowed origins before deploying anywhere outside of localhost.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BackendConfigProperties backendConfigProperties;

    @Autowired
    public WebConfig(BackendConfigProperties backendConfigProperties) {
        this.backendConfigProperties = backendConfigProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(backendConfigProperties.getCors().getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods(backendConfigProperties.getCors().getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(backendConfigProperties.getCors().getAllowedHeaders().toArray(String[]::new))
                .allowCredentials(backendConfigProperties.getCors().isAllowCredentials())
                .maxAge(backendConfigProperties.getCors().getMaxAgeSeconds());
    }
}