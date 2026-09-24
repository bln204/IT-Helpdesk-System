package com.example.ticketing.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho CategoryTeamMapping entity.
 */
@Repository
public interface CategoryTeamMappingRepository extends JpaRepository<CategoryTeamMapping, Long> {
    
    /**
     * Tìm tất cả mappings cho một category.
     */
    List<CategoryTeamMapping> findByCategoryId(Long categoryId);
    
    /**
     * Tìm mappings được kích hoạt cho một category, sắp xếp theo priority giảm dần.
     */
    @Query("SELECT m FROM CategoryTeamMapping m WHERE m.category.id = :categoryId AND m.enabled = true ORDER BY m.priority DESC")
    List<CategoryTeamMapping> findEnabledByCategoryIdOrderByPriorityDesc(@Param("categoryId") Long categoryId);
    
    /**
     * Tìm team tốt nhất (priority cao nhất) cho một category.
     */
    @Query("SELECT m FROM CategoryTeamMapping m WHERE m.category.id = :categoryId AND m.enabled = true ORDER BY m.priority DESC LIMIT 1")
    Optional<CategoryTeamMapping> findBestTeamForCategory(@Param("categoryId") Long categoryId);
    
    /**
     * Tìm tất cả mappings cho một team.
     */
    List<CategoryTeamMapping> findByTeamId(Long teamId);
    
    /**
     * Tìm mappings được kích hoạt cho một team.
     */
    List<CategoryTeamMapping> findByTeamIdAndEnabledTrue(Long teamId);
    
    /**
     * Kiểm tra mapping đã tồn tại chưa.
     */
    boolean existsByCategoryIdAndTeamId(Long categoryId, Long teamId);
    
    /**
     * Xóa mapping theo category và team.
     */
    void deleteByCategoryIdAndTeamId(Long categoryId, Long teamId);
    
    /**
     * Đếm số category được map với một team.
     */
    long countByTeamId(Long teamId);
}
