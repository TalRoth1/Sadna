package org.example.InfrastructureLayer.Persistence;

import org.example.ApplicationLayer.IAuthenticationGateway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthGatewayDebugConfig {

    @Bean
    public ApplicationRunner printAuthGateway(IAuthenticationGateway authGateway) {
        return args -> {
            System.out.println("ACTIVE AUTH GATEWAY = " + authGateway.getClass().getName());
        };
    }
}