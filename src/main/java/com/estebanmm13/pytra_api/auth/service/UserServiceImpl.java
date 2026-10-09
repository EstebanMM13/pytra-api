package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;
import com.estebanmm13.pytra_api.auth.mapper.UserMapper;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponseDto getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toCurrentUserResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
