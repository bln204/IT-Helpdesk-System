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
     * List users with pending delete requests (for Admin)
     */
    @Transactional(readOnly = true)
    public List<UserAccount> listPendingDeleteRequests() {
        return userAccountRepository.findPendingDeleteRequests();
    }

    /**
     * List users with pending delete requests by department
     */
    @Transactional(readOnly = true)
    public List<UserAccount> listPendingDeleteRequestsByDepartment(Long departmentId) {
        return userAccountRepository.findPendingDeleteRequestsByDepartment(departmentId);
    }

    /**
     * Count pending delete requests
     */
    @Transactional(readOnly = true)
    public long countPendingDeleteRequests() {
        return userAccountRepository.countPendingDeleteRequests();
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
        // #region agent debug log
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserAdminService.class);
        log.info("[DEBUG] rejectUser called - id={}, reason='{}', actorUsername={}, actorRole={}", 
            id, reason, actorUsername, actorRole);
        // #endregion

        // Only ADMIN can reject
        if (!"ADMIN".equals(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Chỉ Admin mới có quyền từ chối tài khoản.");
        }

        // #region agent debug log
        log.info("[DEBUG] rejectUser - actorRole check passed: {}", actorRole);
        // #endregion

        if (reason == null || reason.isBlank()) {
            // #region agent debug log
            log.info("[DEBUG] rejectUser - reason validation failed: reason='{}'", reason);
            // #endregion
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Lý do từ chối không được để trống.");
        }

        UserAccount user = getUser(id);

        // #region agent debug log
        log.info("[DEBUG] rejectUser - user found: id={}, username={}, approved={}", 
            user.getId(), user.getUsername(), user.isApproved());
        // #endregion

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
     * - ADMIN: có thể sửa profile của bất kỳ user nào
     * - TRUONG_PHONG: có thể sửa profile của user trong phòng ban mình
     * - GIAM_DOC: có thể sửa profile của user khác
     */
    public UserAccount updateProfile(
        Long id,
        String displayName,
        String title,
        String avatarUrl,
        String email,
        String actorUsername,
        String actorRole,
        Long actorDepartmentId
    ) {
        UserAccount user = getUser(id);
        rejectAvatarChange(user.getAvatarUrl(), avatarUrl);

        // Check if actor can modify this user
        boolean isSelf = user.getUsername().equals(actorUsername);
        
        // Permission check:
        // - Self: always allowed (if approved)
        // - ADMIN: always allowed
        // - GIAM_DOC: allowed
        // - TRUONG_PHONG: allowed only if user is in same department
        boolean canEdit = false;
        if (isSelf) {
            canEdit = true;
        } else if ("ADMIN".equals(actorRole)) {
            canEdit = true;
        } else if ("GIAM_DOC".equals(actorRole)) {
            canEdit = true;
        } else if ("TRUONG_PHONG".equals(actorRole)) {
            // TRUONG_PHONG can edit any user in their department (except other ADMIN/GIAM_DOC/TRUONG_PHONG)
            if (user.getRole() != UserRole.Role.ADMIN && 
                user.getRole() != UserRole.Role.GIAM_DOC && 
                user.getRole() != UserRole.Role.TRUONG_PHONG &&
                user.getDepartment() != null && 
                user.getDepartment().getId().equals(actorDepartmentId)) {
                canEdit = true;
            }
        }
        
        if (!canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Bạn không có quyền chỉnh sửa thông tin của tài khoản này.");
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

        // Track changes for notification
        StringBuilder changes = new StringBuilder();
        if (!normalize(displayName).equals(normalize(user.getDisplayName()))) {
            changes.append("Họ tên");
        }
        if (!normalize(title).equals(normalize(user.getTitle()))) {
            if (changes.length() > 0) changes.append(", ");
            changes.append("Chức danh");
        }
        if (!normalize(email).equals(normalize(user.getEmail()))) {
            if (changes.length() > 0) changes.append(", ");
            changes.append("Email");
        }
        final String changesStr = changes.toString();

        // Update fields
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
        
        UserAccount savedUser = userAccountRepository.save(user);
        
        // Send notification to the user whose profile was changed (only if not self-edit)
        if (!isSelf && savedUser.getEmail() != null && !savedUser.getEmail().isBlank()) {
            notificationService.notifyProfileChanged(
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getDisplayName(),
                actorUsername,
                actorRole,
                changesStr
            );
        }
        
        return savedUser;
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

        // Permission check - ADMIN can delete directly
        // TRUONG_PHONG can only request deletion (handled in frontend)
        boolean canDelete = switch (actorRole) {
            case "ADMIN" -> true;
            case "GIAM_DOC" -> user.getRole() != UserRole.Role.ADMIN;
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

    /**
     * Direct delete without permission check (for Admin approving delete requests from TRUONG_PHONG).
     */
    public void deleteUser(Long id) {
        UserAccount user = getUser(id);

        // Cannot delete ADMIN
        if (user.getRole() == UserRole.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete ADMIN accounts.");
        }

        userAuditService.log(
            UserAuditAction.USER_DELETED,
            "ADMIN",
            "ADMIN",
            user.getUsername()
        );
        userAccountRepository.delete(user);
    }

    /**
     * Request delete a user (TRUONG_PHONG requests Admin to delete).
     * Marks the user with pending deletion status and notifies admins.
     */
    public UserAccount requestDeleteUser(
        Long id,
        String actorUsername,
        String actorRole,
        Long actorDepartmentId
    ) {
        // Only TRUONG_PHONG can request deletion
        if (!"TRUONG_PHONG".equals(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Chỉ Trưởng phòng mới có thể gửi yêu cầu xóa tài khoản.");
        }

        UserAccount user = getUser(id);

        // Cannot delete self
        if (user.getUsername().equals(actorUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request delete your own account.");
        }

        // Cannot delete ADMIN
        if (user.getRole() == UserRole.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot request delete ADMIN accounts.");
        }

        // Can only request deletion of NHAN_VIEN in own department
        if (user.getRole() != UserRole.Role.NHAN_VIEN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Trưởng phòng chỉ có thể yêu cầu xóa tài khoản nhân viên.");
        }

        if (user.getDepartment() == null || !user.getDepartment().getId().equals(actorDepartmentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Trưởng phòng chỉ có thể yêu cầu xóa tài khoản trong phòng mình.");
        }

        // Check if already pending deletion
        if ("DELETE_PENDING".equals(user.getRejectionReason())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Yêu cầu xóa tài khoản này đang chờ Admin duyệt.");
        }

        // Set rejectionReason to indicate pending deletion
        user.setRejectionReason("DELETE_PENDING");
        user.setCreatedBy(actorUsername); // Track who requested the deletion

        userAuditService.log(
            UserAuditAction.USER_REJECTED,
            actorUsername,
            actorRole,
            user.getUsername(),
            null,
            "Yêu cầu xóa đang chờ Admin duyệt"
        );

        UserAccount savedUser = userAccountRepository.save(user);

        // Notify all admins
        notificationService.notifyAdminsOfDeleteRequest(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            actorUsername
        );

        return savedUser;
    }

    /**
     * Cancel a pending delete request.
     * ADMIN - can cancel any delete request
     * TRUONG_PHONG - can only cancel delete requests in their department
     * Removes the DELETE_PENDING status from the user.
     */
    public UserAccount cancelDeleteRequest(
        Long id,
        String actorUsername,
        String actorRole,
        Long actorDepartmentId
    ) {
        UserAccount user = getUser(id);

        // TRUONG_PHONG can only cancel delete requests for users in their department
        if (!"ADMIN".equals(actorRole)) {
            if (!actorDepartmentId.equals(user.getDepartmentId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn chỉ có thể hủy yêu cầu xóa trong phòng ban của mình.");
            }
        }

        // Check if user has DELETE_PENDING status
        if (!"DELETE_PENDING".equals(user.getRejectionReason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tài khoản này không có yêu cầu xóa nào đang chờ.");
        }

        // Clear the DELETE_PENDING status
        user.setRejectionReason(null);

        userAuditService.log(
            UserAuditAction.USER_REJECTED,
            actorUsername,
            actorRole,
            user.getUsername(),
            "DELETE_PENDING",
            "Đã hủy yêu cầu xóa"
        );

        return userAccountRepository.save(user);
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
