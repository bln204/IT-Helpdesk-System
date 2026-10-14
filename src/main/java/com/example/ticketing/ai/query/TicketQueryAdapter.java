package com.example.ticketing.ai.query;

import com.example.ticketing.ai.RagContextDto;
import com.example.ticketing.ai.RagRetrievalService;
import com.example.ticketing.asset.AssetTicketLink;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.RagAuthorizationDecision;
import com.example.ticketing.authorization.RagAuthorizationPolicy;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class TicketQueryAdapter extends JpaQueryAdapter<Ticket> {

        /**
         * TicketController#listTickets has no @PreAuthorize: every authenticated role,
         * rows scoped by policy.
         */
        static final AccessRule ACCESS = AccessRule.allRoles();

        private static final List<String> OPEN = List.of("NEW", "ASSIGNED", "IN_PROGRESS", "WAITING_FOR_USER",
                        "BLOCKED", "ESCALATED", "REOPENED");
        private static final List<String> DONE = List.of("RESOLVED", "CLOSED", "CANCELLED");

        static final DomainDef DEF = new DomainDef("ticket", "ticket", "phiếu yêu cầu hỗ trợ IT do người dùng gửi",
                        List.of(
                                        FieldDef.text("ticketNumber", "ticketNumber", "mã ticket, vd TCK-2026-0001"),
                                        FieldDef.text("title", "title", "tiêu đề"),
                                        FieldDef.enumeration("status", "status", TicketTypes.TicketStatus.class,
                                                        "trạng thái (WAITING_FOR_USER = chờ người gửi phản hồi)")
                                                        .withGroups(Map.of("OPEN", OPEN, "DANG_MO", OPEN, "CHUA_DONG",
                                                                        OPEN,
                                                                        "DONE", DONE, "DA_DONG", DONE)),
                                        FieldDef.enumeration("priority", "priority", TicketTypes.TicketPriority.class,
                                                        "độ ưu tiên"),
                                        FieldDef.enumeration("category", "category", TicketTypes.TicketCategory.class,
                                                        "loại sự cố"),
                                        FieldDef.custom("assignee",
                                                        "người xử lý (kỹ thuật viên được giao; is_null = chưa ai nhận / chưa phân công)",
                                                        EnumSet.of(FilterOp.EQ, FilterOp.IN, FilterOp.NOT_IN,
                                                                        FilterOp.CONTAINS,
                                                                        FilterOp.IS_NULL, FilterOp.NOT_NULL),
                                                        TicketQueryAdapter::assignee),
                                        FieldDef.custom("requester", "người gửi / tạo ticket",
                                                        EnumSet.of(FilterOp.EQ, FilterOp.IN, FilterOp.CONTAINS),
                                                        TicketQueryAdapter::requester),
                                        FieldDef.text("department", "department.name", "phòng ban của người gửi"),
                                        FieldDef.text("team", "team.name", "team xử lý"),
                                        FieldDef.custom("asset", "mã thiết bị liên quan, vd AST-001",
                                                        EnumSet.of(FilterOp.EQ, FilterOp.IN),
                                                        TicketQueryAdapter::asset),
                                        FieldDef.date("createdAt", "createdAt", "ngày tạo"),
                                        FieldDef.date("resolvedAt", "resolvedAt", "ngày giải quyết"),
                                        FieldDef.semantic("mô tả sự cố bằng lời tự do (tìm theo ý nghĩa)")),
                        "createdAt", true);

        private final TicketRepository repository;
        private final RagAuthorizationPolicy policy;
        private final DepartmentRepository departments;
        private final RagRetrievalService ragRetrievalService;
        private final com.example.ticketing.team.TeamRepository teams;
        private final double semanticMinScore;

        public TicketQueryAdapter(TicketRepository repository,
                        RagAuthorizationPolicy policy,
                        DepartmentRepository departments,
                        com.example.ticketing.team.TeamRepository teams,
                        RagRetrievalService ragRetrievalService,
                        @Value("${ai.query.semantic-min-score:0.65}") double semanticMinScore) {
                this.repository = repository;
                this.policy = policy;
                this.departments = departments;
                this.teams = teams;
                this.ragRetrievalService = ragRetrievalService;
                this.semanticMinScore = semanticMinScore;
        }

        @Override
        public DomainDef definition() {
                return DEF;
        }

        @Override
        public AccessRule accessRule() {
                return ACCESS;
        }

        @Override
        protected JpaSpecificationExecutor<Ticket> repository() {
                return repository;
        }

        @Override
        protected String idPath() {
                return "ticketNumber";
        }

        /** Row scope of the tickets this actor may read; reused by ticket-derived domains (escalation history). */
        public Scope<Ticket> ticketScope(ActorContext actor) {
                return scope(actor);
        }

        @Override
        protected Scope<Ticket> scope(ActorContext actor) {
                RagAuthorizationDecision decision = policy.decide(actor);
                return switch (decision.kind()) {
                        case ALLOW_ALL -> Scope.all();
                        // A valid actor with no department still sees the tickets they requested, as in
                        // TicketService.listTickets.
                        case DENY -> actor != null && actor.role() != null && actor.username() != null
                                        ? Scope.of(ownRequests(actor.username()), "Kết quả chỉ gồm ticket do bạn tạo.")
                                        : Scope.deny("Bạn không có quyền xem ticket.");
                        case DEPARTMENT_SCOPED -> departmentScope(decision.departmentCode(), actor.username());
                };
        }

        // Same rule as TicketAuthorization for non-IT actors: own department OR
        // requested by me.
        private Scope<Ticket> departmentScope(String departmentCode, String username) {
                Optional<Department> dept = departments.findByCode(departmentCode);
                String me = username.toLowerCase(Locale.ROOT);
                Specification<Ticket> spec = (root, q, cb) -> {
                        Predicate own = cb.equal(cb.lower(root.get("requesterUsername")), me);
                        return dept.map(d -> cb
                                        .or(cb.equal(root.join("department", JoinType.LEFT).get("id"), d.getId()), own))
                                        .orElse(own);
                };
                String deptName = dept.map(Department::getName).orElse(departmentCode);
                return Scope.of(spec, "Kết quả chỉ gồm ticket thuộc " + deptName + " và ticket do bạn tạo.");
        }

        private static Specification<Ticket> ownRequests(String username) {
                String me = username.toLowerCase(Locale.ROOT);
                return (root, q, cb) -> cb.equal(cb.lower(root.get("requesterUsername")), me);
        }

        @Override
        protected Map<String, Supplier<Collection<Term>>> vocabularies() {
                return Map.of(
                                "department",
                                () -> departments.findAll().stream().map(x -> new Term(x.getName(), x.getCode()))
                                                .toList(),
                                "team",
                                () -> teams.findAll().stream().map(x -> new Term(x.getName(), x.getCode())).toList());
        }

        @Override
        protected List<Long> semanticIds(String text, ActorContext actor) {
                return semanticHits(ragRetrievalService.retrieveContext(
                                new RagContextDto.RetrievalRequest(text, 20, semanticMinScore), actor.username()));
        }

        @Override
        protected Row toRow(Ticket t) {
                String assignee = t.getAssigneeName() == null || t.getAssigneeName().isBlank() ? "(chưa gán)"
                                : t.getAssigneeName();
                return new Row(t.getTicketNumber(), t.getTitle(), fields(
                                "trạng thái", t.getStatus(),
                                "ưu tiên", t.getPriority(),
                                "người xử lý", assignee,
                                "người gửi", t.getRequesterName(),
                                "phòng ban", t.getDepartment() == null ? null : t.getDepartment().getName(),
                                "ngày tạo", t.getCreatedAt()),
                                t.getDescription());
        }

        private static Predicate assignee(Root<?> root, CriteriaQuery<?> q, CriteriaBuilder cb, ResolvedFilter f) {
                Path<String> name = root.get("assigneeName");
                // Unassigned = no FK AND no legacy name; both are written by different code
                // paths.
                Predicate unassigned = cb.and(cb.isNull(root.get("assignee")),
                                cb.or(cb.isNull(name), cb.equal(name, "")));
                if (f.op() == FilterOp.IS_NULL)
                        return unassigned;
                if (f.op() == FilterOp.NOT_NULL)
                        return cb.not(unassigned);
                // assigneeName holds a username or a display name depending on who wrote it:
                // match all three.
                Join<?, ?> user = root.join("assignee", JoinType.LEFT);
                Predicate byName = cb.or(
                                SpecificationFactory.anyContains(cb, cb.coalesce(name, ""), f.values()),
                                SpecificationFactory.anyContains(cb, cb.coalesce(user.<String>get("username"), ""),
                                                f.values()),
                                SpecificationFactory.anyContains(cb, cb.coalesce(user.<String>get("displayName"), ""),
                                                f.values()));
                return f.op() == FilterOp.NOT_IN ? cb.not(byName) : byName;
        }

        private static Predicate requester(Root<?> root, CriteriaQuery<?> q, CriteriaBuilder cb, ResolvedFilter f) {
                return cb.or(SpecificationFactory.anyContains(cb, root.get("requesterName"), f.values()),
                                SpecificationFactory.anyContains(cb, root.get("requesterUsername"), f.values()));
        }

        private static Predicate asset(Root<?> root, CriteriaQuery<?> q, CriteriaBuilder cb, ResolvedFilter f) {
                List<String> codes = f.values().stream().map(v -> v.toString().trim().toUpperCase(Locale.ROOT))
                                .toList();
                Subquery<Long> sub = q.subquery(Long.class);
                Root<AssetTicketLink> link = sub.from(AssetTicketLink.class);
                sub.select(link.get("ticketId"))
                                .where(cb.upper(link.get("asset").<String>get("assetNumber")).in(codes));
                return root.get("id").in(sub);
        }
}
