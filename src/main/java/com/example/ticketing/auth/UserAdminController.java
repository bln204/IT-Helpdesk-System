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

    /**
     * Tạo user mới.
     * - ADMIN/GIAM_DOC: tạo user (tự động duyệt)
     * - TRUONG_PHONG: tạo user (cần duyệt, chỉ NHAN_VIEN)
     * - NHAN_VIEN: không được tạo
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG')")
    public ResponseEntity<UserDtos.UserResponse> createUser(
        @Valid @RequestBody UserDtos.UserCreateRequest request,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        
        // GIAM_DOC chỉ được tạo user, không được tự động duyệt
        // (Admin duyệt giúp)
        if ("GIAM_DOC".equals(actor.getRole().name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Giám đốc không có quyền tạo tài khoản người dùng.");
        }
        
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

    /**
     * Lấy danh sách users.
     * - ADMIN: xem tất cả
     * - GIAM_DOC: xem tất cả
     * - TRUONG_PHONG: xem users trong phòng mình
     */
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
    
    /**
     * Lấy danh sách users đang chờ duyệt.
     * Chỉ ADMIN mới xem được.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDtos.UserResponse> listPendingUsers(Authentication authentication) {
        return userAdminService.listPendingApproval().stream()
            .map(UserDtos.UserResponse::from)
            .toList();
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
    
    /**
     * Xem chi tiết một user.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG')")
    public UserDtos.UserResponse getUser(
        @PathVariable Long id,
        Authentication authentication
    ) {
        return UserDtos.UserResponse.from(userAdminService.getUser(id));
    }

    /**
     * Cập nhật enabled status.
     * - ADMIN: enable/disable tất cả
     * - GIAM_DOC: enable/disable tất cả (trừ ADMIN)
     * - TRUONG_PHONG: không được thay đổi trực tiếp
     */
    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
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

    /**
     * Cập nhật role.
     * - ADMIN: đổi tất cả
     * - GIAM_DOC: đổi tất cả (trừ ADMIN)
     * - TRUONG_PHONG: không được đổi
     */
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

    /**
     * Cập nhật profile.
     * - User tự sửa profile của mình (sau khi được duyệt)
     * - ADMIN sửa profile bất kỳ user
     * - TRUONG_PHONG sửa profile user trong phòng mình
     * - GIAM_DOC sửa profile user khác
     */
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
                actor.getRole().name(),
                actor.getDepartmentId()
            )
        );
    }

    /**
     * Reset password.
     * - ADMIN/GIAM_DOC: reset password tất cả users
     */
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

    /**
     * Xóa user.
     * - ADMIN: xóa tất cả (trừ admin khác)
     * - GIAM_DOC: xóa được NHAN_VIEN, TRUONG_PHONG
     * - TRUONG_PHONG: xóa NHAN_VIEN trong phòng mình
     */
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
