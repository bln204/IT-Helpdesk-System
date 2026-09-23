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
    public ResponseEntity<List<DepartmentResponse>> getActiveDepartments() {
        List<Department> departments = departmentService.getAllActiveDepartments();
        List<DepartmentResponse> response = departments.stream()
            .map(DepartmentResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy tất cả departments (bao gồm disabled) - chỉ ADMIN và GIAM_DOC
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<List<DepartmentDetailResponse>> getAllDepartments() {
        List<DepartmentService.DepartmentDetail> departments = departmentService.getAllDepartments().stream()
            .map(dept -> departmentService.getDepartmentDetail(dept.getId()))
            .toList();
        List<DepartmentDetailResponse> response = departments.stream()
            .map(DepartmentDetailResponse::from)
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
     * Lấy thông tin chi tiết department (kèm số users) - chỉ ADMIN và GIAM_DOC
     */
    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<DepartmentDetailResponse> getDepartmentDetail(@PathVariable Long id) {
        DepartmentService.DepartmentDetail detail = departmentService.getDepartmentDetail(id);
        return ResponseEntity.ok(DepartmentDetailResponse.from(detail));
    }
    
    /**
     * Tạo department mới (chỉ ADMIN và GIAM_DOC)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<DepartmentDetailResponse> createDepartment(
            @RequestBody DepartmentService.CreateDepartmentRequest request) {
        Department dept = departmentService.createDepartment(request);
        DepartmentService.DepartmentDetail detail = departmentService.getDepartmentDetail(dept.getId());
        return ResponseEntity.ok(DepartmentDetailResponse.from(detail));
    }
    
    /**
     * Cập nhật department (chỉ ADMIN và GIAM_DOC)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<DepartmentDetailResponse> updateDepartment(
            @PathVariable Long id,
            @RequestBody DepartmentService.UpdateDepartmentRequest request) {
        Department dept = departmentService.updateDepartment(id, request);
        DepartmentService.DepartmentDetail detail = departmentService.getDepartmentDetail(dept.getId());
        return ResponseEntity.ok(DepartmentDetailResponse.from(detail));
    }
    
    /**
     * Xóa department vĩnh viễn (chỉ ADMIN và GIAM_DOC)
     * Khi xóa, tất cả users thuộc department sẽ được cập nhật department = null
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.hardDeleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
    
    // Response DTO cho danh sách đơn giản
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
    
    // Response DTO cho thông tin chi tiết (kèm số users)
    public record DepartmentDetailResponse(
        Long id,
        String code,
        String name,
        String description,
        Long managerId,
        String managerName,
        boolean enabled,
        long userCount,
        String createdAt,
        String updatedAt
    ) {
        public static DepartmentDetailResponse from(DepartmentService.DepartmentDetail detail) {
            return new DepartmentDetailResponse(
                detail.id(),
                detail.code(),
                detail.name(),
                detail.description(),
                detail.managerId(),
                detail.managerName(),
                detail.enabled(),
                detail.userCount(),
                detail.createdAt() != null ? detail.createdAt().toString() : null,
                detail.updatedAt() != null ? detail.updatedAt().toString() : null
            );
        }
    }
}
