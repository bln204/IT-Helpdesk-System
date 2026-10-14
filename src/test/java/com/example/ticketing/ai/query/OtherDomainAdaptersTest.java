package com.example.ticketing.ai.query;

import static com.example.ticketing.ai.query.QueryTestPlans.filter;
import static com.example.ticketing.ai.query.QueryTestPlans.list;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.change.ChangeRequest;
import com.example.ticketing.change.ChangeRequestRepository;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.problem.KnownError;
import com.example.ticketing.problem.KnownErrorRepository;
import com.example.ticketing.problem.Problem;
import com.example.ticketing.problem.ProblemRepository;
import com.example.ticketing.team.Team;
import com.example.ticketing.team.TeamRepository;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OtherDomainAdaptersTest {

    @MockitoBean private EmbeddingService embeddingService;

    @Autowired private TicketAuthorizationFixtures fixtures;
    @Autowired private ItDepartmentResolver itDepartmentResolver;
    @Autowired private ChangeRequestRepository changes;
    @Autowired private ProblemRepository problems;
    @Autowired private KnownErrorRepository knownErrors;
    @Autowired private DepartmentRepository departments;
    @Autowired private TeamRepository teams;
    @Autowired private ChangeQueryAdapter changeAdapter;
    @Autowired private ProblemQueryAdapter problemAdapter;
    @Autowired private KnownErrorQueryAdapter knownErrorAdapter;
    @Autowired private DepartmentQueryAdapter departmentAdapter;
    @Autowired private TeamQueryAdapter teamAdapter;
    @Autowired private UserQueryAdapter userAdapter;

    private ActorContext actor(UserAccount u) {
        return ActorContext.of(u, itDepartmentResolver.isITDepartment(u.getDepartment()));
    }

    @Test
    void changeFilterByStatus() {
        ChangeRequest c = new ChangeRequest();
        c.setChangeNumber("CHG-T01");
        c.setTitle("Nâng cấp firewall");
        c.setStatus(ChangeRequest.ChangeStatus.APPROVED);
        changes.save(c);
        ChangeRequest draft = new ChangeRequest();
        draft.setChangeNumber("CHG-T02");
        draft.setTitle("Đổi DNS");
        changes.save(draft);
        DomainDef d = ChangeQueryAdapter.DEF;
        QueryResult r = changeAdapter.execute(list(d,
                filter(d, "status", FilterOp.EQ, ChangeRequest.ChangeStatus.APPROVED)), actor(fixtures.nhanVienIT()));
        assertThat(r.rows()).extracting(Row::id).contains("CHG-T01").doesNotContain("CHG-T02");
    }

    @Test
    void businessUserSeesOnlyOwnChanges() {
        UserAccount me = fixtures.nhanVienMkt();
        ChangeRequest mine = new ChangeRequest();
        mine.setChangeNumber("CHG-T03");
        mine.setTitle("Cấp quyền share");
        mine.setRequesterUsername(me.getUsername());
        changes.save(mine);
        ChangeRequest other = new ChangeRequest();
        other.setChangeNumber("CHG-T04");
        other.setTitle("Đổi switch");
        other.setRequesterUsername("someone_else");
        changes.save(other);
        DomainDef d = ChangeQueryAdapter.DEF;
        QueryResult r = changeAdapter.execute(list(d), actor(me));
        assertThat(r.rows()).extracting(Row::id).contains("CHG-T03").doesNotContain("CHG-T04");
    }

    @Test
    void problemListAndTitleContains() {
        Problem p = new Problem();
        p.setProblemNumber("PRB-T01");
        p.setTitle("Mạng chập chờn tầng 3");
        problems.save(p);
        DomainDef d = ProblemQueryAdapter.DEF;
        QueryResult r = problemAdapter.execute(list(d, filter(d, "title", FilterOp.CONTAINS, "mạng")),
                actor(fixtures.nhanVienMkt()));
        assertThat(r.rows()).extracting(Row::id).contains("PRB-T01");
    }

    @Test
    void knownErrorSymptomsContains() {
        KnownError k = new KnownError();
        k.setErrorCode("KE-T01");
        k.setTitle("Outlook treo");
        k.setSymptoms("Outlook không phản hồi khi mở file PST lớn");
        knownErrors.save(k);
        DomainDef d = KnownErrorQueryAdapter.DEF;
        QueryResult r = knownErrorAdapter.execute(list(d, filter(d, "symptoms", FilterOp.CONTAINS, "pst")),
                actor(fixtures.nhanVienMkt()));
        assertThat(r.rows()).extracting(Row::id).contains("KE-T01");
        assertThat(r.rows()).filteredOn(row -> row.id().equals("KE-T01")).first()
                .satisfies(row -> assertThat(row.details()).contains("Triệu chứng"));
    }

    @Test
    void disabledDepartmentHiddenFromNonPrivilegedButVisibleToGiamDoc() {
        Department off = new Department();
        off.setCode("OLD");
        off.setName("Phòng cũ");
        off.setEnabled(false);
        departments.save(off);
        DomainDef d = DepartmentQueryAdapter.DEF;
        assertThat(departmentAdapter.execute(list(d), actor(fixtures.truongPhongMkt())).rows())
                .extracting(Row::id).doesNotContain("OLD");
        assertThat(departmentAdapter.execute(list(d), actor(fixtures.giamDoc())).rows())
                .extracting(Row::id).contains("OLD");
    }

    @Test
    void teamListsByDepartmentName() {
        Team t = new Team();
        t.setName("Helpdesk L1");
        t.setCode("HD1");
        t.setDepartment(fixtures.itDepartment());
        t.setEnabled(true);
        teams.save(t);
        DomainDef d = TeamQueryAdapter.DEF;
        QueryResult r = teamAdapter.execute(list(d, filter(d, "department", FilterOp.CONTAINS, "information")),
                actor(fixtures.truongPhongIT()));
        assertThat(r.rows()).extracting(Row::id).contains("HD1");
    }

    @Test
    void teamDepartmentMatchesByCode() {
        Team t = new Team();
        t.setName("Helpdesk L2");
        t.setCode("HD2");
        t.setDepartment(fixtures.itDepartment());
        t.setEnabled(true);
        teams.save(t);
        DomainDef d = TeamQueryAdapter.DEF;
        QueryResult r = teamAdapter.execute(list(d, filter(d, "department", FilterOp.CONTAINS, "IT")),
                actor(fixtures.truongPhongIT()));
        assertThat(r.rows()).extracting(Row::id).contains("HD2");
    }

    @Test
    void userDepartmentMatchesByCode() {
        fixtures.nhanVienIT();
        fixtures.truongPhongMkt();
        DomainDef d = UserQueryAdapter.DEF;
        QueryResult r = userAdapter.execute(list(d, filter(d, "department", FilterOp.EQ, "it")),
                actor(fixtures.truongPhongIT()));
        assertThat(r.rows()).extracting(Row::id).contains("nv_it").doesNotContain("tp_mkt");
    }

    @Test
    void departmentNameFilterAcceptsCode() {
        fixtures.mktDepartment();
        DomainDef d = DepartmentQueryAdapter.DEF;
        QueryResult r = departmentAdapter.execute(list(d, filter(d, "name", FilterOp.CONTAINS, "MKT")),
                actor(fixtures.truongPhongIT()));
        assertThat(r.rows()).extracting(Row::id).contains("MKT");
    }

    @Test
    void teamScopeAllowsItAndDeniesBusinessUsers() {
        DomainDef d = TeamQueryAdapter.DEF;
        assertThat(teamAdapter.execute(list(d), actor(fixtures.nhanVienIT())).status())
                .isNotEqualTo(QueryResult.Status.DENIED);
        assertThat(teamAdapter.execute(list(d), actor(fixtures.truongPhongMkt())).status())
                .isEqualTo(QueryResult.Status.DENIED);
    }

    @Test
    void nhanVienSeesOnlyEnabledItStaff() {
        fixtures.nhanVienIT();
        fixtures.truongPhongMkt();
        DomainDef d = UserQueryAdapter.DEF;
        QueryResult r = userAdapter.execute(list(d), actor(fixtures.nhanVienMkt()));
        assertThat(r.rows()).extracting(Row::id).contains("nv_it").doesNotContain("tp_mkt");
        assertThat(r.scopeNote()).contains("nhân viên IT");
    }

    @Test
    void truongPhongSeesAllUsersByRole() {
        fixtures.nhanVienIT();
        fixtures.truongPhongMkt();
        DomainDef d = UserQueryAdapter.DEF;
        QueryResult r = userAdapter.execute(list(d, filter(d, "role", FilterOp.EQ, Role.TRUONG_PHONG)),
                actor(fixtures.truongPhongMkt()));
        assertThat(r.rows()).extracting(Row::id).contains("tp_mkt").doesNotContain("nv_it");
        assertThat(r.scopeNote()).isNull();
    }
}
