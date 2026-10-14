package com.example.ticketing.ai.query;

import static com.example.ticketing.ai.query.QueryTestPlans.filter;
import static com.example.ticketing.ai.query.QueryTestPlans.list;
import static com.example.ticketing.ai.query.QueryTestPlans.plan;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.incident.Incident;
import com.example.ticketing.incident.IncidentRepository;
import com.example.ticketing.servicerequest.ServiceCatalogItem;
import com.example.ticketing.servicerequest.ServiceCatalogRepository;
import com.example.ticketing.servicerequest.ServiceRequest;
import com.example.ticketing.servicerequest.ServiceRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NewDomainAdaptersTest {

    @MockitoBean private EmbeddingService embeddingService;
    @Autowired private IncidentRepository incidents;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private ServiceCatalogRepository catalog;
    @Autowired private IncidentQueryAdapter incidentAdapter;
    @Autowired private ServiceRequestQueryAdapter requestAdapter;
    @Autowired private ServiceCatalogQueryAdapter catalogAdapter;

    @Autowired private com.example.ticketing.sla.SlaPolicyRepository slas;
    @Autowired private com.example.ticketing.escalation.EscalationRuleRepository rules;
    @Autowired private com.example.ticketing.category.CategoryRepository categories;
    @Autowired private SlaPolicyQueryAdapter slaAdapter;
    @Autowired private EscalationRuleQueryAdapter ruleAdapter;
    @Autowired private CategoryQueryAdapter categoryAdapter;

    private final ActorContext staff = ActorContext.of("nv", Role.NHAN_VIEN, "MKT", false);

    private Incident incident(String number, Incident.IncidentStatus status, Boolean responseBreached, Boolean resolutionBreached) {
        Incident i = new Incident();
        i.setIncidentNumber(number);
        i.setTitle("Incident " + number);
        i.setStatus(status);
        i.setResponseSlaBreached(responseBreached);
        i.setResolutionSlaBreached(resolutionBreached);
        return incidents.save(i);
    }

    @Test
    void incidentSlaBreachedIsResponseOrResolution() {
        incident("INC-T1", Incident.IncidentStatus.INVESTIGATING, true, false);
        incident("INC-T2", Incident.IncidentStatus.IN_PROGRESS, false, true);
        incident("INC-T3", Incident.IncidentStatus.IN_PROGRESS, false, false);
        DomainDef d = IncidentQueryAdapter.DEF;
        QueryResult r = incidentAdapter.execute(list(d, filter(d, "incidentNumber", FilterOp.CONTAINS, "INC-T"),
                filter(d, "slaBreached", FilterOp.EQ, Boolean.TRUE)), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactlyInAnyOrder("INC-T1", "INC-T2");
        assertThat(r.rows().get(0).fields()).containsEntry("vi phạm SLA", "có");
    }

    @Test
    void nullBreachFlagsCountAsNotBreached() {
        incident("INC-N1", Incident.IncidentStatus.INVESTIGATING, null, null);
        DomainDef d = IncidentQueryAdapter.DEF;
        QueryResult r = incidentAdapter.execute(list(d, filter(d, "incidentNumber", FilterOp.CONTAINS, "INC-N"),
                filter(d, "slaBreached", FilterOp.EQ, Boolean.FALSE)), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("INC-N1");
    }

    @Test
    void incidentStatusGroupOpen() {
        incident("INC-S1", Incident.IncidentStatus.INVESTIGATING, false, false);
        incident("INC-S2", Incident.IncidentStatus.CLOSED, false, false);
        DomainDef d = IncidentQueryAdapter.DEF;
        QueryResult r = incidentAdapter.execute(plan(d, QueryAction.COUNT, 20, 0, null,
                filter(d, "incidentNumber", FilterOp.CONTAINS, "INC-S"),
                filter(d, "status", FilterOp.IN, Incident.IncidentStatus.INVESTIGATING,
                        Incident.IncidentStatus.IN_PROGRESS, Incident.IncidentStatus.IDENTIFIED)), staff);
        assertThat(r.totalCount()).isEqualTo(1);
    }

    @Test
    void serviceRequestByRequesterAndStatus() {
        ServiceRequest sr = new ServiceRequest();
        sr.setRequestNumber("SR-T1");
        sr.setTitle("Cài Visio");
        sr.setServiceName("Cài phần mềm");
        sr.setRequesterName("Carlos Nguyen");
        sr.setRequesterUsername("carlos");
        sr.setStatus(ServiceRequest.ServiceStatus.SUBMITTED);
        requests.save(sr);
        DomainDef d = ServiceRequestQueryAdapter.DEF;
        QueryResult r = requestAdapter.execute(list(d, filter(d, "requester", FilterOp.CONTAINS, "carlos"),
                filter(d, "status", FilterOp.EQ, ServiceRequest.ServiceStatus.SUBMITTED)), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("SR-T1");
        assertThat(r.rows().get(0).fields()).containsEntry("dịch vụ", "Cài phần mềm").doesNotContainKey("chi phí");
    }

    @Test
    void catalogByApprovalFlag() {
        ServiceCatalogItem a = new ServiceCatalogItem();
        a.setName("Cấp laptop T");
        a.setCategory("Phần cứng");
        a.setRequiresApproval(true);
        a.setEnabled(true);
        catalog.save(a);
        ServiceCatalogItem b = new ServiceCatalogItem();
        b.setName("Reset mật khẩu T");
        b.setCategory("Tài khoản");
        b.setRequiresApproval(false);
        b.setEnabled(true);
        catalog.save(b);
        DomainDef d = ServiceCatalogQueryAdapter.DEF;
        QueryResult r = catalogAdapter.execute(list(d, filter(d, "name", FilterOp.CONTAINS, " T"),
                filter(d, "requiresApproval", FilterOp.EQ, Boolean.TRUE)), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("Cấp laptop T");
    }

    @Test
    void slaPolicyByPriorityAndEnabled() {
        com.example.ticketing.sla.SlaPolicy p = new com.example.ticketing.sla.SlaPolicy();
        p.setName("SLA T High");
        p.setPriority(com.example.ticketing.ticket.TicketTypes.TicketPriority.HIGH);
        p.setResponseMinutes(30);
        p.setResolutionMinutes(240);
        p.setEnabled(true);
        slas.save(p);
        DomainDef d = SlaPolicyQueryAdapter.DEF;
        QueryResult r = slaAdapter.execute(list(d, filter(d, "name", FilterOp.CONTAINS, "SLA T"),
                filter(d, "priority", FilterOp.EQ, com.example.ticketing.ticket.TicketTypes.TicketPriority.HIGH),
                filter(d, "enabled", FilterOp.EQ, Boolean.TRUE)), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("SLA T High");
        assertThat(r.rows().get(0).fields()).containsEntry("phản hồi (phút)", "30");
    }

    @Test
    void escalationRuleEnabledFilterAndAccess() {
        com.example.ticketing.escalation.EscalationRule rule = new com.example.ticketing.escalation.EscalationRule();
        rule.setName("Rule T");
        rule.setTriggerType(com.example.ticketing.escalation.EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED);
        rule.setEnabled(false);
        rules.save(rule);
        DomainDef d = EscalationRuleQueryAdapter.DEF;
        QueryResult r = ruleAdapter.execute(list(d, filter(d, "name", FilterOp.CONTAINS, "Rule T"),
                filter(d, "enabled", FilterOp.EQ, Boolean.FALSE)), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("Rule T");
        assertThat(EscalationRuleQueryAdapter.ACCESS.allows(ActorContext.of("gd", Role.GIAM_DOC, "EXEC", false))).isFalse();
        assertThat(EscalationRuleQueryAdapter.ACCESS.allows(staff)).isTrue();
    }

    @Test
    void subcategoriesByParentName() {
        com.example.ticketing.category.Category parent = new com.example.ticketing.category.Category();
        parent.setCode("SWT");
        parent.setName("Phần mềm T");
        parent.setEnabled(true);
        parent = categories.save(parent);
        com.example.ticketing.category.Category child = new com.example.ticketing.category.Category();
        child.setCode("SWT-OFFICE");
        child.setName("Office T");
        child.setParent(parent);
        child.setEnabled(true);
        categories.save(child);
        DomainDef d = CategoryQueryAdapter.DEF;
        QueryResult r = categoryAdapter.execute(list(d, filter(d, "parent", FilterOp.CONTAINS, "phần mềm t")), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("SWT-OFFICE");
        assertThat(r.rows().get(0).fields()).containsEntry("danh mục cha", "Phần mềm T");
    }

    @Test
    void catalogShowsOnlyWhatTheUiShows() {
        ServiceCatalogItem hidden = new ServiceCatalogItem();
        hidden.setName("Nội bộ H");
        hidden.setEnabled(true);
        hidden.setIsPublic(false);
        catalog.save(hidden);
        ServiceCatalogItem off = new ServiceCatalogItem();
        off.setName("Ngừng H");
        off.setEnabled(false);
        off.setIsPublic(true);
        catalog.save(off);
        ServiceCatalogItem shown = new ServiceCatalogItem();
        shown.setName("Công khai H");
        shown.setEnabled(true);
        shown.setIsPublic(true);
        catalog.save(shown);
        DomainDef d = ServiceCatalogQueryAdapter.DEF;
        QueryResult r = catalogAdapter.execute(list(d, filter(d, "name", FilterOp.CONTAINS, " H")), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("Công khai H");
    }

    @Autowired private com.example.ticketing.auth.UserAccountRepository users;

    @Test
    void incidentAssigneeMatchesUsernameOrDisplayName() {
        com.example.ticketing.auth.UserAccount tech = new com.example.ticketing.auth.UserAccount();
        tech.setUsername("tech_inc");
        tech.setPasswordHash("{noop}x");
        tech.setRole(Role.NHAN_VIEN);
        tech.setDisplayName("Phạm Văn Kỹ");
        tech.setEnabled(true);
        tech.setApproved(true);
        tech = users.save(tech);
        Incident i = incident("INC-A1", Incident.IncidentStatus.INVESTIGATING, false, false);
        i.setAssignedTo(tech);
        incidents.save(i);
        DomainDef d = IncidentQueryAdapter.DEF;
        assertThat(incidentAdapter.execute(list(d, filter(d, "assignee", FilterOp.CONTAINS, "tech_inc")), staff).rows())
                .extracting(Row::id).containsExactly("INC-A1");
        assertThat(incidentAdapter.execute(list(d, filter(d, "assignee", FilterOp.CONTAINS, "Văn Kỹ")), staff).rows())
                .extracting(Row::id).containsExactly("INC-A1");
    }
}
