package org.example.ApplicationLayer;

import java.util.UUID;

import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.example.DomainLayer.IAuthenticationGateway;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;

/**
 * UserService
 *
 * Follows the project-wide convention:
 *  - Returns raw payload DTOs (UserResponse) on success
 *  - Throws exceptions on failure (Controller maps them to ApiResponse.error)
 *
 * The Controller is responsible for wrapping the result in ApiResponse<T>.
 */
public class UserService {

    private final IUserRepository userRepository;
    private final IAuthenticationGateway authGateway;
    private final IPurchaseRepository purchaseRepository;

    public UserService(IUserRepository userRepository,
                       IAuthenticationGateway authGateway,
                       IPurchaseRepository purchaseRepository) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.purchaseRepository = purchaseRepository;
    }

    public UserResponse register(RegisterRequest request) {
        if (request.email == null || request.email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.plainPassword == null || request.plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (userRepository.existsByEmail(request.email)) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (!authGateway.verifyUserDetails(request.email, request.plainPassword, request.age, request.username)) {
            throw new IllegalArgumentException("One or more of the details is incorrect");
        }

        String hashedPassword = authGateway.hashPassword(request.plainPassword);
        UUID newUserId = UUID.randomUUID();
        User newUser = new User(newUserId, request.username, request.email, hashedPassword, request.age);

        userRepository.add(newUser);

        return toUserResponse(newUser);
    }

    public UserResponse login(LoginRequest request) {
        if (request.email == null || request.plainPassword == null) {
            throw new IllegalArgumentException("Email or password is empty");
        }

        User user = userRepository.findByEmail(request.email)
                .orElseThrow(() -> new IllegalArgumentException("Incorrect email or password"));

        if (!authGateway.verifyPassword(request.plainPassword, user.getPasswordHash())) {
            System.out.println("LOG: Failed login attempt for user: " + request.email);
            throw new IllegalArgumentException("Incorrect email or password");
        }

        user.login();
        userRepository.add(user);

        return toUserResponse(user);
    }

    public UserResponse logout(UUID memberId) {
        User user = userRepository.getUser(memberId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));

        // TODO: handle pending purchases for this user
        // if (purchaseRepository.findByUserID(memberId) != null) { ... }

        user.logout();
        userRepository.add(user);

        return toUserResponse(user);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus().name(),
                user.getRole().name(),
                user.getAge()
        );
    }
}