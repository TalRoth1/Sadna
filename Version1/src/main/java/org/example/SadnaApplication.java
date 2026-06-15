package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "org.example")
@EnableScheduling
public class SadnaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SadnaApplication.class, args);
    }
}