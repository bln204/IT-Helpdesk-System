package com.example.ticketing.ai.query;

import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorTiers;
import com.example.ticketing.authorization.ChangeAuthorization;
import com.example.ticketing.change.ChangeRequest;
import com.example.ticketing.change.ChangeRequestRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class ChangeQueryAdapter extends JpaQueryAdapter<ChangeRequest> {

    /** Mirrors ChangeRequestController#getAllChanges. */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("change", "change",
            "yêu cầu thay đổi hệ thống (change request), có lịch thực hiện và mức rủi ro",
            List.of(
                    FieldDef.text("changeNumber", "changeNumber", "mã change, vd CHG-001"),
                    FieldDef.text("title", "title", "tiêu đề"),
                    FieldDef.enumeration("status", "status", ChangeRequest.ChangeStatus.class, "trạng thái"),
                    FieldDef.enumeration("changeType", "changeType", ChangeRequest.ChangeType.class, "loại change"),
                    FieldDef.enumeration("riskLevel", "riskLevel", ChangeRequest.RiskLevel.class, "mức rủi ro"),
                    FieldDef.enumeration("priority", "priority", ChangeRequest.Priority.class, "độ ưu tiên"),
                    FieldDef.text("requester", "requesterName", "người yêu cầu"),
                    FieldDef.text("assignedTo", "assignedTo", "người thực hiện"),
                    FieldDef.date("scheduledStartDate", "scheduledStartDate", "ngày bắt đầu dự kiến"),
                    FieldDef.date("createdAt", "createdAt", "ngày tạo")),
            "createdAt", true);

    private final ChangeRequestRepository repository;
    private final ChangeAuthorization authz;

    public ChangeQueryAdapter(ChangeRequestRepository repository, ChangeAuthorization authz) {
        this.repository = repository;
        this.authz = authz;
    }

    @Override
    protected Scope<ChangeRequest> scope(ActorContext actor) {
        return ActorTiers.seesAll(actor)
                ? Scope.all()
                : Scope.of(authz.readScope(actor), "Kết quả chỉ gồm change do bạn yêu cầu.");
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<ChangeRequest> repository() { return repository; }
    @Override protected String idPath() { return "changeNumber"; }

    @Override
    protected Row toRow(ChangeRequest c) {
        return new Row(c.getChangeNumber(), c.getTitle(), fields(
                "trạng thái", c.getStatus(),
                "loại", c.getChangeType(),
                "rủi ro", c.getRiskLevel(),
                "người yêu cầu", c.getRequesterName(),
                "người thực hiện", c.getAssignedTo(),
                "bắt đầu dự kiến", c.getScheduledStartDate()),
                c.getDescription());
    }
}
