package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.LoginRequest;
import org.example.ApplicationLayer.dto.RegisterRequest;
import org.example.DomainLayer.IAuthenticationGateway;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;

import java.util.UUID;
import java.util.logging.Logger;

public class UserService{
    private static final Logger logger = Logger.getLogger(EventService.class.getName());
    private final IUserRepository userRepository;
    private final IAuthenticationGateway authGateway;
    private  final IPurchaseRepository purchaseRepository;

    public UserService(IUserRepository userRepository, IAuthenticationGateway authGateway, IPurchaseRepository purchaseRepository) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.purchaseRepository = purchaseRepository;
    }

    public AuthResponse logout(UUID memberId) {
        try {
            User user = userRepository.getUser(memberId).orElse(null);
            if (user == null) {
                return new AuthResponse(false, "Request denied: user does not exist.", null);
            }

            if (purchaseRepository.findByUserID(memberId) !=null){

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
                return new AuthResponse(false, "incorrect email or password", null);
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
}