package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;
import com.estebanmm13.pytra_api.auth.dto.user.UpdateProfileRequestDto;

public interface UserService {

    CurrentUserResponseDto getCurrentUser(Long userId);

    CurrentUserResponseDto updateProfile(Long userId, UpdateProfileRequestDto requestDto);
}
