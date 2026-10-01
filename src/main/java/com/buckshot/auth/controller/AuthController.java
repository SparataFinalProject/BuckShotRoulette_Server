package com.buckshot.auth.controller;

import com.buckshot.auth.dto.GuestLoginRequest;
import com.buckshot.auth.dto.LoginResponse;
import com.buckshot.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/api/auth/guest")
    public LoginResponse guestLogin(@Valid @RequestBody GuestLoginRequest request) {
        return authService.guestLogin(request.guestKey());
    }
}
