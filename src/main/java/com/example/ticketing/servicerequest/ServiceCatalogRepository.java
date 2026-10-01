package com.example.ticketing.servicerequest;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho ServiceCatalogItem entity.
 */
@Repository
public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalogItem, Long> {

    List<ServiceCatalogItem> findByEnabledTrue();

    List<ServiceCatalogItem> findByEnabledTrueAndIsPublicTrue();

    List<ServiceCatalogItem> findByCategory(String category);

    @Query("SELECT DISTINCT s.category FROM ServiceCatalogItem s WHERE s.enabled = true AND s.category IS NOT NULL")
    List<String> findDistinctCategories();

    List<ServiceCatalogItem> findByEnabledTrueOrderByCategoryAscNameAsc();
}
