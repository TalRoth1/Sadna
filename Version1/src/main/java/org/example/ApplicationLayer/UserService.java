package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.UserAggregate.User;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;


import org.springframework.stereotype.Service;

@Service
public class UserService {
    private static final Logger logger = Logger.getLogger(UserService.class.getName());

    private final IUserRepository userRepository;
    private final IAuthenticationGateway authGateway;
    private final INotifier notifier;

    public UserService(IUserRepository userRepository, IAuthenticationGateway authGateway, INotifier notifier) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.notifier = notifier;
        notifyAll();
    }

    /**
     * Creates a fresh anonymous visitor (guest) and persists it.
     *
     * Called by UserController on the first visit so the front-end can attach
     * a session token to a stable UUID even before the visitor logs in or
     * registers. Issuing the JWT itself is the controller's job — this method
     * only deals with the User aggregate.
     */
    public UserResponse enterAsGuest() {
        User guest = new User(UUID.randomUUID());
        userRepository.add(guest);
        return toResponse(guest);
    }

    public UserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        if (request.email == null || request.email.isBlank()
                || request.plainPassword == null || request.plainPassword.isBlank()
                || request.username == null || request.username.isBlank()) {
            throw new IllegalArgumentException("Missing details.");
        }

        if (userRepository.existsByEmail(request.email)) {
            throw new IllegalArgumentException("User email already exists.");
        }

        if (!authGateway.verifyUserDetails(
                request.email,
                request.plainPassword,
                request.age,
                request.username)) {
            throw new IllegalArgumentException("One or more of the details is incorrect.");
        }

        String hashedPassword = authGateway.hashPassword(request.plainPassword);
        User newUser = new User(
                UUID.randomUUID(),
                request.username,
                request.email,
                hashedPassword,
                request.age);

        userRepository.add(newUser);
        return toResponse(newUser);
    }

    public UserResponse login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        if (request.email == null || request.email.isBlank()
                || request.plainPassword == null || request.plainPassword.isBlank()) {
            throw new IllegalArgumentException("Email or password is empty.");
        }

        User user = userRepository.findByEmail(request.email)
                .orElseThrow(() -> new IllegalArgumentException("Incorrect email or password."));

        boolean isPasswordCorrect =
                authGateway.verifyPassword(request.plainPassword, user.getPasswordHash());

        if (!isPasswordCorrect) {
            logger.warning("Failed login attempt for user: " + request.email);
            throw new IllegalArgumentException("Incorrect email or password.");
        }

        user.login();
        userRepository.add(user);

        return toResponse(user);
    }

    public UserResponse logout(UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("Member ID is required");
        }

        User user = userRepository.getUser(memberId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist."));

        user.logout();
        userRepository.add(user);

        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus().toString(),
                user.getRole().toString(),
                user.getAge());
    }

    public void adminMessage(String username, String message)
    {
        try {
            if(username == null)
                throw new IllegalArgumentException("Username Is null");
            else if(!userRepository.isSystemAdmin(username))
                throw new IllegalArgumentException("User is not admin");
            else
            {
                for(Map.Entry<UUID, User> user: userRepository.getAllUsers().entrySet())
                {
                     notifier.notifyUser(user.getKey(), message);
                }
            }
            logger.info("Admin: " + username + " sent message:" + message);
        }
        catch(Exception e)
        {
            logger.severe(e.toString());
            throw e;
        }
    }
}