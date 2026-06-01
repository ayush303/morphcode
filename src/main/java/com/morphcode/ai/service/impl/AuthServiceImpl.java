package com.morphcode.ai.service.impl;

import org.springframework.stereotype.Service;

import com.morphcode.ai.dto.auth.AuthResponse;
import com.morphcode.ai.dto.auth.LoginRequest;
import com.morphcode.ai.dto.auth.SignupRequest;
import com.morphcode.ai.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {
    @Override
    public AuthResponse signup(SignupRequest request) {
        return null;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        return null;
    }
}
