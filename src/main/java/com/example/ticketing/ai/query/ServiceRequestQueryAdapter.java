package com.example.ticketing.ai.query;

import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorTiers;
import com.example.ticketing.authorization.ServiceRequestAuthorization;
import com.example.ticketing.servicerequest.ServiceRequest;
import com.example.ticketing.servicerequest.ServiceRequestRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class ServiceRequestQueryAdapter extends JpaQueryAdapter<ServiceRequest> {

    /** Mirrors ServiceRequestController#getAllRequests: every role sees every request. */
    static final AccessRule ACCESS = AccessRule.allRoles();

    private static final List<String> OPEN = List.of("SUBMITTED", "PENDING_APPROVAL", "APPROVED", "IN_PROGRESS");
    private static final List<String> DONE = List.of("COMPLETED", "CLOSED", "REJECTED", "CANCELLED");

    static final DomainDef DEF = new DomainDef("service_request", "yêu cầu dịch vụ",
            "yêu cầu dịch vụ IT (cấp quyền, cài phần mềm, cấp thiết bị...) theo danh mục dịch vụ; chỉ khi người dùng nói yêu cầu dịch vụ/service request, \"phiếu\" là ticket",
            List.of(
                    FieldDef.text("requestNumber", "requestNumber", "mã yêu cầu, vd SR-1A2B3C4D"),
                    FieldDef.text("title", "title", "tiêu đề"),
                    FieldDef.text("serviceName", "serviceName", "tên dịch vụ"),
                    FieldDef.enumeration("status", "status", ServiceRequest.ServiceStatus.class, "trạng thái")
                            .withGroups(Map.of("OPEN", OPEN, "DANG_MO", OPEN, "DONE", DONE)),
                    FieldDef.enumeration("priority", "priority", ServiceRequest.Priority.class, "độ ưu tiên"),
                    FieldDef.custom("requester", "người yêu cầu",
                            EnumSet.of(FilterOp.EQ, FilterOp.IN, FilterOp.CONTAINS), ServiceRequestQueryAdapter::requester),
                    FieldDef.text("department", "requesterDepartment", "phòng ban người yêu cầu"),
                    FieldDef.date("createdAt", "createdAt", "ngày tạo")),
            "createdAt", true);

    private final ServiceRequestRepository repository;
    private final ServiceRequestAuthorization authz;

    public ServiceRequestQueryAdapter(ServiceRequestRepository repository, ServiceRequestAuthorization authz) {
        this.repository = repository;
        this.authz = authz;
    }

    @Override
    protected Scope<ServiceRequest> scope(ActorContext actor) {
        return ActorTiers.seesAll(actor)
                ? Scope.all()
                : Scope.of(authz.readScope(actor), "Kết quả chỉ gồm yêu cầu dịch vụ của bạn hoặc phòng ban của bạn.");
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<ServiceRequest> repository() { return repository; }
    @Override protected String idPath() { return "requestNumber"; }

    private static Predicate requester(Root<?> root, CriteriaQuery<?> q, CriteriaBuilder cb, ResolvedFilter f) {
        return cb.or(SpecificationFactory.anyContains(cb, root.get("requesterName"), f.values()),
                SpecificationFactory.anyContains(cb, root.get("requesterUsername"), f.values()));
    }

    @Override
    protected Row toRow(ServiceRequest r) {
        // formData and costs are deliberately left out: not needed to answer, and kept out of the LLM prompt.
        return new Row(r.getRequestNumber(), r.getTitle(), fields(
                "dịch vụ", r.getServiceName(),
                "trạng thái", r.getStatus(),
                "ưu tiên", r.getPriority(),
                "người yêu cầu", r.getRequesterName(),
                "phòng ban", r.getRequesterDepartment(),
                "ngày tạo", r.getCreatedAt()),
                r.getDescription());
    }
}
