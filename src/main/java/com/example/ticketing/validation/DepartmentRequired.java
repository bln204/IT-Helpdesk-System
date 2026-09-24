package com.example.ticketing.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom validation annotation to require departmentId when role is not ADMIN.
 * 
 * This annotation should be placed on the CLASS level of the DTO.
 * The validation will check both 'role' and 'departmentId' fields:
 * - role is ADMIN → departmentId can be null
 * - role is NOT ADMIN → departmentId must NOT be null
 * 
 * Usage: Place @DepartmentRequired on the DTO class that has both 'role' and 'departmentId' fields.
 */
@Documented
@Constraint(validatedBy = DepartmentRequiredValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DepartmentRequired {
    
    String message() default "Phòng ban không được để trống";
    
    String departmentIdField() default "departmentId";
    
    String roleField() default "role";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
