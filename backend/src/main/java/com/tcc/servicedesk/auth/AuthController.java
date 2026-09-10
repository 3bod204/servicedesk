package com.tcc.servicedesk.auth;

import com.tcc.servicedesk.auth.dto.LoginRequest;
import com.tcc.servicedesk.auth.dto.LoginResponce;
import com.tcc.servicedesk.auth.dto.RefreshRequest;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponce login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponce refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
    
    @PostMapping("/logout")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }
    
}