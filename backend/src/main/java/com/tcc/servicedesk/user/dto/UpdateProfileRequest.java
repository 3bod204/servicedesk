package com.tcc.servicedesk.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        String fullName,
        String avatarUrl
) {}