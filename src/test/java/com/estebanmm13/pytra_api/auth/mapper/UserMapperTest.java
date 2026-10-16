package com.estebanmm13.pytra_api.auth.mapper;

import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.validation.ExclusiveAvatars;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper(new ExclusiveAvatars("estebanexplorador:7"));

    private static User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("someone");
        return user;
    }

    @Test
    void currentUserListsTheExclusiveAvatarsTheOwnerMayPick() {
        CurrentUserResponseDto dto = mapper.toCurrentUserResponseDto(userWithId(7L));
        assertEquals(List.of("estebanexplorador"), dto.getExclusiveAvatars());
    }

    @Test
    void otherUsersGetAnEmptyList() {
        CurrentUserResponseDto dto = mapper.toCurrentUserResponseDto(userWithId(8L));
        assertEquals(List.of(), dto.getExclusiveAvatars());
    }
}
