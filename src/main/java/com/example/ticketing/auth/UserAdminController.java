package com.example.ticketing.auth;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserAdminController {
    private final UserAdminService userAdminService;
    private final UserAccountRepository userAccountRepository;

    public UserAdminController(UserAdminService userAdminService, UserAccountRepository userAccountRepository) {
        this.userAdminService = userAdminService;
        this.userAccountRepository = userAccountRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<UserDtos.UserResponse> createUser(
        @Valid @RequestBody UserDtos.UserCreateRequest request,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        
        UserAccount user = userAdminService.createUser(
            request.getUsername(),
            request.getPassword(),
            request.getRole(),
            request.getEnabled(),
            request.getDisplayName(),
            request.getTitle(),
            request.getAvatarUrl(),
            request.getEmail(),
            request.getDepartmentId(),
            actor.getUsername(),
            actor.getRole().name()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDtos.UserResponse.from(user));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG')")
    public Page<UserDtos.UserResponse> listUsers(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String search
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("username").ascending());
        return userAdminService.listUsers(pageRequest, search).map(UserDtos.UserResponse::from);
    }

    @GetMapping("/engineers")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public List<UserDtos.UserResponse> listITStaff(Authentication authentication) {
        return userAdminService.listITStaff().stream()
            .map(UserDtos.UserResponse::from)
            .toList();
    }

    @GetMapping("/by-department/{departmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG')")
    public List<UserDtos.UserResponse> listUsersByDepartment(
        @PathVariable Long departmentId,
        Authentication authentication
    ) {
        return userAdminService.listUsersByDepartment(departmentId).stream()
            .map(UserDtos.UserResponse::from)
            .toList();
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public List<UserDtos.UserAuditResponse> listAudit(
        @RequestParam(required = false) String targetUsername,
        Authentication authentication
    ) {
        if (targetUsername == null || targetUsername.isBlank()) {
            return userAdminService.listAudit().stream()
                .map(UserDtos.UserAuditResponse::from)
                .toList();
        }
        return userAdminService.listAudit(targetUsername).stream()
            .map(UserDtos.UserAuditResponse::from)
            .toList();
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG')")
    public UserDtos.UserResponse updateEnabled(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.UserEnabledRequest request,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        return UserDtos.UserResponse.from(
            userAdminService.updateEnabled(
                id,
                request.getEnabled(),
                actor.getUsername(),
                actor.getRole().name(),
                actor.getDepartmentId()
            )
        );
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public UserDtos.UserResponse updateRole(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.UserRoleUpdateRequest request,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        return UserDtos.UserResponse.from(
            userAdminService.updateRole(
                id,
                request.getRole(),
                actor.getUsername(),
                actor.getRole().name()
            )
        );
    }

    @PatchMapping("/{id}/profile")
    public UserDtos.UserResponse updateProfile(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.UserProfileUpdateRequest request,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        return UserDtos.UserResponse.from(
            userAdminService.updateProfile(
                id,
                request.getDisplayName(),
                request.getTitle(),
                request.getAvatarUrl(),
                request.getEmail(),
                actor.getUsername(),
                actor.getRole().name()
            )
        );
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<Void> resetPassword(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.UserPasswordResetRequest request,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        userAdminService.resetPassword(
            id,
            request.getPassword(),
            actor.getUsername(),
            actor.getRole().name()
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG')")
    public ResponseEntity<Void> deleteUser(
        @PathVariable Long id,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        userAdminService.deleteUser(
            id,
            actor.getUsername(),
            actor.getRole().name(),
            actor.getDepartmentId()
        );
        return ResponseEntity.noContent().build();
    }

    private UserAccount getCurrentUser(Authentication authentication) {
        return userAccountRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }
}
