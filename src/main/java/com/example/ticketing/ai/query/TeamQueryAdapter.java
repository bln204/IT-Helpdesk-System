package com.example.ticketing.ai.query;

import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.team.Team;
import com.example.ticketing.team.TeamRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class TeamQueryAdapter extends JpaQueryAdapter<Team> {

    /** TeamController#listTeams: no @PreAuthorize, requireAdminOrManager() rejects NHAN_VIEN in code. */
    static final AccessRule ACCESS = AccessRule.of(Role.ADMIN, Role.GIAM_DOC, Role.TRUONG_PHONG);

    static final DomainDef DEF = new DomainDef("team", "team", "nhóm xử lý ticket (vd Helpdesk L1) thuộc phòng ban",
            List.of(
                    FieldDef.text("code", "code", "mã team"),
                    FieldDef.text("name", "name", "tên team"),
                    FieldDef.text("department", "department.name", "phòng ban"),
                    FieldDef.text("lead", "lead.displayName", "trưởng nhóm")),
            "name", false);

    private final TeamRepository repository;
    private final com.example.ticketing.department.DepartmentRepository departments;

    public TeamQueryAdapter(TeamRepository repository, com.example.ticketing.department.DepartmentRepository departments) {
        this.repository = repository;
        this.departments = departments;
    }

    @Override
    protected java.util.Map<String, java.util.function.Supplier<java.util.Collection<Term>>> vocabularies() {
        return java.util.Map.of("department", () -> departments.findAll().stream()
                .map(x -> new Term(x.getName(), x.getCode())).toList());
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<Team> repository() { return repository; }
    @Override protected String idPath() { return "code"; }

    @Override
    protected Row toRow(Team t) {
        return new Row(t.getCode(), t.getName(), fields(
                "phòng ban", t.getDepartment() == null ? null : t.getDepartment().getName(),
                "trưởng nhóm", t.getLead() == null ? null : t.getLead().getDisplayName(),
                "trạng thái", t.isEnabled() ? "đang hoạt động" : "đã tắt"),
                t.getDescription());
    }
}
