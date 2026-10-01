package com.buckshot.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NicknameRequest(
        @NotBlank
        @Size(min = 2, max = 12)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$")
        String nickname) {
}
