package com.example.ticketing.report;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report Service - Manage report configurations.
 */
@Service
@Transactional
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportConfigRepository reportConfigRepository;

    public ReportService(ReportConfigRepository reportConfigRepository) {
        this.reportConfigRepository = reportConfigRepository;
    }

    /**
     * Get all accessible reports for a user.
     */
    @Transactional(readOnly = true)
    public List<ReportConfig> getAccessibleReports(String username) {
        return reportConfigRepository.findAccessibleReports(username);
    }

    /**
     * Get public reports.
     */
    @Transactional(readOnly = true)
    public List<ReportConfig> getPublicReports() {
        return reportConfigRepository.findByIsPublicTrueOrderByNameAsc();
    }

    /**
     * Get reports by category.
     */
    @Transactional(readOnly = true)
    public List<ReportConfig> getReportsByCategory(String category) {
        return reportConfigRepository.findByCategoryOrderByNameAsc(category);
    }

    /**
     * Get report by ID.
     */
    @Transactional(readOnly = true)
    public ReportConfig getReportById(Long id) {
        return reportConfigRepository.findById(id)
                .orElseThrow(() -> new ReportNotFoundException(id));
    }

    /**
     * Create report config.
     */
    public ReportConfig createReport(ReportConfig report, String createdBy) {
        log.info("Creating report: {}", report.getName());
        report.setCreatedBy(createdBy);
        return reportConfigRepository.save(report);
    }

    /**
     * Update report config.
     */
    public ReportConfig updateReport(Long id, ReportConfig updates, String updatedBy) {
        ReportConfig existing = getReportById(id);
        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setReportType(updates.getReportType());
        existing.setCategory(updates.getCategory());
        existing.setConfig(updates.getConfig());
        existing.setOutputFormat(updates.getOutputFormat());
        existing.setIsPublic(updates.getIsPublic());
        existing.setAllowedRoles(updates.getAllowedRoles());
        return reportConfigRepository.save(existing);
    }

    /**
     * Delete report.
     */
    public void deleteReport(Long id) {
        log.info("Deleting report: {}", id);
        reportConfigRepository.deleteById(id);
    }

    // ==================== Exceptions ====================

    public static class ReportNotFoundException extends RuntimeException {
        public ReportNotFoundException(Long id) {
            super("Không tìm thấy Report với ID: " + id);
        }
    }
}
