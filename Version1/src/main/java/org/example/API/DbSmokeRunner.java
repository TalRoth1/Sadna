package org.example.API;

import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserRole;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Profile("db-smoke")
public class DbSmokeRunner implements CommandLineRunner {

    private final IUserRepository userRepository;
    private final IAdminRepository adminRepository;

    public DbSmokeRunner(IUserRepository userRepository,
                         IAdminRepository adminRepository) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
    }

    @Override
    public void run(String... args) {
        String email = "db-smoke-admin@demo.test";

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> new User(
                        UUID.randomUUID(),
                        email,
                        email,
                        "smoke-password-hash",
                        UserRole.MEMBER,
                        UserStatus.NOT_LOGGED_IN,
                        30
                ));

        userRepository.add(user);

        User savedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User was not saved"));

        System.out.println("[DB-SMOKE] user saved: " + savedUser.getId() + " / " + savedUser.getEmail());

        Admin admin = new Admin() {
            @Override
            public String getUsername() {
                return email;
            }

            @Override
            public UUID getId() {
                return savedUser.getId();
            }
        };

        userRepository.addAdmin(admin);

        if (!userRepository.isSystemAdmin(email)) {
            throw new IllegalStateException("Admin was not saved");
        }

        System.out.println("[DB-SMOKE] admin saved: " + email);

        AdminComplaint complaint = new AdminComplaint(
                savedUser.getId(),
                email,
                "DB smoke complaint " + LocalDateTime.now(),
                "Checking that JpaAdminRepository saves complaints to Google Cloud SQL"
        );

        adminRepository.saveComplaint(complaint);

        adminRepository.saveActionLog(new AdminActionLog(
                savedUser.getId(),
                email,
                "DB_SMOKE_TEST",
                "users/admins/admin_complaints/admin_action_logs/system_analytics_snapshots"
        ));

        adminRepository.saveAnalyticsSnapshot(new SystemAnalyticsSnapshot(
                1,
                0,
                0,
                0,
                0,
                0,
                0.0,
                0.0,
                0.0
        ));

        System.out.println("[DB-SMOKE] admin repository smoke data saved");
    }
}