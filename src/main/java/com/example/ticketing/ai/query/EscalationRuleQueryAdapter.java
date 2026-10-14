package com.example.ticketing.ai.query;

import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.escalation.EscalationRule;
import com.example.ticketing.escalation.EscalationRuleRepository;
import com.example.ticketing.ticket.TicketTypes;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class EscalationRuleQueryAdapter extends JpaQueryAdapter<EscalationRule> {

    /** Mirrors EscalationController#getAllRules: ADMIN and NHAN_VIEN only (as the UI does today). */
    static final AccessRule ACCESS = AccessRule.of(Role.ADMIN, Role.NHAN_VIEN);

    static final DomainDef DEF = new DomainDef("escalation_rule", "quy tắc escalation",
            "quy tắc tự động escalate ticket khi sắp hoặc đã vi phạm SLA",
            List.of(
                    FieldDef.text("name", "name", "tên quy tắc"),
                    FieldDef.enumeration("triggerType", "triggerType", EscalationRule.EscalationTrigger.class, "điều kiện kích hoạt"),
                    FieldDef.enumeration("escalationLevel", "escalationLevel", EscalationRule.EscalationLevel.class, "mức escalation"),
                    FieldDef.enumeration("minPriority", "minPriority", TicketTypes.TicketPriority.class, "độ ưu tiên tối thiểu"),
                    FieldDef.bool("enabled", "enabled", "đang bật")),
            "name", false);

    private final EscalationRuleRepository repository;

    public EscalationRuleQueryAdapter(EscalationRuleRepository repository) {
        this.repository = repository;
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<EscalationRule> repository() { return repository; }
    @Override protected String idPath() { return "name"; }

    @Override
    protected Row toRow(EscalationRule r) {
        return new Row(r.getName(), fmt(r.getTriggerType()), fields(
                "mức", r.getEscalationLevel(),
                "ưu tiên tối thiểu", r.getMinPriority(),
                "ngưỡng (phút)", r.getMinutesThreshold(),
                "đang bật", yesNo(r.getEnabled())),
                r.getDescription());
    }
}
