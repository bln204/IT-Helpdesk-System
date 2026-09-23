package com.example.ticketing.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserSelfService {
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuditService userAuditService;

    public UserSelfService(
        UserAccountRepository userAccountRepository,
        PasswordEncoder passwordEncoder,
        UserAuditService userAuditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.userAuditService = userAuditService;
    }

    @Transactional(readOnly = true)
    public UserAccount getByUsername(String username) {
        return userAccountRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    public void changePassword(String username, String currentPassword, String newPassword) {
        UserAccount user = getByUsername(username);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userAuditService.log(
            UserAuditAction.PASSWORD_CHANGED,
            user.getUsername(),
            user.getRole(),
            user.getUsername()
        );
    }

    public UserAccount updateProfile(
        String username,
        String displayName,
        String title,
        String avatarUrl,
        String email
    ) {
        UserAccount user = getByUsername(username);
        rejectAvatarChange(user.getAvatarUrl(), avatarUrl);
        user.setDisplayName(displayName);
        user.setTitle(title);
        user.setEmail(email);
        userAuditService.log(
            UserAuditAction.PROFILE_UPDATED,
            user.getUsername(),
            user.getRole(),
            user.getUsername()
        );
        return user;
    }

    private void rejectAvatarChange(String currentAvatarUrl, String requestedAvatarUrl) {
        String current = currentAvatarUrl == null ? "" : currentAvatarUrl.trim();
        String requested = requestedAvatarUrl == null ? "" : requestedAvatarUrl.trim();
        if (!current.equals(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar changes are disabled.");
        }
    }
}
