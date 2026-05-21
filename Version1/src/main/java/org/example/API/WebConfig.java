package org.example.API;

import org.example.ApplicationLayer.JwtService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebConfig
 *
 * Registers the JwtAuthFilter against the URL patterns that should be
 * protected. Public paths (guest entry, login, register) are excluded
 * inside the filter itself via shouldNotFilter().
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(JwtService jwtService) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(jwtService));
        // Apply to all /api/** endpoints; the filter itself decides which paths to skip.
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
