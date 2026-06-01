package com.morphcode.ai.mapper;

import org.mapstruct.Mapper;

import com.morphcode.ai.dto.auth.SignupRequest;
import com.morphcode.ai.dto.auth.UserProfileResponse;
import com.morphcode.ai.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toEntity(SignupRequest signupRequest);

    UserProfileResponse toUserProfileResponse(User user);
}
