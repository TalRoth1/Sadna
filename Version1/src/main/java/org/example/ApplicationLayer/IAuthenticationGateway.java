package org.example.ApplicationLayer;

public interface IAuthenticationGateway {
    String hashPassword(String plainPassword); // מיוז קייס 2
    boolean verifyPassword(String plainPassword, String hashedPassword); // מיוז קייס 3
    boolean verifyEmail(String email);
    boolean verifyPassword(String pass);
    boolean verifyUserDetails(String email, String password, float age, String username);

}
