package com.example.ticketing.auth;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.exception.UserNotApprovedActionException;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class UserAdminService {
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuditService userAuditService;
    private final NotificationService notificationService;

    public UserAdminService(
        UserAccountRepository userAccountRepository,
        DepartmentRepository departmentRepository,
        PasswordEncoder passwordEncoder,
        UserAuditService userAuditService,
        NotificationService notificationService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.userAuditService = userAuditService;
        this.notificationService = notificationService;
    }

    /**
     * Tạo user mới với department.
     * - ADMIN tạo: tự động duyệt (approved = true, enabled = true)
     * - GIAM_DOC tạo: tự động duyệt (approved = true, enabled = true)
     * - TRUONG_PHONG tạo: cần duyệt (approved = false, enabled = false)
     * - NHAN_VIEN: không được tạo
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
        // Check if user already exists
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
        
        // ============ NEW: Approval Logic ============
        
        // ADMIN và GIAM_DOC tạo: tự động duyệt
        if ("ADMIN".equals(actorRole) || "GIAM_DOC".equals(actorRole)) {
            user.setApproved(true);
            user.setApprovedAt(LocalDateTime.now());
            user.setApprovedBy(actorUsername);
            user.setEnabled(true); // Tự động enabled khi admin tạo
            
            userAuditService.log(
                UserAuditAction.USER_CREATED,
                actorUsername,
                actorRole,
                username
            );
        }
        // TRUONG_PHONG tạo: cần duyệt
        else if ("TRUONG_PHONG".equals(actorRole)) {
            // TRUONG_PHONG chỉ có thể tạo NHAN_VIEN
            if (role != UserRole.Role.NHAN_VIEN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Trưởng phòng chỉ có thể tạo tài khoản cho nhân viên.");
            }
            
            user.setApproved(false);
            user.setApprovedAt(null);
            user.setApprovedBy(null);
            user.setEnabled(false); // Mặc định disabled cho đến khi duyệt
            user.setCreatedBy(actorUsername); // Track creator for notifications
            
            userAuditService.log(
                UserAuditAction.USER_PENDING_CREATED,
                actorUsername,
                actorRole,
                username,
                null,
                "Tạo bởi " + actorUsername + ", cần Admin duyệt"
            );
            
            // Gửi thông báo cho ADMIN
            notificationService.notifyAdminOfPendingAccount(username, displayName, actorUsername);
        }
        // NHAN_VIEN: không được tạo user
        else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Bạn không có quyền tạo tài khoản người dùng.");
        }

        return userAccountRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> listUsers(Pageable pageable, String search) {
        if (search != null && !search.isBlank()) {
            return userAccountRepository.findByUsernameContainingIgnoreCase(search.trim(), pageable);
        }
        return userAccountRepository.findAll(pageable);
    }
    
    /**
     * List all pending approval users (for Admin)
     */
    @Transactional(readOnly = true)
    public List<UserAccount> listPendingApproval() {
        return userAccountRepository.findPendingApproval();
    }
    
    /**
     * List pending approval users by department (for Admin)
     */
    @Transactional(readOnly = true)
    public List<UserAccount> listPendingApprovalByDepartment(Long departmentId) {
        return userAccountRepository.findPendingApprovalByDepartment(departmentId);
    }
    
    /**
     * Count pending approval users
     */
    @Transactional(readOnly = true)
    public long countPendingApproval() {
        return userAccountRepository.countPendingApproval();
    }

    /**
     * Lấy users theo department (bao gồm cả pending và disabled)
     */
    @Transactional(readOnly = true)
    public List<UserAccount> listUsersByDepartment(Long departmentId) {
        return userAccountRepository.findAllByDepartment(departmentId);
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
    
    // ============ NEW: Approval Methods ============
    
    /**
     * Approve a pending user account.
     * Only ADMIN can approve users.
     */
    public UserAccount approveUser(
        Long id,
        String actorUsername,
        String actorRole
    ) {
        // Only ADMIN can approve
        if (!"ADMIN".equals(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Chỉ Admin mới có quyền phê duyệt tài khoản.");
        }

        UserAccount user = getUser(id);

        // Check if already approved
        if (user.isApproved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tài khoản đã được phê duyệt trước đó.");
        }

        user.setApproved(true);
        user.setApprovedAt(LocalDateTime.now());
        user.setApprovedBy(actorUsername);
        user.setRejectionReason(null);
        user.setEnabled(true); // Auto-enable when approved

        userAuditService.log(
            UserAuditAction.USER_APPROVED,
            actorUsername,
            actorRole,
            user.getUsername(),
            "pending",
            "approved"
        );

        return userAccountRepository.save(user);
    }
    
    /**
     * Reject a pending user account.
     * Only ADMIN can reject users.
     */
    public UserAccount rejectUser(
        Long id,
        String reason,
        String actorUsername,
        String actorRole
    ) {
        // Only ADMIN can reject
        if (!"ADMIN".equals(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Chỉ Admin mới có quyền từ chối tài khoản.");
        }

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Lý do từ chối không được để trống.");
        }

        UserAccount user = getUser(id);

        // Check if already approved
        if (user.isApproved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Không thể từ chối tài khoản đã được phê duyệt.");
        }

        user.setApproved(false);
        user.setApprovedAt(LocalDateTime.now());
        user.setApprovedBy(actorUsername);
        user.setRejectionReason(reason);
        user.setEnabled(false);

        userAuditService.log(
            UserAuditAction.USER_REJECTED,
            actorUsername,
            actorRole,
            user.getUsername(),
            null,
            reason
        );

        return userAccountRepository.save(user);
    }
    
    // ============ END Approval Methods ============

    /**
     * Cập nhật enabled status.
     * - ADMIN: có thể enable/disable tất cả
     * - GIAM_DOC: có thể enable/disable tất cả (trừ ADMIN)
     * - TRUONG_PHONG: không được trực tiếp thay đổi, cần gửi yêu cầu
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
                // TRUONG_PHONG không được trực tiếp thay đổi enabled status
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Trưởng phòng không có quyền trực tiếp thay đổi trạng thái tài khoản. " +
                    "Vui lòng gửi yêu cầu đến Admin.");
            }
            default -> false;
        };

        if (!canModify) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to modify this user's status.");
        }

        // Cannot disable approved = false users (they are not active yet)
        if (!enabled && !user.isApproved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Không thể vô hiệu hoá tài khoản chưa được phê duyệt.");
        }

        user.setEnabled(enabled);
        userAuditService.log(
            enabled ? UserAuditAction.USER_ENABLED : UserAuditAction.USER_DISABLED,
            actorUsername,
            actorRole,
            user.getUsername(),
            String.valueOf(!enabled),
            String.valueOf(enabled)
        );
        
        return userAccountRepository.save(user);
    }

    /**
     * Cập nhật role.
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
        
        // Cannot change role of unapproved users to ADMIN or GIAM_DOC
        if (!user.isApproved() && (role == UserRole.Role.ADMIN || role == UserRole.Role.GIAM_DOC)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Không thể phân quyền ADMIN hoặc GIAM_DOC cho tài khoản chưa được phê duyệt.");
        }

        user.setRole(role);
        userAuditService.log(
            UserAuditAction.ROLE_CHANGED,
            actorUsername,
            actorRole,
            user.getUsername(),
            user.getRole().name(),
            role.name()
        );
        
        return userAccountRepository.save(user);
    }

    /**
     * Cập nhật profile.
     * - User có thể tự sửa profile của mình (sau khi được duyệt)
     * - ADMIN/GIAM_DOC có thể sửa profile của user khác
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
        boolean isSelf = user.getUsername().equals(actorUsername);
        boolean isElevated = "ADMIN".equals(actorRole) || "GIAM_DOC".equals(actorRole);

        if (!isSelf && !isElevated) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own profile.");
        }
        
        // Self-edit: check if account is approved
        if (isSelf && !user.isApproved()) {
            userAuditService.log(
                UserAuditAction.PROFILE_EDIT_REJECTED,
                actorUsername,
                actorRole,
                user.getUsername(),
                null,
                "Tài khoản chưa được phê duyệt"
            );
            throw new UserNotApprovedActionException("chỉnh sửa profile");
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
            user.getUsername(),
            null,
            "profile updated"
        );
        
        return userAccountRepository.save(user);
    }
    
    /**
     * Get account status for a user (for self-service status view).
     */
    @Transactional(readOnly = true)
    public UserAccount getAccountStatus(Long id, String actorUsername, String actorRole) {
        UserAccount user = getUser(id);
        
        // Log status view
        userAuditService.log(
            UserAuditAction.STATUS_VIEWED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        
        return user;
    }

    /**
     * Reset password.
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
        user.markPasswordChanged();
        
        userAuditService.log(
            UserAuditAction.PASSWORD_RESET,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        
        return userAccountRepository.save(user);
    }

    /**
     * Xóa user.
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
