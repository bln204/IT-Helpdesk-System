package com.example.ticketing.ai.query;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.department.ItDepartmentResolver;
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class UserQueryAdapter extends JpaQueryAdapter<UserAccount> {

    /** UserAdminController#listUsers (ADMIN, GIAM_DOC, TRUONG_PHONG) + #listITStaff (all roles, IT staff only). */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("user", "người dùng",
            "người dùng / nhân viên / kỹ thuật viên: họ tên, vai trò, phòng ban",
            List.of(
                    FieldDef.text("username", "username", "tên đăng nhập, không dấu, vd nv_it"),
                    FieldDef.text("displayName", "displayName", "họ tên người, vd Liam Chen"),
                    FieldDef.enumeration("role", "role", UserRole.Role.class, "vai trò"),
                    FieldDef.text("department", "department.name", "phòng ban"),
                    FieldDef.text("title", "title", "chức danh")),
            "username", false);

    private final UserAccountRepository repository;
    private final ItDepartmentResolver itDepartmentResolver;
    private final com.example.ticketing.department.DepartmentRepository departments;

    public UserQueryAdapter(UserAccountRepository repository, ItDepartmentResolver itDepartmentResolver,
                            com.example.ticketing.department.DepartmentRepository departments) {
        this.repository = repository;
        this.itDepartmentResolver = itDepartmentResolver;
        this.departments = departments;
    }

    @Override
    protected java.util.Map<String, java.util.function.Supplier<java.util.Collection<Term>>> vocabularies() {
        return java.util.Map.of("department", () -> departments.findAll().stream()
                .map(x -> new Term(x.getName(), x.getCode())).toList());
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<UserAccount> repository() { return repository; }
    @Override protected String idPath() { return "username"; }

    @Override
    protected Scope<UserAccount> scope(ActorContext actor) {
        if (actor.role() != Role.NHAN_VIEN) return Scope.all();
        String itCode = itDepartmentResolver.getItDepartmentCode();
        return Scope.of((root, q, cb) -> cb.and(
                cb.equal(root.join("department", JoinType.LEFT).get("code"), itCode),
                cb.isTrue(root.get("enabled"))),
                "Bạn chỉ xem được danh sách nhân viên IT (kỹ thuật viên).");
    }

    @Override
    protected Row toRow(UserAccount u) {
        return new Row(u.getUsername(), u.getDisplayName(), fields(
                "vai trò", u.getRole(),
                "phòng ban", u.getDepartment() == null ? null : u.getDepartment().getName(),
                "chức danh", u.getTitle(),
                "email", u.getEmail()),
                null);
    }
}
