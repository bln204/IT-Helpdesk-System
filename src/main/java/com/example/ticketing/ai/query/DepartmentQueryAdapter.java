package com.example.ticketing.ai.query;

import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class DepartmentQueryAdapter extends JpaQueryAdapter<Department> {

    /** Mirrors DepartmentController#getActiveDepartments; disabled ones need /all (ADMIN, GIAM_DOC). */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("department", "phòng ban", "phòng ban trong công ty và trưởng phòng",
            List.of(
                    FieldDef.text("code", "code", "mã phòng ban"),
                    FieldDef.text("name", "name", "tên phòng ban"),
                    FieldDef.text("manager", "manager.displayName", "trưởng phòng")),
            "name", false);

    private final DepartmentRepository repository;

    public DepartmentQueryAdapter(DepartmentRepository repository) {
        this.repository = repository;
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<Department> repository() { return repository; }
    @Override protected String idPath() { return "code"; }

    @Override
    protected java.util.Map<String, java.util.function.Supplier<java.util.Collection<Term>>> vocabularies() {
        return java.util.Map.of("name", () -> repository.findAll().stream().map(x -> new Term(x.getName(), x.getCode())).toList());
    }

    @Override
    protected Scope<Department> scope(ActorContext actor) {
        if (actor.role() == Role.ADMIN || actor.role() == Role.GIAM_DOC) return Scope.all();
        return Scope.of((root, q, cb) -> cb.isTrue(root.get("enabled")), null);
    }

    @Override
    protected Row toRow(Department d) {
        return new Row(d.getCode(), d.getName(), fields(
                "trưởng phòng", d.getManager() == null ? null : d.getManager().getDisplayName(),
                "trạng thái", d.isEnabled() ? "đang hoạt động" : "đã tắt"),
                d.getDescription());
    }
}
