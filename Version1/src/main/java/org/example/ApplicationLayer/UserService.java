package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.LoginRequest;
import org.example.ApplicationLayer.dto.RegisterRequest;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.UserAggregate.User;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class UserService{
    private static final Logger logger = Logger.getLogger(EventService.class.getName());
    private final IUserRepository userRepository;
    private final IAuthenticationGateway authGateway;
    private final INotifier notifier;

    public UserService(IUserRepository userRepository, IAuthenticationGateway authGateway, INotifier notifier) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.notifier = notifier;
        notifyAll();
    }

    public AuthResponse logout(UUID memberId) {
        try {
            User user = userRepository.getUser(memberId).orElse(null);
            if (user == null) {
                return new AuthResponse(false, "Request denied: user does not exist.", null);
            }

            user.logout();
            userRepository.add(user);

            return new AuthResponse(true, "logout successfully", user.getId());
        } catch (IllegalStateException e) {
            return new AuthResponse(false, e.getMessage(), memberId);
        } catch (Exception e) {
            return new AuthResponse(false, "Logout failed: system exception.", memberId);
        }
    }

    public AuthResponse register(RegisterRequest request) {
        try {
            if (request.email == null || request.email.isEmpty() || request.plainPassword == null) {
                return new AuthResponse(false, "Missing details.", null);
            }

            if (userRepository.existsByEmail(request.email)) {
                return new AuthResponse(false, "User Email is already exist.", null);
            }
            if(!(authGateway.verifyUserDetails(request.email,request.plainPassword,request.age,request.username))){
                return new AuthResponse(false, "One or more of the details is incorrect.",null);
            }

            String hashedPassword = authGateway.hashPassword(request.plainPassword);
            UUID newUserId = UUID.randomUUID();
            User newUser = new User(newUserId,request.username, request.email, hashedPassword, request.age);

            userRepository.add(newUser);
            return new AuthResponse(true, "Register Successfully", newUser.getId());
        } catch (Exception e) {
            return new AuthResponse(false, "Register failed: system exception", null);
        }
    }

    public AuthResponse login(LoginRequest request) {
        try {
            if (request.email == null || request.plainPassword == null) {
                return new AuthResponse(false, "email or pass is empty", null);
            }
            User user = userRepository.findByEmail(request.email).orElse(null);
            if (user == null) {
                return new AuthResponse(false, "incorrect email or password.", null);
            }
            boolean isPasswordCorrect = authGateway.verifyPassword(request.plainPassword, user.getPasswordHash());
            if (!isPasswordCorrect) {
                System.out.println("LOG: Failed login attempt for user: " + request.email);
                return new AuthResponse(false, "incorrect email or password", null);
            }
            user.login();
            userRepository.add(user);
            return new AuthResponse(true, "Login successfully", user.getId());

        } catch (Exception e) {
            return new AuthResponse(false, "Login failed: system exception", null);
        }
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