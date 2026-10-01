package com.buckshot.user.controller;

import com.buckshot.auth.web.AuthInterceptor;
import com.buckshot.user.dto.NicknameRequest;
import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public PlayerProfileResponse getProfile(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return userService.getProfile(userId);
    }

    @PutMapping("/nickname")
    public PlayerProfileResponse changeNickname(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Valid @RequestBody NicknameRequest request) {
        return userService.changeNickname(userId, request.nickname());
    }
}
