package com.morphcode.ai.service;

import com.morphcode.ai.dto.auth.AuthResponse;
import com.morphcode.ai.dto.auth.LoginRequest;
import com.morphcode.ai.dto.auth.SignupRequest;

public interface AuthService {
    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);
}
