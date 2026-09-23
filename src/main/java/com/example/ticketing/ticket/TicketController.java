package com.example.ticketing.ticket;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tickets")
@Validated
public class TicketController {
    private final TicketService ticketService;
    private final TicketAttachmentService attachmentService;
    private final UserAccountRepository userAccountRepository;

    public TicketController(
            TicketService ticketService,
            TicketAttachmentService attachmentService,
            UserAccountRepository userAccountRepository) {
        this.ticketService = ticketService;
        this.attachmentService = attachmentService;
        this.userAccountRepository = userAccountRepository;
    }

    @PostMapping
    public ResponseEntity<TicketDtos.TicketResponse> createTicket(
        @Valid @RequestBody TicketDtos.TicketCreateRequest request,
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);
        UserRole.Role actorRole = user.getRole();

        Ticket ticket = new Ticket();
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(request.getPriority());
        ticket.setCategory(request.getCategory());
        ticket.setRequesterName(request.getRequesterName());
        ticket.setRequesterEmail(request.getRequesterEmail());
        ticket.setAssigneeName(request.getAssigneeName());

        Ticket created = ticketService.createTicket(ticket, authentication.getName(), actorRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketDtos.TicketResponse.from(created));
    }

    @GetMapping
    public Page<TicketDtos.TicketResponse> listTickets(
        @RequestParam(required = false) String assignee,
        @RequestParam(required = false) TicketTypes.TicketStatus status,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "false") boolean excludeClosed,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);

        String[] parts = sort.split(",", 2);
        String sortField = parts[0];
        String sortDirection = parts.length > 1 ? parts[1] : "asc";
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDirection).orElse(Sort.Direction.ASC);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortField));

        return ticketService.listTickets(
            assignee,
            status,
            search,
            excludeClosed,
            pageRequest,
            user.getRole().name(),
            user.getDepartmentId()
        ).map(TicketDtos.TicketResponse::from);
    }

    @GetMapping("/{id}")
    public TicketDtos.TicketResponse getTicket(@PathVariable Long id) {
        return TicketDtos.TicketResponse.from(ticketService.getTicket(id));
    }

    @GetMapping("/queue")
    public List<TicketDtos.TicketResponse> unassignedQueue(Authentication authentication) {
        requireStaff(authentication);
        return ticketService.listUnassignedQueue().stream()
            .map(TicketDtos.TicketResponse::from)
            .toList();
    }

    @PostMapping("/{id}/assign/me")
    public TicketDtos.TicketResponse assignToMe(
        @PathVariable Long id,
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);
        requireAssignPermission(user);

        Ticket updated = ticketService.assignTicket(
            id,
            authentication.getName(),
            user.getRole(),
            user.getDepartmentId(),
            authentication.getName()
        );
        return TicketDtos.TicketResponse.from(updated);
    }

    @PatchMapping("/{id}/status")
    public TicketDtos.TicketResponse updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody TicketDtos.TicketStatusUpdateRequest request,
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);
        Ticket updated = ticketService.updateStatus(
            id,
            request.getStatus(),
            user.getRole(),
            user.getDepartmentId(),
            authentication.getName(),
            authentication.getName()
        );
        return TicketDtos.TicketResponse.from(updated);
    }

    @PatchMapping("/{id}/priority")
    public TicketDtos.TicketResponse updatePriority(
        @PathVariable Long id,
        @Valid @RequestBody TicketDtos.TicketPriorityUpdateRequest request,
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);
        Ticket updated = ticketService.updatePriority(
            id,
            request.getPriority(),
            user.getRole(),
            user.getDepartmentId(),
            authentication.getName()
        );
        return TicketDtos.TicketResponse.from(updated);
    }

    @PatchMapping("/{id}/assignee")
    public TicketDtos.TicketResponse updateAssignee(
        @PathVariable Long id,
        @Valid @RequestBody TicketDtos.TicketAssigneeUpdateRequest request,
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);
        requireAssignPermission(user);

        Ticket updated = ticketService.assignTicket(
            id,
            request.getAssigneeName(),
            user.getRole(),
            user.getDepartmentId(),
            authentication.getName()
        );
        return TicketDtos.TicketResponse.from(updated);
    }

    @GetMapping("/{id}/assignments")
    public List<TicketDtos.TicketAssignmentResponse> listAssignments(@PathVariable Long id) {
        return ticketService.listAssignments(id).stream()
            .map(TicketDtos.TicketAssignmentResponse::from)
            .toList();
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<TicketDtos.TicketCommentResponse> addComment(
        @PathVariable Long id,
        @Valid @RequestBody TicketDtos.TicketCommentCreateRequest request,
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);
        TicketComment comment = ticketService.addComment(
            id,
            request.getVisibility(),
            request.getBody(),
            user.getRole(),
            authentication.getName()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(TicketDtos.TicketCommentResponse.from(comment));
    }

    @GetMapping("/{id}/comments")
    public List<TicketDtos.TicketCommentResponse> listComments(
        @PathVariable Long id,
        @RequestParam(required = false) TicketTypes.CommentVisibility visibility,
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);
        return ticketService.listComments(id, user.getRole(), visibility).stream()
            .map(TicketDtos.TicketCommentResponse::from)
            .toList();
    }

    // ==================== Attachment Endpoints ====================

    @PostMapping("/{id}/attachments")
    public ResponseEntity<TicketDtos.TicketAttachmentResponse> uploadAttachment(
        @PathVariable Long id,
        @RequestPart("file") MultipartFile file,
        Authentication authentication
    ) {
        // Verify ticket exists
        ticketService.getTicket(id);
        UserAccount user = getCurrentUser(authentication);
        
        TicketAttachment attachment = attachmentService.uploadAttachment(
            id,
            file,
            authentication.getName()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(TicketDtos.TicketAttachmentResponse.from(attachment));
    }

    @GetMapping("/{id}/attachments")
    public List<TicketDtos.TicketAttachmentResponse> listAttachments(
        @PathVariable Long id,
        Authentication authentication
    ) {
        // Verify ticket exists
        ticketService.getTicket(id);
        return attachmentService.getAttachmentsByTicketId(id).stream()
            .map(TicketDtos.TicketAttachmentResponse::from)
            .toList();
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
        @PathVariable Long attachmentId,
        Authentication authentication
    ) {
        TicketAttachment attachment = attachmentService.getAttachment(attachmentId);
        Resource resource = attachmentService.downloadAttachment(attachmentId);
        
        String contentDisposition = "attachment; filename=\"" + attachment.getOriginalName() + "\"";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(attachment.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .body(resource);
    }

    @GetMapping("/{id}/audit")
    public List<TicketDtos.TicketAuditResponse> listAudit(@PathVariable Long id) {
        return ticketService.listAudit(id).stream()
            .map(TicketDtos.TicketAuditResponse::from)
            .toList();
    }

    @GetMapping("/{id}/audit/export")
    public ResponseEntity<String> exportAudit(@PathVariable Long id) {
        List<TicketAudit> entries = ticketService.listAudit(id);
        StringBuilder csv = new StringBuilder();
        csv.append("created_at,action,field,old_value,new_value,actor_role,actor_name\n");
        for (TicketAudit entry : entries) {
            csv.append(escapeCsv(entry.getCreatedAt()))
                .append(',')
                .append(escapeCsv(entry.getAction()))
                .append(',')
                .append(escapeCsv(entry.getFieldName()))
                .append(',')
                .append(escapeCsv(entry.getOldValue()))
                .append(',')
                .append(escapeCsv(entry.getNewValue()))
                .append(',')
                .append(escapeCsv(entry.getActorRole()))
                .append(',')
                .append(escapeCsv(entry.getActorName()))
                .append('\n');
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ticket-" + id + "-audit.csv");
        return new ResponseEntity<>(csv.toString(), headers, HttpStatus.OK);
    }

    @GetMapping("/reports/status-counts")
    public List<TicketDtos.TicketStatusCountResponse> statusCounts(
        Authentication authentication,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to
    ) {
        requireStaff(authentication);
        Map<TicketTypes.TicketStatus, Long> counts = new HashMap<>();
        for (Ticket ticket : ticketService.listTickets(null, null, null, false, Pageable.unpaged(), "ADMIN", null)) {
            if (!withinRange(ticket.getCreatedAt(), from, to)) {
                continue;
            }
            counts.merge(ticket.getStatus(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .map(entry -> new TicketDtos.TicketStatusCountResponse(entry.getKey(), entry.getValue()))
            .toList();
    }

    @GetMapping("/reports/ticket-counts")
    public TicketDtos.TicketCountReport ticketCounts(Authentication authentication) {
        requireStaff(authentication);
        return ticketService.getTicketCounts(null);
    }

    @GetMapping("/reports/dashboard")
    public TicketDtos.DashboardSummary dashboardSummary(
        Authentication authentication,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to
    ) {
        UserAccount user = getCurrentUser(authentication);
        String requesterUsername = user.getRole() == UserRole.Role.NHAN_VIEN 
            ? authentication.getName() : null;
        LocalDateTime[] range = resolveReportRange(from, to);
        return ticketService.buildDashboardSummary(requesterUsername, range[0], range[1]);
    }

    @GetMapping("/reports/assignee-workload")
    public List<TicketDtos.TicketAssigneeWorkloadResponse> assigneeWorkload(
        Authentication authentication,
        @RequestParam(required = false) TicketTypes.TicketStatus status,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to
    ) {
        requireStaff(authentication);
        Map<String, Long> counts = new HashMap<>();
        for (Ticket ticket : ticketService.listTickets(null, null, null, false, Pageable.unpaged(), "ADMIN", null)) {
            if (status != null && ticket.getStatus() != status) {
                continue;
            }
            if (!withinRange(ticket.getCreatedAt(), from, to)) {
                continue;
            }
            String assignee = ticket.getAssigneeName();
            if (assignee == null || assignee.isBlank()) {
                assignee = "Unassigned";
            }
            counts.merge(assignee, 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .map(entry -> new TicketDtos.TicketAssigneeWorkloadResponse(entry.getKey(), entry.getValue()))
            .toList();
    }

    @GetMapping("/reports/resolution-time")
    public TicketDtos.TicketResolutionTimeResponse resolutionTime(
        Authentication authentication,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to
    ) {
        requireStaff(authentication);
        long resolvedCount = 0;
        long totalSeconds = 0;
        for (Ticket ticket : ticketService.listTickets(null, null, null, false, Pageable.unpaged(), "ADMIN", null)) {
            if (ticket.getResolvedAt() != null && ticket.getCreatedAt() != null) {
                if (!withinRange(ticket.getResolvedAt(), from, to)) {
                    continue;
                }
                resolvedCount += 1;
                totalSeconds += Duration.between(ticket.getCreatedAt(), ticket.getResolvedAt()).getSeconds();
            }
        }
        double averageSeconds = resolvedCount == 0 ? 0 : (double) totalSeconds / resolvedCount;
        double averageMinutes = averageSeconds / 60.0;
        double averageHours = averageMinutes / 60.0;
        return new TicketDtos.TicketResolutionTimeResponse(
            resolvedCount,
            averageSeconds,
            averageMinutes,
            averageHours
        );
    }

    @GetMapping("/reports/engineer-summary")
    public List<TicketDtos.EngineerReportRow> engineerSummary(
        Authentication authentication,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to
    ) {
        requireStaff(authentication);
        LocalDateTime[] range = resolveReportRange(from, to);
        return ticketService.buildEngineerReport(range[0], range[1]);
    }

    @GetMapping("/reports/requester-summary")
    public List<TicketDtos.RequesterReportRow> requesterSummary(
        Authentication authentication,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to
    ) {
        requireStaff(authentication);
        LocalDateTime[] range = resolveReportRange(from, to);
        return ticketService.buildRequesterReport(range[0], range[1]);
    }

    @GetMapping("/reports/backlog-aging")
    public List<TicketDtos.BacklogAgingRow> backlogAging(
        Authentication authentication,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to
    ) {
        requireStaff(authentication);
        LocalDateTime[] range = resolveReportRange(from, to);
        return ticketService.buildBacklogAging(range[0], range[1]);
    }

    @GetMapping("/reports/sla-buckets")
    public List<TicketDtos.SlaBucketRow> slaBuckets(
        Authentication authentication,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to
    ) {
        requireStaff(authentication);
        LocalDateTime[] range = resolveReportRange(from, to);
        return ticketService.buildSlaBuckets(range[0], range[1]);
    }

    // ==================== Helper Methods ====================

    private UserAccount getCurrentUser(Authentication authentication) {
        return userAccountRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }

    private void requireStaff(Authentication authentication) {
        UserAccount user = getCurrentUser(authentication);
        if (user.getRole() == UserRole.Role.NHAN_VIEN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This action requires staff privileges.");
        }
    }

    private void requireAssignPermission(UserAccount user) {
        boolean canAssign = switch (user.getRole()) {
            case ADMIN, GIAM_DOC -> true;
            case TRUONG_PHONG -> {
                if (user.getDepartment() == null) {
                    yield false;
                }
                yield "IT".equals(user.getDepartment().getCode());
            }
            case NHAN_VIEN -> false;
        };

        if (!canAssign) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "You don't have permission to assign tickets. Only IT Manager can assign tickets.");
        }
    }

    private boolean withinRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (value == null) {
            return false;
        }
        if (from != null && value.isBefore(from)) {
            return false;
        }
        if (to != null && value.isAfter(to)) {
            return false;
        }
        return true;
    }

    private LocalDateTime[] resolveReportRange(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to != null ? to : LocalDateTime.now();
        LocalDateTime start = from != null ? from : end.minusDays(30);
        return new LocalDateTime[] { start, end };
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains("\"") || text.contains(",") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
