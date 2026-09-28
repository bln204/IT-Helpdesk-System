package com.example.ticketing.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho SoftwareCatalog entity.
 */
@Repository
public interface SoftwareCatalogRepository extends JpaRepository<SoftwareCatalog, Long> {

    List<SoftwareCatalog> findByComplianceStatusOrderByNameAsc(String complianceStatus);
}
