package com.example.ticketing.escalation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.team.Team;
import com.example.ticketing.team.TeamRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketNotFoundException;
import com.example.ticketing.ticket.TicketTypes;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * Service cho Escalation Management.
 * Xử lý escalation tự động và thủ công.
 */
@Service
@Transactional
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final EscalationRuleRepository ruleRepository;
    private final EscalationHistoryRepository historyRepository;
    private final TicketRepository ticketRepository;
    private final UserAccountRepository userAccountRepository;
    private final TeamRepository teamRepository;

    public EscalationService(
            EscalationRuleRepository ruleRepository,
            EscalationHistoryRepository historyRepository,
            TicketRepository ticketRepository,
            UserAccountRepository userAccountRepository,
            TeamRepository teamRepository) {
        this.ruleRepository = ruleRepository;
        this.historyRepository = historyRepository;
        this.ticketRepository = ticketRepository;
        this.userAccountRepository = userAccountRepository;
        this.teamRepository = teamRepository;
    }

    // ==================== Rule Management ====================

    /**
     * Lấy tất cả escalation rules.
     */
    @Transactional(readOnly = true)
    public List<EscalationRule> getAllRules() {
        return ruleRepository.findAll();
    }

    /**
     * Lấy rules đang enabled.
     */
    @Transactional(readOnly = true)
    public List<EscalationRule> getEnabledRules() {
        return ruleRepository.findByEnabledTrueOrderByPriorityDesc();
    }

    /**
     * Tạo escalation rule mới.
     */
    public EscalationRule createRule(EscalationRule rule, String createdBy) {
        log.info("Creating escalation rule: {}", rule.getName());
        rule.setCreatedBy(createdBy);
        rule.setUpdatedBy(createdBy);
        return ruleRepository.save(rule);
    }

    /**
     * Cập nhật escalation rule.
     */
    public EscalationRule updateRule(Long id, EscalationRule updates, String updatedBy) {
        EscalationRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new EscalationRuleNotFoundException(id));

        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setTriggerType(updates.getTriggerType());
        existing.setMinPriority(updates.getMinPriority());
        existing.setMinutesThreshold(updates.getMinutesThreshold());
        existing.setEscalateToUserId(updates.getEscalateToUserId());
        existing.setEscalateToTeamId(updates.getEscalateToTeamId());
        existing.setEscalationLevel(updates.getEscalationLevel());
        existing.setActionType(updates.getActionType());
        existing.setNotificationMessage(updates.getNotificationMessage());
        existing.setStartTime(updates.getStartTime());
        existing.setEndTime(updates.getEndTime());
        existing.setDaysOfWeek(updates.getDaysOfWeek());
        existing.setMaxEscalations(updates.getMaxEscalations());
        existing.setEnabled(updates.getEnabled());
        existing.setPriority(updates.getPriority());
        existing.setUpdatedBy(updatedBy);

        return ruleRepository.save(existing);
    }

    /**
     * Xóa escalation rule.
     */
    public void deleteRule(Long id) {
        log.info("Deleting escalation rule: {}", id);
        ruleRepository.deleteById(id);
    }

    /**
     * Toggle enabled status.
     */
    public EscalationRule toggleRule(Long id, boolean enabled, String updatedBy) {
        EscalationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new EscalationRuleNotFoundException(id));
        rule.setEnabled(enabled);
        rule.setUpdatedBy(updatedBy);
        return ruleRepository.save(rule);
    }

    // ==================== Escalation Logic ====================

    /**
     * Check và thực hiện escalation cho một ticket.
     * Gọi bởi SlaSchedulerService.
     */
    public void checkAndEscalate(Ticket ticket) {
        log.debug("Checking escalation for ticket: {}", ticket.getTicketNumber());

        // Không escalate resolved/closed tickets
        if (ticket.getStatus() == TicketStatus.RESOLVED ||
            ticket.getStatus() == TicketStatus.CLOSED ||
            ticket.getStatus() == TicketStatus.CANCELLED) {
            return;
        }

        // Check các trigger types
        checkSlaResponseWarning(ticket);
        checkSlaResponseBreached(ticket);
        checkSlaResolutionWarning(ticket);
        checkSlaResolutionBreached(ticket);
    }

    /**
     * Check SLA Response Warning.
     */
    private void checkSlaResponseWarning(Ticket ticket) {
        if (ticket.getSlaResponseAt() == null) return;
        if (ticket.getFirstResponseAt() != null) return; // Đã response

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningTime = ticket.getSlaResponseAt();

        // Check nếu đã đến thời điểm warning (75% threshold)
        long totalMinutes = Duration.between(ticket.getCreatedAt(), ticket.getSlaResponseAt()).toMinutes();
        long elapsedMinutes = Duration.between(ticket.getCreatedAt(), now).toMinutes();

        if (totalMinutes > 0 && elapsedMinutes >= totalMinutes * 0.75) {
            triggerEscalation(ticket, EscalationRule.EscalationTrigger.SLA_RESPONSE_WARNING,
                    "SLA phản hồi sắp hết hạn");
        }
    }

    /**
     * Check SLA Response Breached.
     */
    private void checkSlaResponseBreached(Ticket ticket) {
        if (ticket.getSlaResponseAt() == null) return;
        if (ticket.getFirstResponseAt() != null) return;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(ticket.getSlaResponseAt())) {
            triggerEscalation(ticket, EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED,
                    "SLA phản hồi đã bị vi phạm");
        }
    }

    /**
     * Check SLA Resolution Warning.
     */
    private void checkSlaResolutionWarning(Ticket ticket) {
        if (ticket.getSlaResolutionAt() == null) return;
        if (ticket.isResolutionSLABreached()) return;

        LocalDateTime now = LocalDateTime.now();
        long totalMinutes = Duration.between(ticket.getCreatedAt(), ticket.getSlaResolutionAt()).toMinutes();
        long elapsedMinutes = Duration.between(ticket.getCreatedAt(), now).toMinutes();

        if (totalMinutes > 0 && elapsedMinutes >= totalMinutes * 0.75) {
            triggerEscalation(ticket, EscalationRule.EscalationTrigger.SLA_RESOLUTION_WARNING,
                    "SLA giải quyết sắp hết hạn");
        }
    }

    /**
     * Check SLA Resolution Breached.
     */
    private void checkSlaResolutionBreached(Ticket ticket) {
        if (ticket.getSlaResolutionAt() == null) return;
        if (ticket.isResolutionSLABreached()) return;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(ticket.getSlaResolutionAt())) {
            triggerEscalation(ticket, EscalationRule.EscalationTrigger.SLA_RESOLUTION_BREACHED,
                    "SLA giải quyết đã bị vi phạm");
        }
    }

    /**
     * Trigger escalation cho ticket.
     */
    private void triggerEscalation(Ticket ticket, EscalationRule.EscalationTrigger triggerType, String reason) {
        // Tìm applicable rules
        List<EscalationRule> rules = ruleRepository.findApplicableRules(triggerType, ticket.getPriority());

        for (EscalationRule rule : rules) {
            if (!rule.isCurrentlyApplicable()) continue;
            if (!rule.isPriorityApplicable(ticket.getPriority())) continue;

            // Check nếu đã escalate quá max
            long currentEscalations = historyRepository.countByTicketId(ticket.getId());
            if (currentEscalations >= rule.getMaxEscalations()) {
                log.debug("Max escalations reached for ticket {} on rule {}", ticket.getTicketNumber(), rule.getName());
                continue;
            }

            // Check nếu rule đã được apply gần đây (tránh spam)
            if (hasRecentEscalation(ticket.getId(), rule.getId())) {
                continue;
            }

            // Execute escalation action
            executeEscalation(ticket, rule, reason);
            break; // Chỉ trigger rule đầu tiên phù hợp
        }
    }

    /**
     * Kiểm tra nếu có escalation gần đây.
     */
    private boolean hasRecentEscalation(Long ticketId, Long ruleId) {
        List<EscalationHistory> recent = historyRepository.findUnresolvedByTicketId(ticketId);
        return recent.stream()
                .anyMatch(h -> h.getRuleId() != null && h.getRuleId().equals(ruleId));
    }

    /**
     * Thực hiện escalation action.
     */
    private void executeEscalation(Ticket ticket, EscalationRule rule, String reason) {
        log.info("Executing escalation for ticket {} - Rule: {} - Reason: {}",
                ticket.getTicketNumber(), rule.getName(), reason);

        // Lưu history
        EscalationHistory history = EscalationHistory.builder()
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .rule(rule)
                .reason(reason)
                .previousAssignee(ticket.getAssigneeName())
                .previousStatus(ticket.getStatus().name())
                .build();

        // Execute action
        switch (rule.getActionType()) {
            case NOTIFY:
                history.setNotificationSent(true);
                // TODO: Send notification
                break;

            case REASSIGN:
                performReassignment(ticket, rule, history);
                break;

            case ESCALATE_STATUS:
                performStatusEscalation(ticket, rule, history);
                break;
        }

        historyRepository.save(history);
    }

    /**
     * Thực hiện reassignment.
     */
    private void performReassignment(Ticket ticket, EscalationRule rule, EscalationHistory history) {
        if (rule.getEscalateToUserId() != null) {
            userAccountRepository.findById(rule.getEscalateToUserId()).ifPresent(user -> {
                history.setNewAssignee(user.getUsername());
                ticket.setAssigneeName(user.getUsername());
                ticket.setAssignee(user);
            });
        } else if (rule.getEscalateToTeamId() != null) {
            teamRepository.findById(rule.getEscalateToTeamId()).ifPresent(team -> {
                history.setNewTeamId(team.getId());
                ticket.setTeam(team);
            });
        }

        ticketRepository.save(ticket);
    }

    /**
     * Thực hiện status escalation.
     */
    private void performStatusEscalation(Ticket ticket, EscalationRule rule, EscalationHistory history) {
        ticket.setStatus(TicketStatus.ESCALATED);
        ticket.incrementEscalation();
        history.setNewStatus(TicketStatus.ESCALATED.name());
        ticketRepository.save(ticket);
    }

    /**
     * Manual escalation cho ticket.
     */
    public EscalationHistory manualEscalate(Long ticketId, String reason, String escalatedBy) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        EscalationHistory history = EscalationHistory.builder()
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .reason("Manual escalation: " + reason)
                .previousAssignee(ticket.getAssigneeName())
                .previousStatus(ticket.getStatus().name())
                .createdBy(escalatedBy)
                .build();

        // Change status to ESCALATED
        ticket.setStatus(TicketStatus.ESCALATED);
        ticket.incrementEscalation();
        ticketRepository.save(ticket);

        history.setNewStatus(TicketStatus.ESCALATED.name());
        history.setActionTaken("MANUAL_ESCALATION");

        return historyRepository.save(history);
    }

    /**
     * Lấy lịch sử escalation của ticket.
     */
    @Transactional(readOnly = true)
    public List<EscalationHistory> getTicketEscalationHistory(Long ticketId) {
        return historyRepository.findByTicketIdOrderByEscalatedAtDesc(ticketId);
    }

    /**
     * Resolve escalation (khi ticket được resolved).
     */
    public void resolveEscalation(Long ticketId) {
        List<EscalationHistory> unresolved = historyRepository.findUnresolvedByTicketId(ticketId);
        LocalDateTime now = LocalDateTime.now();
        for (EscalationHistory history : unresolved) {
            history.setResolvedAt(now);
        }
        historyRepository.saveAll(unresolved);
    }

    // ==================== Exception Classes ====================

    public static class EscalationRuleNotFoundException extends RuntimeException {
        public EscalationRuleNotFoundException(Long id) {
            super("Không tìm thấy Escalation Rule với ID: " + id);
        }
    }
}
