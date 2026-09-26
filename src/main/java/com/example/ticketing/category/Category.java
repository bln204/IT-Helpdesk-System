package com.example.ticketing.category;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entity đại diện cho Category (Danh mục ticket).
 * Hỗ trợ hierarchical structure: Category có thể có Subcategories.
 * 
 * Ví dụ:
 * - Network (Category)
 *   - Wi-Fi (Subcategory)
 *   - LAN (Subcategory)
 *   - VPN (Subcategory)
 */
@Entity
@Table(name = "categories")
public class Category {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 120)
    private String name;
    
    @Column(nullable = false, unique = true, length = 20)
    private String code;
    
    @Column(length = 500)
    private String description;
    
    /**
     * Parent category - NULL nếu là top-level category.
     * Nếu có giá trị, đây là một subcategory.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties({"parent", "subcategories", "description", "enabled", "displayOrder", "createdAt", "updatedAt"})
    private Category parent;
    
    /**
     * Các subcategories của category này.
     * Chỉ có giá trị khi đây là top-level category.
     */
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"parent", "subcategories", "description", "enabled", "displayOrder", "createdAt", "updatedAt"})
    private List<Category> subcategories;
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    @Column(name = "display_order")
    private Integer displayOrder = 0;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ============ Lifecycle Callbacks ============
    
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ============ Helper Methods ============
    
    /**
     * Kiểm tra đây có phải là top-level category không.
     */
    public boolean isTopLevel() {
        return parent == null;
    }
    
    /**
     * Kiểm tra đây có phải là subcategory không.
     */
    public boolean isSubcategory() {
        return parent != null;
    }
    
    /**
     * Lấy parent category ID.
     */
    public Long getParentId() {
        return parent != null ? parent.getId() : null;
    }
    
    // ============ Getters & Setters ============
    
    public Long getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Category getParent() {
        return parent;
    }
    
    public void setParent(Category parent) {
        this.parent = parent;
    }
    
    public List<Category> getSubcategories() {
        return subcategories;
    }
    
    public void setSubcategories(List<Category> subcategories) {
        this.subcategories = subcategories;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Integer getDisplayOrder() {
        return displayOrder;
    }
    
    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    // ============ DTO for API Response ============
    
    public static class CategoryResponse {
        private Long id;
        private String name;
        private String code;
        private String description;
        private Long parentId;
        private String parentName;
        private boolean isSubcategory;
        private boolean enabled;
        private Integer displayOrder;
        private LocalDateTime createdAt;
        
        // Subcategories (chỉ có cho top-level categories)
        private List<CategoryResponse> subcategories;
        
        public CategoryResponse() {}
        
        public CategoryResponse(Category category) {
            this.id = category.getId();
            this.name = category.getName();
            this.code = category.getCode();
            this.description = category.getDescription();
            this.parentId = category.getParentId();
            this.parentName = category.getParent() != null ? category.getParent().getName() : null;
            this.isSubcategory = category.isSubcategory();
            this.enabled = category.isEnabled();
            this.displayOrder = category.getDisplayOrder();
            this.createdAt = category.getCreatedAt();
        }
        
        // Getters
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getCode() { return code; }
        public String getDescription() { return description; }
        public Long getParentId() { return parentId; }
        public String getParentName() { return parentName; }
        public boolean isSubcategory() { return isSubcategory; }
        public boolean isEnabled() { return enabled; }
        public Integer getDisplayOrder() { return displayOrder; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public List<CategoryResponse> getSubcategories() { return subcategories; }
        
        // Setters
        public void setId(Long id) { this.id = id; }
        public void setName(String name) { this.name = name; }
        public void setCode(String code) { this.code = code; }
        public void setDescription(String description) { this.description = description; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public void setParentName(String parentName) { this.parentName = parentName; }
        public void setSubcategory(boolean isSubcategory) { this.isSubcategory = isSubcategory; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public void setSubcategories(List<CategoryResponse> subcategories) { this.subcategories = subcategories; }
    }
}
