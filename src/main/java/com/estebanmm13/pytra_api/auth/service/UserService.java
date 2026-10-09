package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;

public interface UserService {

    CurrentUserResponseDto getCurrentUser(Long userId);
}
