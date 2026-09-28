package com.example.ticketing.sla;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * Service cho SLA Policy management.
 * Cung cấp CRUD operations và cache để improve performance.
 */
@Service
@Transactional
public class SlaPolicyService {

    private static final Logger log = LoggerFactory.getLogger(SlaPolicyService.class);

    private final SlaPolicyRepository slaPolicyRepository;

    // Cache cho default policies (priority -> SlaPolicy)
    private Map<TicketPriority, SlaPolicy> defaultPolicyCache;

    public SlaPolicyService(SlaPolicyRepository slaPolicyRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
        // Initialize cache
        refreshDefaultPolicyCache();
    }

    // ==================== CRUD Operations ====================

    /**
     * Lấy tất cả SLA policies.
     */
    @Transactional(readOnly = true)
    public List<SlaPolicy> getAllPolicies() {
        return slaPolicyRepository.findAll();
    }

    /**
     * Lấy tất cả policies đang enabled.
     */
    @Transactional(readOnly = true)
    public List<SlaPolicy> getEnabledPolicies() {
        return slaPolicyRepository.findByEnabledTrue();
    }

    /**
     * Lấy policy theo ID.
     */
    @Transactional(readOnly = true)
    public Optional<SlaPolicy> getPolicyById(Long id) {
        return slaPolicyRepository.findById(id);
    }

    /**
     * Lấy policy theo ID, throw exception nếu không tìm thấy.
     */
    @Transactional(readOnly = true)
    public SlaPolicy getPolicyByIdOrThrow(Long id) {
        return slaPolicyRepository.findById(id)
                .orElseThrow(() -> new SlaPolicyNotFoundException(id));
    }

    /**
     * Tạo mới SLA policy.
     */
    @CacheEvict(value = "defaultPolicies", allEntries = true)
    public SlaPolicy createPolicy(SlaPolicy policy, String createdBy) {
        log.info("Creating new SLA policy: {} for priority {}", policy.getName(), policy.getPriority());

        // Validate
        validatePolicy(policy);

        // Nếu là default, unset other defaults for this priority
        if (Boolean.TRUE.equals(policy.getIsDefault())) {
            unsetOtherDefaultsForPriority(policy.getPriority(), null);
        }

        policy.setCreatedBy(createdBy);
        policy.setUpdatedBy(createdBy);

        SlaPolicy saved = slaPolicyRepository.save(policy);
        
        // Refresh cache if this is a default policy
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            refreshDefaultPolicyCache();
        }

        log.info("Created SLA policy with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Cập nhật SLA policy.
     */
    @CacheEvict(value = "defaultPolicies", allEntries = true)
    public SlaPolicy updatePolicy(Long id, SlaPolicy updates, String updatedBy) {
        log.info("Updating SLA policy ID: {}", id);

        SlaPolicy existing = getPolicyByIdOrThrow(id);

        // Validate
        validatePolicy(updates);

        // Nếu updates muốn set là default, unset other defaults
        if (Boolean.TRUE.equals(updates.getIsDefault()) && 
            !Boolean.TRUE.equals(existing.getIsDefault())) {
            unsetOtherDefaultsForPriority(existing.getPriority(), id);
        }

        // Update fields
        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setPriority(updates.getPriority());
        existing.setResponseMinutes(updates.getResponseMinutes());
        existing.setResolutionMinutes(updates.getResolutionMinutes());
        existing.setBusinessHoursOnly(updates.getBusinessHoursOnly());
        existing.setWarningThreshold(updates.getWarningThreshold());
        existing.setSecondResponseMinutes(updates.getSecondResponseMinutes());
        existing.setIsDefault(updates.getIsDefault());
        existing.setEnabled(updates.getEnabled());
        existing.setUpdatedBy(updatedBy);

        SlaPolicy saved = slaPolicyRepository.save(existing);

        // Refresh cache
        refreshDefaultPolicyCache();

        log.info("Updated SLA policy ID: {}", saved.getId());
        return saved;
    }

    /**
     * Xóa SLA policy.
     */
    @CacheEvict(value = "defaultPolicies", allEntries = true)
    public void deletePolicy(Long id) {
        log.info("Deleting SLA policy ID: {}", id);

        SlaPolicy policy = getPolicyByIdOrThrow(id);
        
        // Kiểm tra nếu là default policy
        if (Boolean.TRUE.equals(policy.getIsDefault())) {
            throw new IllegalStateException("Không thể xóa policy mặc định. Hãy set một policy khác làm default trước.");
        }

        slaPolicyRepository.delete(policy);
        
        // Refresh cache
        refreshDefaultPolicyCache();

        log.info("Deleted SLA policy ID: {}", id);
    }

    /**
     * Toggle enabled status của policy.
     */
    @CacheEvict(value = "defaultPolicies", allEntries = true)
    public SlaPolicy toggleEnabled(Long id, boolean enabled, String updatedBy) {
        SlaPolicy policy = getPolicyByIdOrThrow(id);

        // Nếu muốn disable default policy
        if (!enabled && Boolean.TRUE.equals(policy.getIsDefault())) {
            throw new IllegalStateException("Không thể disable policy mặc định.");
        }

        policy.setEnabled(enabled);
        policy.setUpdatedBy(updatedBy);

        SlaPolicy saved = slaPolicyRepository.save(policy);
        
        // Refresh cache
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            refreshDefaultPolicyCache();
        }

        return saved;
    }

    /**
     * Set một policy làm default cho priority của nó.
     */
    @CacheEvict(value = "defaultPolicies", allEntries = true)
    public SlaPolicy setAsDefault(Long id, String updatedBy) {
        SlaPolicy policy = getPolicyByIdOrThrow(id);

        if (!Boolean.TRUE.equals(policy.getEnabled())) {
            throw new IllegalStateException("Không thể set disabled policy làm default.");
        }

        // Unset other defaults for this priority
        unsetOtherDefaultsForPriority(policy.getPriority(), id);

        // Set this as default
        policy.setIsDefault(true);
        policy.setUpdatedBy(updatedBy);

        SlaPolicy saved = slaPolicyRepository.save(policy);
        
        // Refresh cache
        refreshDefaultPolicyCache();

        return saved;
    }

    // ==================== SLA Policy Lookup ====================

    /**
     * Lấy default SLA policy cho một priority.
     * Kết quả được cache để improve performance.
     */
    @Transactional(readOnly = true)
    @Cacheable("defaultPolicies")
    public Optional<SlaPolicy> getDefaultPolicyForPriority(TicketPriority priority) {
        log.debug("Fetching default SLA policy for priority: {}", priority);
        return slaPolicyRepository.findByPriorityAndIsDefaultTrueAndEnabledTrue(priority);
    }

    /**
     * Lấy default SLA policy cho priority, fallback sang hardcoded values nếu không có.
     */
    @Transactional(readOnly = true)
    public SlaPolicy getEffectivePolicyForPriority(TicketPriority priority) {
        return getDefaultPolicyForPriority(priority)
                .orElseGet(() -> createFallbackPolicy(priority));
    }

    /**
     * Tính SLA deadline cho một ticket dựa trên priority.
     * Trả về SLA deadline info.
     */
    @Transactional(readOnly = true)
    public SlaDeadline calculateSlaDeadlines(TicketPriority priority, java.time.LocalDateTime ticketCreatedAt) {
        SlaPolicy policy = getEffectivePolicyForPriority(priority);

        java.time.LocalDateTime responseDeadline;
        java.time.LocalDateTime resolutionDeadline;

        if (Boolean.TRUE.equals(policy.getBusinessHoursOnly())) {
            // Tính theo business hours
            responseDeadline = calculateBusinessHoursDeadline(
                    ticketCreatedAt, policy.getResponseMinutes());
            resolutionDeadline = calculateBusinessHoursDeadline(
                    ticketCreatedAt, policy.getResolutionMinutes());
        } else {
            // Tính theo calendar hours (24/7)
            responseDeadline = ticketCreatedAt.plusMinutes(policy.getResponseMinutes());
            resolutionDeadline = ticketCreatedAt.plusMinutes(policy.getResolutionMinutes());
        }

        return new SlaDeadline(
                policy.getId(),
                policy.getResponseMinutes(),
                policy.getResolutionMinutes(),
                policy.getWarningThreshold(),
                responseDeadline,
                resolutionDeadline
        );
    }

    /**
     * Refresh default policy cache.
     */
    public void refreshDefaultPolicyCache() {
        List<SlaPolicy> defaultPolicies = slaPolicyRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsDefault()) && Boolean.TRUE.equals(p.getEnabled()))
                .collect(Collectors.toList());

        defaultPolicyCache = defaultPolicies.stream()
                .collect(Collectors.toMap(SlaPolicy::getPriority, p -> p));

        log.info("Refreshed default SLA policy cache with {} policies", defaultPolicyCache.size());
    }

    // ==================== Helper Methods ====================

    private void validatePolicy(SlaPolicy policy) {
        if (policy.getName() == null || policy.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên policy không được trống");
        }

        if (policy.getPriority() == null) {
            throw new IllegalArgumentException("Priority không được trống");
        }

        if (policy.getResponseMinutes() == null || policy.getResponseMinutes() <= 0) {
            throw new IllegalArgumentException("Response time phải > 0");
        }

        if (policy.getResolutionMinutes() == null || policy.getResolutionMinutes() <= 0) {
            throw new IllegalArgumentException("Resolution time phải > 0");
        }

        if (policy.getResponseMinutes() > policy.getResolutionMinutes()) {
            throw new IllegalArgumentException("Response time không được lớn hơn Resolution time");
        }

        if (policy.getWarningThreshold() != null && 
            (policy.getWarningThreshold() < 0 || policy.getWarningThreshold() > 100)) {
            throw new IllegalArgumentException("Warning threshold phải từ 0 đến 100");
        }
    }

    private void unsetOtherDefaultsForPriority(TicketPriority priority, Long exceptPolicyId) {
        Optional<SlaPolicy> currentDefault = slaPolicyRepository.findByPriorityAndIsDefaultTrue(priority);
        if (currentDefault.isPresent() && !currentDefault.get().getId().equals(exceptPolicyId)) {
            currentDefault.get().setIsDefault(false);
            slaPolicyRepository.save(currentDefault.get());
        }
    }

    /**
     * Tạo fallback policy từ hardcoded values (để backward compatibility).
     */
    private SlaPolicy createFallbackPolicy(TicketPriority priority) {
        log.warn("No SLA policy found for priority {}, using fallback", priority);
        
        SlaPolicy fallback = new SlaPolicy();
        fallback.setId(null); // Not persisted
        fallback.setName("Fallback " + priority.name() + " SLA");
        fallback.setPriority(priority);
        
        // Use hardcoded values from TicketTypes.SLAPriority
        switch (priority) {
            case CRITICAL:
                fallback.setResponseMinutes(4 * 60);  // 4 hours (backward compatible)
                fallback.setResolutionMinutes(4 * 60); // 4 hours
                break;
            case HIGH:
                fallback.setResponseMinutes(2 * 60);  // 2 hours
                fallback.setResolutionMinutes(8 * 60); // 8 hours
                break;
            case MEDIUM:
                fallback.setResponseMinutes(4 * 60);  // 4 hours
                fallback.setResolutionMinutes(24 * 60); // 24 hours
                break;
            case LOW:
                fallback.setResponseMinutes(8 * 60);  // 8 hours
                fallback.setResolutionMinutes(72 * 60); // 72 hours
                break;
            case URGENT:
                fallback.setResponseMinutes(1 * 60);   // 1 hour
                fallback.setResolutionMinutes(6 * 60); // 6 hours
                break;
            default:
                fallback.setResponseMinutes(4 * 60);
                fallback.setResolutionMinutes(24 * 60);
        }
        
        fallback.setWarningThreshold(75);
        fallback.setEnabled(true);
        fallback.setIsDefault(false);
        
        return fallback;
    }

    /**
     * Tính deadline theo business hours (9:00 - 18:00, Mon-Fri).
     */
    private java.time.LocalDateTime calculateBusinessHoursDeadline(
            java.time.LocalDateTime start, int minutesToAdd) {
        
        java.time.LocalDateTime current = start;
        int remainingMinutes = minutesToAdd;

        while (remainingMinutes > 0) {
            java.time.DayOfWeek day = current.getDayOfWeek();
            int hour = current.getHour();

            // Skip weekends
            if (day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY) {
                current = current.with(java.time.temporal.ChronoField.DAY_OF_WEEK, 
                        day == java.time.DayOfWeek.SATURDAY ? 
                                java.time.DayOfWeek.MONDAY.getValue() : 
                                java.time.DayOfWeek.MONDAY.getValue())
                        .withHour(9).withMinute(0).withSecond(0);
                continue;
            }

            // If before 9 AM, move to 9 AM
            if (hour < 9) {
                current = current.withHour(9).withMinute(0).withSecond(0);
                continue;
            }

            // If after 6 PM, move to next day 9 AM
            if (hour >= 18) {
                current = current.plusDays(1)
                        .withHour(9).withMinute(0).withSecond(0);
                continue;
            }

            // Calculate remaining business minutes today
            int businessMinutesToday = (18 - hour) * 60 - current.getMinute();

            if (remainingMinutes <= businessMinutesToday) {
                current = current.plusMinutes(remainingMinutes);
                remainingMinutes = 0;
            } else {
                remainingMinutes -= businessMinutesToday;
                current = current.plusDays(1)
                        .withHour(9).withMinute(0).withSecond(0);
            }
        }

        return current;
    }

    // ==================== DTO Classes ====================

    /**
     * SLA Deadline DTO - chứa thông tin deadline cho một ticket.
     */
    public static class SlaDeadline {
        private final Long policyId;
        private final Integer responseMinutes;
        private final Integer resolutionMinutes;
        private final Integer warningThreshold;
        private final java.time.LocalDateTime responseDeadline;
        private final java.time.LocalDateTime resolutionDeadline;

        public SlaDeadline(Long policyId, Integer responseMinutes, Integer resolutionMinutes,
                           Integer warningThreshold,
                           java.time.LocalDateTime responseDeadline,
                           java.time.LocalDateTime resolutionDeadline) {
            this.policyId = policyId;
            this.responseMinutes = responseMinutes;
            this.resolutionMinutes = resolutionMinutes;
            this.warningThreshold = warningThreshold;
            this.responseDeadline = responseDeadline;
            this.resolutionDeadline = resolutionDeadline;
        }

        public Long getPolicyId() { return policyId; }
        public Integer getResponseMinutes() { return responseMinutes; }
        public Integer getResolutionMinutes() { return resolutionMinutes; }
        public Integer getWarningThreshold() { return warningThreshold; }
        public java.time.LocalDateTime getResponseDeadline() { return responseDeadline; }
        public java.time.LocalDateTime getResolutionDeadline() { return resolutionDeadline; }
    }
}
