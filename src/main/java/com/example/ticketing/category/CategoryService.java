package com.example.ticketing.category;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.team.Team;
import com.example.ticketing.team.TeamRepository;

/**
 * Service cho Category operations.
 */
@Service
@Transactional
public class CategoryService {
    
    private final CategoryRepository categoryRepository;
    private final CategoryTeamMappingRepository categoryTeamMappingRepository;
    private final TeamRepository teamRepository;
    
    public CategoryService(
            CategoryRepository categoryRepository,
            CategoryTeamMappingRepository categoryTeamMappingRepository,
            TeamRepository teamRepository) {
        this.categoryRepository = categoryRepository;
        this.categoryTeamMappingRepository = categoryTeamMappingRepository;
        this.teamRepository = teamRepository;
    }
    
    // ============================================================
    // CATEGORY OPERATIONS
    // ============================================================
    
    /**
     * Lấy tất cả top-level categories (không có subcategories).
     */
    @Transactional(readOnly = true)
    public List<Category> getTopLevelCategories() {
        return categoryRepository.findByParentIsNullOrderByDisplayOrderAsc();
    }
    
    /**
     * Lấy tất cả top-level categories được kích hoạt.
     */
    @Transactional(readOnly = true)
    public List<Category> getActiveTopLevelCategories() {
        return categoryRepository.findByParentIsNullAndEnabledTrueOrderByDisplayOrderAsc();
    }
    
    /**
     * Lấy subcategories của một category.
     */
    @Transactional(readOnly = true)
    public List<Category> getSubcategories(Long parentId) {
        return categoryRepository.findByParentIdOrderByDisplayOrderAsc(parentId);
    }
    
    /**
     * Lấy subcategories được kích hoạt của một category.
     */
    @Transactional(readOnly = true)
    public List<Category> getActiveSubcategories(Long parentId) {
        return categoryRepository.findByParentIdAndEnabledTrueOrderByDisplayOrderAsc(parentId);
    }
    
    /**
     * Lấy category theo ID.
     */
    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new CategoryNotFoundException(id));
    }
    
    /**
     * Lấy category theo code.
     */
    @Transactional(readOnly = true)
    public Category getCategoryByCode(String code) {
        return categoryRepository.findByCode(code);
    }
    
    /**
     * Lấy tất cả categories với subcategories (nested structure).
     */
    @Transactional(readOnly = true)
    public List<Category> getAllCategoriesWithSubcategories() {
        return categoryRepository.findAllTopLevelWithSubcategories();
    }
    
    /**
     * Tạo category mới.
     */
    public Category createCategory(Category category) {
        if (categoryRepository.existsByCode(category.getCode())) {
            throw new CategoryAlreadyExistsException("Category code already exists: " + category.getCode());
        }
        
        if (category.getParent() != null && category.getParent().getId() != null) {
            Category parent = getCategoryById(category.getParent().getId());
            if (parent.getParent() != null) {
                throw new IllegalArgumentException("Cannot create subcategory under a subcategory.");
            }
            category.setParent(parent);
        }
        
        return categoryRepository.save(category);
    }
    
    /**
     * Cập nhật category.
     */
    public Category updateCategory(Long id, Category updates) {
        Category existing = getCategoryById(id);
        
        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getCode() != null && !updates.getCode().equals(existing.getCode())) {
            if (categoryRepository.existsByCode(updates.getCode())) {
                throw new CategoryAlreadyExistsException("Category code already exists: " + updates.getCode());
            }
            existing.setCode(updates.getCode());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getDisplayOrder() != null) {
            existing.setDisplayOrder(updates.getDisplayOrder());
        }
        if (updates.isEnabled() != existing.isEnabled()) {
            existing.setEnabled(updates.isEnabled());
        }
        
        return categoryRepository.save(existing);
    }
    
    /**
     * Xóa category (soft delete - disable).
     */
    public void disableCategory(Long id) {
        Category category = getCategoryById(id);
        category.setEnabled(false);
        categoryRepository.save(category);
    }
    
    /**
     * Enable category.
     */
    public void enableCategory(Long id) {
        Category category = getCategoryById(id);
        category.setEnabled(true);
        categoryRepository.save(category);
    }
    
    // ============================================================
    // CATEGORY-TEAM MAPPING OPERATIONS
    // ============================================================
    
    /**
     * Lấy team được recommend cho một category (priority cao nhất).
     */
    @Transactional(readOnly = true)
    public Optional<Team> getRecommendedTeamForCategory(Long categoryId) {
        return categoryTeamMappingRepository.findBestTeamForCategory(categoryId)
            .map(CategoryTeamMapping::getTeam);
    }
    
    /**
     * Lấy tất cả teams được map với một category.
     */
    @Transactional(readOnly = true)
    public List<Team> getTeamsForCategory(Long categoryId) {
        return categoryTeamMappingRepository.findByCategoryId(categoryId).stream()
            .map(CategoryTeamMapping::getTeam)
            .collect(Collectors.toList());
    }
    
    /**
     * Thêm mapping giữa category và team.
     */
    public CategoryTeamMapping addTeamMapping(Long categoryId, Long teamId, Integer priority) {
        Category category = getCategoryById(categoryId);
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new TeamNotFoundException(teamId));
        
        if (categoryTeamMappingRepository.existsByCategoryIdAndTeamId(categoryId, teamId)) {
            throw new MappingAlreadyExistsException(
                "Mapping already exists for category " + categoryId + " and team " + teamId);
        }
        
        CategoryTeamMapping mapping = new CategoryTeamMapping();
        mapping.setCategory(category);
        mapping.setTeam(team);
        mapping.setPriority(priority != null ? priority : 0);
        
        return categoryTeamMappingRepository.save(mapping);
    }
    
    /**
     * Cập nhật priority của mapping.
     */
    public CategoryTeamMapping updateMappingPriority(Long mappingId, Integer newPriority) {
        CategoryTeamMapping mapping = categoryTeamMappingRepository.findById(mappingId)
            .orElseThrow(() -> new MappingNotFoundException(mappingId));
        
        mapping.setPriority(newPriority);
        return categoryTeamMappingRepository.save(mapping);
    }
    
    /**
     * Xóa mapping.
     */
    public void removeTeamMapping(Long mappingId) {
        if (!categoryTeamMappingRepository.existsById(mappingId)) {
            throw new MappingNotFoundException(mappingId);
        }
        categoryTeamMappingRepository.deleteById(mappingId);
    }
    
    /**
     * Enable/Disable mapping.
     */
    public CategoryTeamMapping setMappingEnabled(Long mappingId, boolean enabled) {
        CategoryTeamMapping mapping = categoryTeamMappingRepository.findById(mappingId)
            .orElseThrow(() -> new MappingNotFoundException(mappingId));
        
        mapping.setEnabled(enabled);
        return categoryTeamMappingRepository.save(mapping);
    }
    
    /**
     * Lấy tất cả mappings.
     */
    @Transactional(readOnly = true)
    public List<CategoryTeamMapping> getAllMappings() {
        return categoryTeamMappingRepository.findAll();
    }
    
    /**
     * Lấy mappings cho một team.
     */
    @Transactional(readOnly = true)
    public List<CategoryTeamMapping> getMappingsForTeam(Long teamId) {
        return categoryTeamMappingRepository.findByTeamId(teamId);
    }
    
    // ============================================================
    // EXCEPTIONS
    // ============================================================
    
    public static class CategoryNotFoundException extends RuntimeException {
        public CategoryNotFoundException(Long id) {
            super("Category not found with id: " + id);
        }
    }
    
    public static class CategoryAlreadyExistsException extends RuntimeException {
        public CategoryAlreadyExistsException(String message) {
            super(message);
        }
    }
    
    public static class TeamNotFoundException extends RuntimeException {
        public TeamNotFoundException(Long id) {
            super("Team not found with id: " + id);
        }
    }
    
    public static class MappingNotFoundException extends RuntimeException {
        public MappingNotFoundException(Long id) {
            super("Category-Team mapping not found with id: " + id);
        }
    }
    
    public static class MappingAlreadyExistsException extends RuntimeException {
        public MappingAlreadyExistsException(String message) {
            super(message);
        }
    }
}
