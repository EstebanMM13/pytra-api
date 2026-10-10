package com.estebanmm13.pytra_api.auth.dto.user;

import com.estebanmm13.pytra_api.auth.validation.ValidUsername;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Partial profile update: only the fields present in the JSON body are changed. */
@Getter
@Setter
public class UpdateProfileRequestDto {

    // Same rules as RegisterRequestDto.username; null means "keep the current username".
    @ValidUsername
    private String username;

    /** Preset avatar key (see AvatarPolicy); an explicit null clears it. */
    private String avatar;

    /** Jackson calls the setter even for an explicit null, which tells "clear" apart from "absent". */
    @Setter(AccessLevel.NONE)
    private boolean avatarPresent;

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
        this.avatarPresent = true;
    }
}
