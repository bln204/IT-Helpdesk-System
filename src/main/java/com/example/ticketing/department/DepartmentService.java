package com.example.ticketing.department;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;

@Service
@Transactional
public class DepartmentService {
    
    private final DepartmentRepository departmentRepository;
    private final com.example.ticketing.auth.UserAccountRepository userAccountRepository;
    
    public DepartmentService(
            DepartmentRepository departmentRepository,
            com.example.ticketing.auth.UserAccountRepository userAccountRepository) {
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
    }
    
    /**
     * Lấy tất cả departments đang hoạt động
     */
    @Transactional(readOnly = true)
    public List<Department> getAllActiveDepartments() {
        return departmentRepository.findByEnabledTrueOrderByCodeAsc();
    }
    
    /**
     * Lấy department theo ID
     */
    @Transactional(readOnly = true)
    public Department getDepartmentById(Long id) {
        return departmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Department not found: " + id));
    }
    
    /**
     * Lấy department theo code
     */
    @Transactional(readOnly = true)
    public Department getDepartmentByCode(String code) {
        return departmentRepository.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Department not found: " + code));
    }
    
    /**
     * Tạo department mới
     */
    public Department createDepartment(CreateDepartmentRequest request) {
        // Check duplicate code
        if (departmentRepository.findByCode(request.code()).isPresent()) {
            throw new IllegalArgumentException("Department code already exists: " + request.code());
        }
        
        Department dept = new Department();
        dept.setCode(request.code());
        dept.setName(request.name());
        dept.setDescription(request.description());
        dept.setEnabled(true);
        
        // Set manager if provided
        if (request.managerId() != null) {
            UserAccount manager = userAccountRepository.findById(request.managerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.managerId()));
            dept.setManager(manager);
            manager.setDepartment(dept);
            userAccountRepository.save(manager);
        }
        
        return departmentRepository.save(dept);
    }
    
    /**
     * Cập nhật department
     */
    public Department updateDepartment(Long id, UpdateDepartmentRequest request) {
        Department dept = getDepartmentById(id);
        
        if (request.name() != null) {
            dept.setName(request.name());
        }
        if (request.description() != null) {
            dept.setDescription(request.description());
        }
        if (request.enabled() != null) {
            dept.setEnabled(request.enabled());
        }
        if (request.managerId() != null) {
            UserAccount manager = userAccountRepository.findById(request.managerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.managerId()));
            dept.setManager(manager);
            manager.setDepartment(dept);
            userAccountRepository.save(manager);
        }
        
        return departmentRepository.save(dept);
    }
    
    /**
     * Xóa department vĩnh viễn (hard delete)
     * Khi xóa, tất cả users thuộc department sẽ được cập nhật department = null
     */
    public void hardDeleteDepartment(Long id) {
        Department dept = getDepartmentById(id);
        
        // Cập nhật tất cả users thuộc department này về null
        List<UserAccount> users = departmentRepository.findUsersByDepartmentId(id);
        for (UserAccount user : users) {
            user.setDepartment(null);
            userAccountRepository.save(user);
        }
        
        // Xóa vĩnh viễn department
        departmentRepository.delete(dept);
    }
    
    /**
     * Lấy tất cả departments (bao gồm cả disabled) - cho Admin/GiamDoc
     */
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAllOrderByCodeAsc();
    }
    
    /**
     * Đếm số users trong một department
     */
    @Transactional(readOnly = true)
    public long countUsersInDepartment(Long departmentId) {
        return departmentRepository.countUsersByDepartmentId(departmentId);
    }
    
    /**
     * Kiểm tra department có đang được sử dụng không
     */
    @Transactional(readOnly = true)
    public boolean isDepartmentInUse(Long departmentId) {
        return departmentRepository.countUsersByDepartmentId(departmentId) > 0;
    }
    
    /**
     * Lấy thông tin chi tiết department kèm số users
     */
    @Transactional(readOnly = true)
    public DepartmentDetail getDepartmentDetail(Long id) {
        Department dept = getDepartmentById(id);
        long userCount = departmentRepository.countUsersByDepartmentId(id);
        return new DepartmentDetail(dept, userCount);
    }
    
    // DTOs
    public record CreateDepartmentRequest(
        String code,
        String name,
        String description,
        Long managerId
    ) {}
    
    public record UpdateDepartmentRequest(
        String name,
        String description,
        Boolean enabled,
        Long managerId
    ) {}
    
    /**
     * DTO cho thông tin chi tiết department kèm số users
     */
    public record DepartmentDetail(
        Long id,
        String code,
        String name,
        String description,
        Long managerId,
        String managerName,
        boolean enabled,
        long userCount,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
    ) {
        public DepartmentDetail(Department dept, long userCount) {
            this(
                dept.getId(),
                dept.getCode(),
                dept.getName(),
                dept.getDescription(),
                dept.getManager() != null ? dept.getManager().getId() : null,
                dept.getManager() != null ? dept.getManager().getDisplayName() : null,
                dept.isEnabled(),
                userCount,
                dept.getCreatedAt(),
                dept.getUpdatedAt()
            );
        }
    }
}
