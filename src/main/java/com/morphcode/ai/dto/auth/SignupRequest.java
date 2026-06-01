package com.morphcode.ai.dto.auth;

public record SignupRequest(
        String email,
        String name,
        String password) {

}
