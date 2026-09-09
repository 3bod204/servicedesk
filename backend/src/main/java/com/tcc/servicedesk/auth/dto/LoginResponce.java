package com.tcc.servicedesk.auth.dto;

public record LoginResponce(
    String accessToken,
    String refreshToken,
    Long userId,
    String fullname,
    String email
) {}
