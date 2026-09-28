package com.tcc.servicedesk.user;

import com.tcc.servicedesk.user.dto.ChangePasswordRequest;
import com.tcc.servicedesk.user.dto.CreateUserRequest;
import com.tcc.servicedesk.user.dto.UpdateProfileRequest;
import com.tcc.servicedesk.user.dto.UserResponse;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return toResponse(findUserOrThrow(userId));
    }

    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);
        user.setFullName(request.fullName());
        user.setAvatarUrl(request.avatarUrl());
        user.setUpdatedAt(Instant.now());
        return toResponse(user);
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUserOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(Instant.now());
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user =
                User.builder()
                        .email(request.email())
                        .fullName(request.fullName())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .active(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        for (String roleName : request.roleNames()) {
            Role role =
                    roleRepository
                            .findByName(roleName)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Unknown role: " + roleName));
            user.getRoles().add(role);
        }

        userRepository.save(user);
        return toResponse(user);
    }

    public UserResponse deactivateUser(Long userId) {
        User user = findUserOrThrow(userId);
        user.setActive(false);
        user.setUpdatedAt(Instant.now());
        return toResponse(user);
    }

    public UserResponse activateUser(Long userId) {
        User user = findUserOrThrow(userId);
        user.setActive(true);
        user.setUpdatedAt(Instant.now());
        return toResponse(user);
    }

    public List<UserResponse> getUsersByQueue(Long queueId) {
        return userRepository
                .findByQueues_IdAndActiveTrueAndDeletedFalseAndRoles_NameIn(
                        queueId, List.of("ROLE_AGENT", "ROLE_MANAGER"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<UserResponse> getAssignableUsers() {
        return userRepository
                .findByActiveTrueAndDeletedFalseAndRoles_NameInOrderByFullNameAsc(
                        List.of("ROLE_AGENT", "ROLE_MANAGER"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<UserResponse> listAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !u.isDeleted())
                .map(this::toResponse)
                .toList();
    }

    public UserResponse updateRoles(Long userId, Set<String> roleNames) {
        User user = findUserOrThrow(userId);

        Set<Role> newRoles = new HashSet<>();
        for (String roleName : roleNames) {
            Role role =
                    roleRepository
                            .findByName(roleName)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Unknown role: " + roleName));
            newRoles.add(role);
        }

        user.getRoles().clear();
        user.getRoles().addAll(newRoles);

        return toResponse(user);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserResponse toResponse(User user) {
        Set<String> roleNames =
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.isActive(),
                roleNames);
    }
}
