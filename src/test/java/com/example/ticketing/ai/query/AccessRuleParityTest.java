package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.asset.AssetController;
import com.example.ticketing.incident.IncidentController;
import com.example.ticketing.servicerequest.ServiceRequestController;
import com.example.ticketing.sla.SlaPolicyController;
import com.example.ticketing.escalation.EscalationController;
import com.example.ticketing.category.CategoryController;
import com.example.ticketing.auth.UserAdminController;
import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.change.ChangeRequestController;
import com.example.ticketing.department.DepartmentController;
import com.example.ticketing.problem.ProblemController;
import com.example.ticketing.team.TeamController;
import com.example.ticketing.ticket.TicketController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** If a controller's read permission changes, the AI must change with it. */
class AccessRuleParityTest {

    private static final Pattern ROLE = Pattern.compile("(ADMIN|GIAM_DOC|TRUONG_PHONG|NHAN_VIEN)");

    private static Method method(Class<?> controller, String name) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(controller.getSimpleName() + "#" + name + " không tồn tại"));
    }

    private static Set<Role> rolesOf(Class<?> controller, String name) {
        PreAuthorize pa = method(controller, name).getAnnotation(PreAuthorize.class);
        assertThat(pa).as(controller.getSimpleName() + "#" + name + " phải có @PreAuthorize").isNotNull();
        Set<Role> roles = EnumSet.noneOf(Role.class);
        Matcher m = ROLE.matcher(pa.value());
        while (m.find()) roles.add(Role.valueOf(m.group(1)));
        return roles;
    }

    @Test
    void assetMatchesController() {
        assertThat(AssetQueryAdapter.ACCESS.roles()).isEqualTo(rolesOf(AssetController.class, "getAllAssets"));
    }

    @Test
    void changeMatchesController() {
        assertThat(ChangeQueryAdapter.ACCESS.roles()).isEqualTo(rolesOf(ChangeRequestController.class, "getAllChanges"));
    }

    @Test
    void problemAndKnownErrorMatchController() {
        assertThat(ProblemQueryAdapter.ACCESS.roles()).isEqualTo(rolesOf(ProblemController.class, "getAllProblems"));
        assertThat(KnownErrorQueryAdapter.ACCESS.roles()).isEqualTo(rolesOf(ProblemController.class, "getKnownErrors"));
    }

    @Test
    void departmentMatchesController() {
        assertThat(DepartmentQueryAdapter.ACCESS.roles())
                .isEqualTo(rolesOf(DepartmentController.class, "getActiveDepartments"));
    }

    @Test
    void userIsUnionOfListUsersAndEngineers() {
        Set<Role> union = EnumSet.noneOf(Role.class);
        union.addAll(rolesOf(UserAdminController.class, "listUsers"));
        union.addAll(rolesOf(UserAdminController.class, "listITStaff"));
        assertThat(UserQueryAdapter.ACCESS.roles()).isEqualTo(union);
    }

    @Test
    void teamIsCheckedInCode() {
        // TeamController#listTeams -> requireAdminOrManager(): NHAN_VIEN gets 403. If someone adds
        // @PreAuthorize here, switch this test to rolesOf(...).
        assertThat(method(TeamController.class, "listTeams").getAnnotation(PreAuthorize.class)).isNull();
        assertThat(TeamQueryAdapter.ACCESS.roles()).isEqualTo(EnumSet.of(Role.ADMIN, Role.GIAM_DOC, Role.TRUONG_PHONG));
    }

    @Test
    void ticketIsAuthenticatedAndRowScoped() {
        assertThat(method(TicketController.class, "listTickets").getAnnotation(PreAuthorize.class)).isNull();
        assertThat(TicketQueryAdapter.ACCESS.roles()).isEqualTo(EnumSet.allOf(Role.class));
    }

    @Test
    void newDomainsMatchControllers() {
        assertThat(IncidentQueryAdapter.ACCESS.roles()).isEqualTo(rolesOf(IncidentController.class, "getAllIncidents"));
        assertThat(ServiceRequestQueryAdapter.ACCESS.roles()).isEqualTo(rolesOf(ServiceRequestController.class, "getAllRequests"));
        assertThat(SlaPolicyQueryAdapter.ACCESS.roles()).isEqualTo(rolesOf(SlaPolicyController.class, "getAllPolicies"));
        assertThat(EscalationRuleQueryAdapter.ACCESS.roles()).isEqualTo(rolesOf(EscalationController.class, "getAllRules"));
    }

    @Test
    void unannotatedNewDomainsAreOpenToAuthenticatedUsers() {
        // No @PreAuthorize: SecurityConfig requires authentication only. If one gets an annotation, compare instead.
        assertThat(method(ServiceRequestController.class, "getServiceCatalog").getAnnotation(PreAuthorize.class)).isNull();
        assertThat(method(CategoryController.class, "listCategories").getAnnotation(PreAuthorize.class)).isNull();
        assertThat(method(EscalationController.class, "getTicketHistory").getAnnotation(PreAuthorize.class)).isNull();
        assertThat(ServiceCatalogQueryAdapter.ACCESS.roles()).isEqualTo(EnumSet.allOf(Role.class));
        assertThat(CategoryQueryAdapter.ACCESS.roles()).isEqualTo(EnumSet.allOf(Role.class));
        assertThat(EscalationHistoryQueryAdapter.ACCESS.roles()).isEqualTo(EnumSet.allOf(Role.class));
    }
}
