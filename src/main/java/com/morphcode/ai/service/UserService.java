package com.morphcode.ai.service;

import com.morphcode.ai.dto.auth.UserProfileResponse;

public interface UserService {

    UserProfileResponse getProfile(Long id);
}
