package com.example.ticketing.department;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    
    private final DepartmentService departmentService;
    
    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }
    
    /**
     * Lấy danh sách tất cả departments đang hoạt động
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments() {
        List<Department> departments = departmentService.getAllActiveDepartments();
        List<DepartmentResponse> response = departments.stream()
            .map(DepartmentResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy department theo ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable Long id) {
        Department dept = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(DepartmentResponse.from(dept));
    }
    
    /**
     * Tạo department mới (chỉ ADMIN và GIAM_DOC)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @RequestBody DepartmentService.CreateDepartmentRequest request) {
        Department dept = departmentService.createDepartment(request);
        return ResponseEntity.ok(DepartmentResponse.from(dept));
    }
    
    /**
     * Cập nhật department (chỉ ADMIN và GIAM_DOC)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable Long id,
            @RequestBody DepartmentService.UpdateDepartmentRequest request) {
        Department dept = departmentService.updateDepartment(id, request);
        return ResponseEntity.ok(DepartmentResponse.from(dept));
    }
    
    /**
     * Xóa department (chỉ ADMIN và GIAM_DOC)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
    
    // Response DTO
    public record DepartmentResponse(
        Long id,
        String code,
        String name,
        String description,
        Long managerId,
        String managerName,
        boolean enabled
    ) {
        public static DepartmentResponse from(Department dept) {
            return new DepartmentResponse(
                dept.getId(),
                dept.getCode(),
                dept.getName(),
                dept.getDescription(),
                dept.getManager() != null ? dept.getManager().getId() : null,
                dept.getManager() != null ? dept.getManager().getDisplayName() : null,
                dept.isEnabled()
            );
        }
    }
}
