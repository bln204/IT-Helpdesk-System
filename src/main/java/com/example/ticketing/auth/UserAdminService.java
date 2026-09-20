package com.example.ticketing.auth;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class UserAdminService {
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuditService userAuditService;

    public UserAdminService(
        UserAccountRepository userAccountRepository,
        DepartmentRepository departmentRepository,
        PasswordEncoder passwordEncoder,
        UserAuditService userAuditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.userAuditService = userAuditService;
    }

    /**
     * Tạo user mới với department
     */
    public UserAccount createUser(
        String username,
        String rawPassword,
        UserRole.Role role,
        Boolean enabled,
        String displayName,
        String title,
        String avatarUrl,
        String email,
        Long departmentId,
        String actorUsername,
        String actorRole
    ) {
        if (userAccountRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }
        rejectAvatarChange(avatarUrl);

        // Validate department
        Department department = null;
        if (departmentId != null) {
            department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department not found."));
            // ADMIN không thuộc department
            if (role == UserRole.Role.ADMIN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin cannot belong to a department.");
            }
        } else if (role != UserRole.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Non-admin users must belong to a department.");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setDepartment(department);
        user.setDisplayName(displayName);
        user.setTitle(title);
        user.setEmail(email);
        if (enabled != null) {
            user.setEnabled(enabled);
        }

        UserAccount created = userAccountRepository.save(user);
        userAuditService.log(
            UserAuditAction.USER_CREATED,
            actorUsername,
            actorRole,
            created.getUsername()
        );
        return created;
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> listUsers(Pageable pageable, String search) {
        if (search != null && !search.isBlank()) {
            return userAccountRepository.findByUsernameContainingIgnoreCase(search.trim(), pageable);
        }
        return userAccountRepository.findAll(pageable);
    }

    /**
     * Lấy users theo department
     */
    @Transactional(readOnly = true)
    public List<UserAccount> listUsersByDepartment(Long departmentId) {
        return userAccountRepository.findByDepartmentIdAndEnabledTrueOrderByUsernameAsc(departmentId);
    }

    /**
     * Lấy TRUONG_PHONG theo department
     */
    @Transactional(readOnly = true)
    public List<UserAccount> listTruongPhongByDepartment(Long departmentId) {
        return userAccountRepository.findByDepartmentIdAndRoleAndEnabledTrueOrderByUsernameAsc(
            departmentId, UserRole.Role.TRUONG_PHONG);
    }

    @Transactional(readOnly = true)
    public List<UserAudit> listAudit() {
        return userAuditService.listAll();
    }

    @Transactional(readOnly = true)
    public List<UserAudit> listAudit(String targetUsername) {
        return userAuditService.listForTarget(targetUsername);
    }

    /**
     * Lấy danh sách IT staff (cho assign tickets)
     */
    @Transactional(readOnly = true)
    public List<UserAccount> listITStaff() {
        Department itDept = departmentRepository.findByCode("IT")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "IT department not found."));
        return userAccountRepository.findByDepartmentIdAndEnabledTrueOrderByUsernameAsc(itDept.getId());
    }

    /**
     * Cập nhật enabled status
     * - ADMIN: có thể enable/disable tất cả
     * - GIAM_DOC: có thể enable/disable tất cả (trừ ADMIN)
     * - TRUONG_PHONG: chỉ enable/disable NHAN_VIEN trong phòng mình
     */
    public UserAccount updateEnabled(
        Long id,
        boolean enabled,
        String actorUsername,
        String actorRole,
        Long actorDepartmentId
    ) {
        UserAccount user = getUser(id);

        // Check permissions
        boolean canModify = switch (actorRole) {
            case "ADMIN" -> true;
            case "GIAM_DOC" -> user.getRole() != UserRole.Role.ADMIN;
            case "TRUONG_PHONG" -> {
                // Chỉ được sửa user cùng department và là NHAN_VIEN
                if (user.getRole() != UserRole.Role.NHAN_VIEN) {
                    yield false;
                }
                if (user.getDepartment() == null || !user.getDepartment().getId().equals(actorDepartmentId)) {
                    yield false;
                }
                yield true;
            }
            default -> false;
        };

        if (!canModify) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to modify this user's status.");
        }

        user.setEnabled(enabled);
        userAuditService.log(
            enabled ? UserAuditAction.USER_ENABLED : UserAuditAction.USER_DISABLED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        return user;
    }

    /**
     * Cập nhật role
     * - ADMIN: có thể đổi tất cả
     * - GIAM_DOC: có thể đổi tất cả (trừ ADMIN)
     * - TRUONG_PHONG: không được đổi role
     */
    public UserAccount updateRole(
        Long id,
        UserRole.Role role,
        String actorUsername,
        String actorRole
    ) {
        UserAccount user = getUser(id);

        // Permission check
        if ("TRUONG_PHONG".equals(actorRole) || "NHAN_VIEN".equals(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN and GIAM_DOC can change roles.");
        }

        // Cannot change ADMIN role
        if (user.getRole() == UserRole.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot change ADMIN role.");
        }

        user.setRole(role);
        userAuditService.log(
            UserAuditAction.ROLE_CHANGED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        return user;
    }

    /**
     * Cập nhật profile
     */
    public UserAccount updateProfile(
        Long id,
        String displayName,
        String title,
        String avatarUrl,
        String email,
        String actorUsername,
        String actorRole
    ) {
        UserAccount user = getUser(id);
        rejectAvatarChange(user.getAvatarUrl(), avatarUrl);

        // Check if actor can modify this user
        // User có thể tự sửa profile của mình, hoặc ADMIN/GIAM_DOC sửa user khác
        boolean isSelf = user.getUsername().equals(actorUsername);
        boolean isElevated = "ADMIN".equals(actorRole) || "GIAM_DOC".equals(actorRole);

        if (!isSelf && !isElevated) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own profile.");
        }

        // Non-elevated users cannot change email
        if (!isElevated && email != null && !email.equals(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change your email address.");
        }

        user.setDisplayName(displayName);
        user.setTitle(title);
        user.setEmail(email);

        userAuditService.log(
            UserAuditAction.PROFILE_UPDATED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        return user;
    }

    /**
     * Reset password
     * - ADMIN/GIAM_DOC: reset được password tất cả users
     * - User thường: không reset được
     */
    public UserAccount resetPassword(
        Long id,
        String rawPassword,
        String actorUsername,
        String actorRole
    ) {
        UserAccount user = getUser(id);

        // Only ADMIN and GIAM_DOC can reset passwords
        if (!"ADMIN".equals(actorRole) && !"GIAM_DOC".equals(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only ADMIN and GIAM_DOC can reset passwords.");
        }

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userAuditService.log(
            UserAuditAction.PASSWORD_RESET,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        return user;
    }

    /**
     * Xóa user
     * - Không thể xóa chính mình
     * - ADMIN: xóa được tất cả (trừ admin khác)
     * - GIAM_DOC: xóa được NHAN_VIEN, TRUONG_PHONG (không phải cùng department)
     * - TRUONG_PHONG: xóa được NHAN_VIEN trong phòng mình
     */
    public void deleteUser(
        Long id,
        String actorUsername,
        String actorRole,
        Long actorDepartmentId
    ) {
        UserAccount user = getUser(id);

        // Cannot delete self
        if (user.getUsername().equals(actorUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete your own account.");
        }

        // Cannot delete ADMIN
        if (user.getRole() == UserRole.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete ADMIN accounts.");
        }

        // Permission check
        boolean canDelete = switch (actorRole) {
            case "ADMIN" -> true;
            case "GIAM_DOC" -> user.getRole() != UserRole.Role.ADMIN;
            case "TRUONG_PHONG" -> {
                // Chỉ được xóa NHAN_VIEN trong phòng mình
                if (user.getRole() != UserRole.Role.NHAN_VIEN) {
                    yield false;
                }
                if (user.getDepartment() == null || !user.getDepartment().getId().equals(actorDepartmentId)) {
                    yield false;
                }
                yield true;
            }
            default -> false;
        };

        if (!canDelete) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to delete this user.");
        }

        userAuditService.log(
            UserAuditAction.USER_DELETED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        userAccountRepository.delete(user);
    }

    public UserAccount getUser(Long id) {
        return userAccountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    public UserAccount getUserByUsername(String username) {
        return userAccountRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private void rejectAvatarChange(String avatarUrl) {
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar changes are disabled.");
        }
    }

    private void rejectAvatarChange(String currentAvatarUrl, String requestedAvatarUrl) {
        if (!normalize(currentAvatarUrl).equals(normalize(requestedAvatarUrl))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar changes are disabled.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
