package com.tcc.servicedesk.user.dto;

import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String avatarUrl,
        boolean active,
        Set<String> roles
) {}