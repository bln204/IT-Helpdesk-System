package com.example.ticketing.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users/me")
@Validated
public class UserSelfController {
    private final UserSelfService userSelfService;

    public UserSelfController(UserSelfService userSelfService) {
        this.userSelfService = userSelfService;
    }

    @GetMapping
    public UserDtos.UserResponse getProfile(Authentication authentication) {
        return UserDtos.UserResponse.from(userSelfService.getByUsername(authentication.getName()));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
        @Valid @RequestBody UserDtos.UserPasswordChangeRequest request,
        Authentication authentication
    ) {
        userSelfService.changePassword(
            authentication.getName(),
            request.getCurrentPassword(),
            request.getNewPassword()
        );
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/profile")
    public UserDtos.UserResponse updateProfile(
        @Valid @RequestBody UserDtos.UserProfileUpdateRequest request,
        Authentication authentication
    ) {
        return UserDtos.UserResponse.from(
            userSelfService.updateProfile(
                authentication.getName(),
                request.getDisplayName(),
                request.getTitle(),
                request.getAvatarUrl(),
                request.getEmail()
            )
        );
    }
}
