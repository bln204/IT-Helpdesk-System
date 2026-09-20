package com.example.ticketing.department;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DepartmentService {
    
    private final DepartmentRepository departmentRepository;
    
    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
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
        
        return departmentRepository.save(dept);
    }
    
    /**
     * Xóa department (soft delete - chỉ tắt enabled)
     */
    public void deleteDepartment(Long id) {
        Department dept = getDepartmentById(id);
        dept.setEnabled(false);
        departmentRepository.save(dept);
    }
    
    // DTOs
    public record CreateDepartmentRequest(
        String code,
        String name,
        String description
    ) {}
    
    public record UpdateDepartmentRequest(
        String name,
        String description,
        Boolean enabled
    ) {}
}
