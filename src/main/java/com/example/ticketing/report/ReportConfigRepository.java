package com.example.ticketing.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho ReportConfig entity.
 */
@Repository
public interface ReportConfigRepository extends JpaRepository<ReportConfig, Long> {

    List<ReportConfig> findByIsPublicTrueOrderByNameAsc();

    List<ReportConfig> findByCategoryOrderByNameAsc(String category);

    List<ReportConfig> findByReportTypeOrderByNameAsc(ReportConfig.ReportType reportType);

    @Query("SELECT r FROM ReportConfig r ORDER BY r.name ASC")
    List<ReportConfig> findAllOrderByName();

    @Query("SELECT r FROM ReportConfig r WHERE " +
           "r.createdBy = :username OR r.isPublic = true " +
           "ORDER BY r.name ASC")
    List<ReportConfig> findAccessibleReports(@Param("username") String username);
}
