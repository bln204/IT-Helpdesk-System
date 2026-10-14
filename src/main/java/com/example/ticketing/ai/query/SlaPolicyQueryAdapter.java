package com.example.ticketing.ai.query;

import com.example.ticketing.sla.SlaPolicy;
import com.example.ticketing.sla.SlaPolicyRepository;
import com.example.ticketing.ticket.TicketTypes;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class SlaPolicyQueryAdapter extends JpaQueryAdapter<SlaPolicy> {

    /** Mirrors SlaPolicyController#getAllPolicies. */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("sla_policy", "chính sách SLA",
            "chính sách SLA: thời gian phản hồi và xử lý theo độ ưu tiên ticket",
            List.of(
                    FieldDef.text("name", "name", "tên chính sách"),
                    FieldDef.enumeration("priority", "priority", TicketTypes.TicketPriority.class, "độ ưu tiên ticket: khẩn cấp = URGENT, nghiêm trọng = CRITICAL"),
                    FieldDef.bool("enabled", "enabled", "đang áp dụng"),
                    FieldDef.bool("businessHoursOnly", "businessHoursOnly", "chỉ tính giờ hành chính")),
            "name", false);

    private final SlaPolicyRepository repository;

    public SlaPolicyQueryAdapter(SlaPolicyRepository repository) {
        this.repository = repository;
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<SlaPolicy> repository() { return repository; }
    @Override protected String idPath() { return "name"; }

    @Override
    protected Row toRow(SlaPolicy p) {
        return new Row(p.getName(), "ưu tiên " + fmt(p.getPriority()), fields(
                "phản hồi (phút)", p.getResponseMinutes(),
                "xử lý (phút)", p.getResolutionMinutes(),
                "chỉ giờ hành chính", yesNo(p.getBusinessHoursOnly()),
                "đang áp dụng", yesNo(p.getEnabled()),
                "mặc định", yesNo(p.getIsDefault())),
                p.getDescription());
    }
}
