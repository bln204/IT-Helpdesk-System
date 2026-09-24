package com.example.ticketing.category;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.team.Team;

/**
 * REST Controller cho Category operations.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    
    private final CategoryService categoryService;
    private final UserAccountRepository userAccountRepository;
    
    public CategoryController(CategoryService categoryService, UserAccountRepository userAccountRepository) {
        this.categoryService = categoryService;
        this.userAccountRepository = userAccountRepository;
    }
    
    // ============================================================
    // CATEGORY ENDPOINTS
    // ============================================================
    
    /**
     * Lấy categories với nested subcategories.
     * GET /api/categories?includeSubcategories=true
     */
    @GetMapping
    public ResponseEntity<List<Category.CategoryResponse>> listCategories(
        @RequestParam(defaultValue = "false") boolean includeSubcategories,
        Authentication authentication
    ) {
        List<Category> categories;
        if (includeSubcategories) {
            categories = categoryService.getAllCategoriesWithSubcategories();
        } else {
            categories = categoryService.getActiveTopLevelCategories();
        }
        
        List<Category.CategoryResponse> response = categories.stream()
            .map(Category.CategoryResponse::new)
            .collect(Collectors.toList());
        
        // Populate subcategories if requested
        if (includeSubcategories) {
            for (int i = 0; i < response.size(); i++) {
                Category cat = categories.get(i);
                if (cat.getSubcategories() != null && !cat.getSubcategories().isEmpty()) {
                    response.get(i).setSubcategories(
                        cat.getSubcategories().stream()
                            .map(Category.CategoryResponse::new)
                            .collect(Collectors.toList())
                    );
                }
            }
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy category theo ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Category.CategoryResponse> getCategory(@PathVariable Long id) {
        Category category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(new Category.CategoryResponse(category));
    }
    
    /**
     * Lấy subcategories của một category.
     * GET /api/categories/{parentId}/subcategories
     */
    @GetMapping("/{parentId}/subcategories")
    public ResponseEntity<List<Category.CategoryResponse>> getSubcategories(
        @PathVariable Long parentId,
        Authentication authentication
    ) {
        List<Category> subcategories = categoryService.getActiveSubcategories(parentId);
        return ResponseEntity.ok(subcategories.stream()
            .map(Category.CategoryResponse::new)
            .toList());
    }
    
    /**
     * Tạo category mới (Admin only).
     */
    @PostMapping
    public ResponseEntity<Category.CategoryResponse> createCategory(
        @RequestBody Category category,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        Category created = categoryService.createCategory(category);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new Category.CategoryResponse(created));
    }
    
    /**
     * Cập nhật category (Admin only).
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Category.CategoryResponse> updateCategory(
        @PathVariable Long id,
        @RequestBody Category updates,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        Category updated = categoryService.updateCategory(id, updates);
        return ResponseEntity.ok(new Category.CategoryResponse(updated));
    }
    
    /**
     * Disable category (Admin only).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disableCategory(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        categoryService.disableCategory(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Enable category (Admin only).
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableCategory(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        categoryService.enableCategory(id);
        return ResponseEntity.ok().build();
    }
    
    // ============================================================
    // CATEGORY-TEAM MAPPING ENDPOINTS
    // ============================================================
    
    /**
     * Lấy team được recommend cho một category.
     * GET /api/categories/{categoryId}/recommended-team
     */
    @GetMapping("/{categoryId}/recommended-team")
    public ResponseEntity<Team.TeamResponse> getRecommendedTeam(
        @PathVariable Long categoryId,
        Authentication authentication
    ) {
        return categoryService.getRecommendedTeamForCategory(categoryId)
            .map(team -> ResponseEntity.ok(new Team.TeamResponse(team)))
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Lấy tất cả teams được map với một category.
     * GET /api/categories/{categoryId}/teams
     */
    @GetMapping("/{categoryId}/teams")
    public ResponseEntity<List<Team.TeamResponse>> getTeamsForCategory(
        @PathVariable Long categoryId,
        Authentication authentication
    ) {
        List<Team> teams = categoryService.getTeamsForCategory(categoryId);
        return ResponseEntity.ok(teams.stream()
            .map(Team.TeamResponse::new)
            .toList());
    }
    
    /**
     * Thêm mapping giữa category và team.
     * POST /api/categories/{categoryId}/teams/{teamId}
     */
    @PostMapping("/{categoryId}/teams/{teamId}")
    public ResponseEntity<CategoryTeamMapping.CategoryTeamMappingResponse> addTeamMapping(
        @PathVariable Long categoryId,
        @PathVariable Long teamId,
        @RequestParam(required = false) Integer priority,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        CategoryTeamMapping mapping = categoryService.addTeamMapping(categoryId, teamId, priority);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CategoryTeamMapping.CategoryTeamMappingResponse(mapping));
    }
    
    /**
     * Cập nhật priority của mapping.
     * PATCH /api/categories/mappings/{mappingId}
     */
    @PatchMapping("/mappings/{mappingId}")
    public ResponseEntity<CategoryTeamMapping.CategoryTeamMappingResponse> updateMappingPriority(
        @PathVariable Long mappingId,
        @RequestBody CategoryTeamMapping mapping,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        CategoryTeamMapping updated = categoryService.updateMappingPriority(mappingId, mapping.getPriority());
        return ResponseEntity.ok(new CategoryTeamMapping.CategoryTeamMappingResponse(updated));
    }
    
    /**
     * Xóa mapping.
     * DELETE /api/categories/mappings/{mappingId}
     */
    @DeleteMapping("/mappings/{mappingId}")
    public ResponseEntity<Void> removeMapping(
        @PathVariable Long mappingId,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        categoryService.removeTeamMapping(mappingId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Enable/Disable mapping.
     * PUT /api/categories/mappings/{mappingId}/enabled
     */
    @PutMapping("/mappings/{mappingId}/enabled")
    public ResponseEntity<CategoryTeamMapping.CategoryTeamMappingResponse> setMappingEnabled(
        @PathVariable Long mappingId,
        @RequestParam boolean enabled,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        CategoryTeamMapping updated = categoryService.setMappingEnabled(mappingId, enabled);
        return ResponseEntity.ok(new CategoryTeamMapping.CategoryTeamMappingResponse(updated));
    }
    
    /**
     * Lấy tất cả mappings (Admin only).
     * GET /api/categories/mappings
     */
    @GetMapping("/mappings")
    public ResponseEntity<List<CategoryTeamMapping.CategoryTeamMappingResponse>> getAllMappings(
        Authentication authentication
    ) {
        requireAdmin(authentication);
        List<CategoryTeamMapping> mappings = categoryService.getAllMappings();
        return ResponseEntity.ok(mappings.stream()
            .map(CategoryTeamMapping.CategoryTeamMappingResponse::new)
            .toList());
    }
    
    // ============================================================
    // HELPER METHODS
    // ============================================================
    
    private UserAccount getCurrentUser(Authentication authentication) {
        return userAccountRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }
    
    private void requireAdmin(Authentication authentication) {
        UserAccount user = getCurrentUser(authentication);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }
    }
}
