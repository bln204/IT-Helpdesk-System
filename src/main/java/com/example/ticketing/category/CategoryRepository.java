package com.example.ticketing.category;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho Category entity.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    /**
     * Tìm category theo code.
     */
    Category findByCode(String code);
    
    /**
     * Tìm tất cả top-level categories (không có parent).
     */
    List<Category> findByParentIsNullOrderByDisplayOrderAsc();
    
    /**
     * Tìm tất cả top-level categories được kích hoạt.
     */
    List<Category> findByParentIsNullAndEnabledTrueOrderByDisplayOrderAsc();
    
    /**
     * Tìm subcategories của một category.
     */
    List<Category> findByParentIdOrderByDisplayOrderAsc(Long parentId);
    
    /**
     * Tìm subcategories được kích hoạt của một category.
     */
    List<Category> findByParentIdAndEnabledTrueOrderByDisplayOrderAsc(Long parentId);
    
    /**
     * Tìm tất cả categories được kích hoạt.
     */
    List<Category> findByEnabledTrueOrderByDisplayOrderAsc();
    
    /**
     * Tìm categories theo parent (phân biệt top-level và subcategory).
     */
    @Query("SELECT c FROM Category c WHERE (c.parent IS NULL AND :isTopLevel = true) OR (c.parent IS NOT NULL AND :isTopLevel = false) ORDER BY c.displayOrder ASC")
    List<Category> findByTopLevelFlag(@Param("isTopLevel") boolean isTopLevel);
    
    /**
     * Đếm subcategories của một category.
     */
    long countByParentId(Long parentId);
    
    /**
     * Kiểm tra code đã tồn tại chưa.
     */
    boolean existsByCode(String code);
    
    /**
     * Kiểm tra tên đã tồn tại trong parent chưa.
     */
    boolean existsByNameAndParent(String name, Category parent);
    
    /**
     * Tìm category theo tên và parent (null cho top-level).
     */
    @Query("SELECT c FROM Category c WHERE c.name = :name AND ((:parentId IS NULL AND c.parent IS NULL) OR (:parentId IS NOT NULL AND c.parent.id = :parentId))")
    Category findByNameAndParentId(@Param("name") String name, @Param("parentId") Long parentId);
    
    /**
     * Tìm tất cả categories với subcategories (nested structure).
     * Sử dụng cho hierarchical dropdown.
     */
    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.subcategories WHERE c.parent IS NULL AND c.enabled = true ORDER BY c.displayOrder ASC")
    List<Category> findAllTopLevelWithSubcategories();
}
