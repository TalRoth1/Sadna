package org.example.API;

import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
@Order(100)
public class SystemAdminStartupValidator implements CommandLineRunner {

    private static final Logger logger = Logger.getLogger(SystemAdminStartupValidator.class.getName());

    private final IUserRepository userRepository;

    public SystemAdminStartupValidator(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (!systemAdminExists()) {
            logger.severe("[SystemAdminStartupValidator] No system admin exists. Application startup is blocked.");
            throw new IllegalStateException("Application cannot start without at least one system admin");
        }

        logger.info("[SystemAdminStartupValidator] System admin exists. Startup validation passed.");
    }

    private boolean systemAdminExists() {
        return userRepository.getAllUsers().values().stream()
                .anyMatch(this::isSystemAdmin);
    }

    private boolean isSystemAdmin(User user) {
        if (user == null) {
            return false;
        }

        return userRepository.existsAdmin(user.getId())
                || (user.getEmail() != null && userRepository.isSystemAdmin(user.getEmail()))
                || (user.getUsername() != null && userRepository.isSystemAdmin(user.getUsername()));
    }
}