package com.tcc.servicedesk.auth.dto;

public record LoginResponse(
        String accessToken, String refreshToken, Long userId, String fullname, String email) {}
