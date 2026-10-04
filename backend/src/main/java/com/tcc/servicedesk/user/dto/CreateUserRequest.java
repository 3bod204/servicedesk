package com.tcc.servicedesk.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record CreateUserRequest(
        @Email @NotBlank String email,
        @NotBlank String fullName,
        @NotBlank
                @jakarta.validation.constraints.Size(
                        min = 8,
                        message = "Password must be at least 8 characters")
                String password,
        @NotEmpty(message = "At least one role is required") Set<String> roleNames) {}
