package com.buckshot.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NicknameRequest(
        @NotBlank
        @Size(min = 1, max = 8)
        @Pattern(regexp = "^[^\\s\\p{C}]+$")   // 한글(자모 포함)·영문·숫자·밑줄·특수문자 모두 한 글자, 공백과 제어 문자만 안 됨
        String nickname) {
}
