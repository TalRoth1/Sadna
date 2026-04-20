package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.LoginRequest;
import org.example.ApplicationLayer.dto.RegisterRequest;

public interface IUserService {
    // מממש את Use Case 2
    AuthResponse register(RegisterRequest request);

    // מממש את Use Case 3
    AuthResponse login(LoginRequest request);

    // מממש את Use Case 1
    AuthResponse logout(String memberId);
}
