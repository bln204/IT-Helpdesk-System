package com.example.ticketing.validation;

import com.example.ticketing.auth.UserDtos.UserCreateRequest;
import com.example.ticketing.auth.UserRole;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link DepartmentRequired} annotation.
 * 
 * Checks that departmentId is provided when the role is NOT ADMIN.
 * ADMIN users are allowed to have null departmentId since they don't belong to any department.
 */
public class DepartmentRequiredValidator 
        implements ConstraintValidator<DepartmentRequired, UserCreateRequest> {

    @Override
    public void initialize(DepartmentRequired constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(UserCreateRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true; // Let @NotNull handle null object validation
        }

        // If role is ADMIN, departmentId can be null
        if (request.getRole() == UserRole.Role.ADMIN) {
            return true;
        }

        // For all other roles, departmentId is required
        boolean isValid = request.getDepartmentId() != null;
        
        if (!isValid) {
            // Disable default constraint violation
            context.disableDefaultConstraintViolation();
            
            // Build custom message based on role
            String customMessage = buildCustomMessage(request.getRole());
            
            // Add custom constraint violation
            context.buildConstraintViolationWithTemplate(customMessage)
                    .addConstraintViolation();
        }
        
        return isValid;
    }
    
    private String buildCustomMessage(UserRole.Role role) {
        if (role == null) {
            return "Phòng ban không được để trống";
        }
        
        return switch (role) {
            case ADMIN -> "Phòng ban không được để trống (Admin không thuộc phòng ban nào)";
            case GIAM_DOC -> "Phòng ban không được để trống. Giám đốc phải thuộc phòng ban 'EXEC'.";
            case TRUONG_PHONG -> "Phòng ban không được để trống. Trưởng phòng phải thuộc một phòng ban.";
            case NHAN_VIEN -> "Phòng ban không được để trống. Nhân viên phải thuộc một phòng ban.";
        };
    }
}
