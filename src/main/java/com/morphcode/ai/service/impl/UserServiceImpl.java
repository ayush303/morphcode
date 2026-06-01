package com.morphcode.ai.service.impl;

import org.springframework.stereotype.Service;

import com.morphcode.ai.dto.auth.UserProfileResponse;
import com.morphcode.ai.service.UserService;

@Service
public class UserServiceImpl implements UserService {
    @Override
    public UserProfileResponse getProfile(Long userId) {
        return null;
    }
}
