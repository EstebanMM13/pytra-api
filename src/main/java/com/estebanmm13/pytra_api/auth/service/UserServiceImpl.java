package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;
import com.estebanmm13.pytra_api.auth.dto.user.UpdateProfileRequestDto;
import com.estebanmm13.pytra_api.auth.mapper.UserMapper;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.validation.AvatarPolicy;
import com.estebanmm13.pytra_api.auth.validation.UsernamePolicy;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
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

    @Override
    @Transactional
    public CurrentUserResponseDto updateProfile(Long userId, UpdateProfileRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (requestDto.isAvatarPresent()) {
            String avatar = requestDto.getAvatar();
            if (avatar != null && !AvatarPolicy.isValid(avatar)) {
                throw new InvalidRequestException(InvalidRequestException.INVALID_AVATAR);
            }
            user.setAvatar(avatar);
        }
        if (requestDto.getUsername() != null) {
            applyUsername(user, requestDto.getUsername());
        }
        try {
            // Flush now so a concurrent rename to the same name surfaces here as a 409.
            return userMapper.toCurrentUserResponseDto(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            log.warn("Username change lost a race on username uniqueness");
            throw new DuplicateResourceException("Username: " + requestDto.getUsername() + " already exists");
        }
    }

    private void applyUsername(User user, String username) {
        // Usernames are stored lowercase (see AuthServiceImpl.register), so uniqueness is case-insensitive.
        String normalizedUsername = UsernamePolicy.normalize(username);

        if (!normalizedUsername.equals(user.getUsername()) && userRepository.existsByUsername(normalizedUsername)) {
            log.warn("Username change attempt to existing username: {}", username);
            throw new DuplicateResourceException("Username: " + username + " already exists");
        }

        user.setUsername(normalizedUsername);
        user.setUsernameDisplay(username);
    }
}
