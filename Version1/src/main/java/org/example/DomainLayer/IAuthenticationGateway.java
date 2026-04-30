package org.example.DomainLayer;

public interface IAuthenticationGateway {
    String hashPassword(String plainPassword); // מיוז קייס 2
    boolean verifyPassword(String plainPassword, String hashedPassword); // מיוז קייס 3

}
