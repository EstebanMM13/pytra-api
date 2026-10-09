package com.estebanmm13.pytra_api.auth.mapper;

import com.estebanmm13.pytra_api.auth.dto.register.RegisterResponseDto;
import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;
import com.estebanmm13.pytra_api.auth.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public RegisterResponseDto toRegisterResponseDto(User user){
        if (user == null) return null;
        return new RegisterResponseDto(
                user.getId(),
                user.getUsernameDisplay(),
                user.getEmail()
        );
    }

    public CurrentUserResponseDto toCurrentUserResponseDto(User user) {
        if (user == null) return null;
        return new CurrentUserResponseDto(
                user.getUsername(),
                user.getUsernameDisplay(),
                user.getEmail(),
                user.getPasswordHash() != null,
                user.getGoogleId() != null
        );
    }
}
