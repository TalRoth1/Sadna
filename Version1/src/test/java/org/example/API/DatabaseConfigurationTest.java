package org.example.API;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataSourceAutoConfiguration.class);

    @Test
    void whenDatabaseConfigPointsToWorkingDatabase_thenConnectionCanBeOpened() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:config_test;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.hikari.connection-timeout=1000"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);

                    DataSource dataSource = context.getBean(DataSource.class);

                    try (Connection connection = dataSource.getConnection()) {
                        assertThat(connection.isValid(1)).isTrue();
                    }
                });
    }

    @Test
    void whenDatabaseConfigPointsToNonExistingDatabase_thenConnectionFails() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/not_real_db?connectTimeout=1",
                        "spring.datasource.username=wrong_user",
                        "spring.datasource.password=wrong_password",
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.datasource.hikari.connection-timeout=1000"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);

                    HikariDataSource dataSource = context.getBean(HikariDataSource.class);

                    assertThrows(SQLException.class, dataSource::getConnection);
                });
    }
}