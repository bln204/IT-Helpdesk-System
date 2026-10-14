package com.example.ticketing.ai.query;

import static com.example.ticketing.ai.query.QueryTestPlans.filter;
import static com.example.ticketing.ai.query.QueryTestPlans.list;
import static com.example.ticketing.ai.query.QueryTestPlans.plan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.ai.RagContextDto;
import com.example.ticketing.ai.RagRetrievalService;
import com.example.ticketing.asset.Asset;
import com.example.ticketing.asset.AssetRepository;
import com.example.ticketing.asset.AssetTicketLink;
import com.example.ticketing.asset.AssetTicketLinkRepository;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketQueryAdapterTest {

    @MockitoBean private EmbeddingService embeddingService;
    @MockitoBean private RagRetrievalService ragRetrievalService;

    @Autowired private TicketAuthorizationFixtures fixtures;
    @Autowired private ItDepartmentResolver itDepartmentResolver;
    @Autowired private TicketRepository tickets;
    @Autowired private AssetRepository assets;
    @Autowired private AssetTicketLinkRepository links;
    @Autowired private TicketQueryAdapter adapter;
    @Autowired private com.example.ticketing.auth.UserAccountRepository users;
    @Autowired private com.example.ticketing.department.DepartmentRepository departmentRepository;

    private final DomainDef d = TicketQueryAdapter.DEF;

    private ActorContext actor(UserAccount u) {
        return ActorContext.of(u, itDepartmentResolver.isITDepartment(u.getDepartment()));
    }

    private Ticket save(String title, UserAccount requester, String assignee) {
        Ticket t = fixtures.newTicket(title, TicketPriority.MEDIUM, requester);
        t.setAssigneeName(assignee);
        return tickets.save(t);
    }

    @Test
    void unassignedFilterRunsBeforePaging() {
        // 5 unassigned tickets created first (oldest => last page by default sort), 25 assigned after.
        for (int i = 0; i < 5; i++) save("PG-Unassigned " + i, fixtures.nhanVienMkt(), null);
        for (int i = 0; i < 25; i++) save("PG-Assigned " + i, fixtures.nhanVienMkt(), "tech.smith");

        QueryResult r = adapter.execute(plan(d, QueryAction.LIST, 20, 0, null,
                filter(d, "title", FilterOp.CONTAINS, "PG-"),
                filter(d, "assignee", FilterOp.IS_NULL),
                filter(d, "status", FilterOp.IN, TicketStatus.NEW, TicketStatus.ASSIGNED)), actor(fixtures.admin()));

        assertThat(r.totalCount()).isEqualTo(5);
        assertThat(r.rows()).hasSize(5).allSatisfy(row -> assertThat(row.title()).startsWith("PG-Unassigned"));
        assertThat(r.rows().get(0).fields()).containsEntry("người xử lý", "(chưa gán)");
    }

    @Test
    void privilegedActorSeesAllWithoutScopeNote() {
        save("MKT ticket", fixtures.nhanVienMkt(), null);
        save("IT ticket", fixtures.nhanVienIT(), null);
        QueryResult r = adapter.execute(list(d), actor(fixtures.giamDoc()));
        assertThat(r.rows()).extracting(Row::title).contains("MKT ticket", "IT ticket");
        assertThat(r.scopeNote()).isNull();
    }

    @Test
    void nonItStaffSeesOwnDepartmentOnlyWithScopeNote() {
        save("MKT ticket", fixtures.nhanVienMkt(), null);
        save("IT ticket", fixtures.nhanVienIT(), null);
        QueryResult r = adapter.execute(list(d), actor(fixtures.truongPhongMkt()));
        assertThat(r.rows()).extracting(Row::title).contains("MKT ticket").doesNotContain("IT ticket");
        assertThat(r.scopeNote()).isEqualTo("Kết quả chỉ gồm ticket thuộc Marketing và ticket do bạn tạo.");
    }

    @Test
    void ticketWithoutDepartmentIsVisibleOnlyToItsRequester() {
        Ticket t = fixtures.newTicket("No dept", TicketPriority.LOW, fixtures.nhanVienMkt());
        t.setDepartment(null);
        tickets.save(t);
        assertThat(adapter.execute(list(d), actor(fixtures.nhanVienMkt())).rows())
                .extracting(Row::title).contains("No dept");
        assertThat(adapter.execute(list(d), actor(fixtures.truongPhongMkt())).rows())
                .extracting(Row::title).doesNotContain("No dept");
    }

    @Test
    void detailOutsideScopeIsDeniedNotEmpty() {
        Ticket it = save("IT secret", fixtures.nhanVienIT(), null);
        QueryResult r = adapter.execute(plan(d, QueryAction.DETAIL, 20, 0, it.getTicketNumber().toLowerCase()),
                actor(fixtures.nhanVienMkt()));
        assertThat(r.status()).isEqualTo(QueryResult.Status.DENIED);
        assertThat(r.message()).isEqualTo("Bạn không có quyền xem " + it.getTicketNumber().toLowerCase() + ".");
    }

    @Test
    void assetRelationFilterUsesLinkTable() {
        Ticket linked = save("Server down", fixtures.nhanVienMkt(), null);
        save("Unrelated", fixtures.nhanVienMkt(), null);
        Asset a = new Asset();
        a.setAssetNumber("AST-LINK1");
        a.setName("Linked server");
        a.setAssetType(Asset.AssetType.SERVER);
        a = assets.save(a);
        AssetTicketLink link = new AssetTicketLink();
        link.setAsset(a);
        link.setTicketId(linked.getId());
        links.save(link);

        QueryResult r = adapter.execute(list(d, filter(d, "asset", FilterOp.EQ, "ast-link1")), actor(fixtures.admin()));
        assertThat(r.rows()).extracting(Row::title).containsExactly("Server down");
    }

    @Test
    void meValueResolvesToCurrentUser() {
        save("Mine", fixtures.nhanVienMkt(), null);
        save("Someone else", fixtures.truongPhongMkt(), null);
        QueryResult r = adapter.execute(list(d, filter(d, "requester", FilterOp.EQ, "@me")), actor(fixtures.nhanVienMkt()));
        assertThat(r.rows()).extracting(Row::title).contains("Mine").doesNotContain("Someone else");
    }

    @Test
    void semanticMatchRestrictsToRetrievedIds() {
        Ticket hit = save("Excel chậm", fixtures.nhanVienMkt(), null);
        save("Chuột hỏng", fixtures.nhanVienMkt(), null);
        RagContextDto.RagSource src = new RagContextDto.RagSource();
        src.setId(hit.getId());
        RagContextDto.RetrievalResponse resp = new RagContextDto.RetrievalResponse();
        resp.setSources(List.of(src));
        when(ragRetrievalService.retrieveContext(any(), anyString())).thenReturn(resp);

        QueryResult r = adapter.execute(list(d, filter(d, "text", FilterOp.SEMANTIC_MATCH, "máy chậm khi mở Excel")),
                actor(fixtures.admin()));
        assertThat(r.rows()).extracting(Row::title).containsExactly("Excel chậm");
    }

    @Test
    void semanticSearchFailureIsAnErrorNotEmpty() {
        save("Chuột hỏng", fixtures.nhanVienMkt(), null);
        RagContextDto.RetrievalResponse failed = new RagContextDto.RetrievalResponse();
        failed.setStatus("embedding_failed");
        when(ragRetrievalService.retrieveContext(any(), anyString())).thenReturn(failed);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.execute(
                list(d, filter(d, "text", FilterOp.SEMANTIC_MATCH, "máy in")), actor(fixtures.admin())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonItUserWithoutDepartmentSeesOwnTickets() {
        UserAccount noDept = new UserAccount();
        noDept.setUsername("nodept_user");
        noDept.setPasswordHash("{noop}x");
        noDept.setRole(com.example.ticketing.auth.UserRole.Role.NHAN_VIEN);
        noDept.setDisplayName("No Dept");
        noDept.setEnabled(true);
        noDept.setApproved(true);
        noDept = users.save(noDept);
        tickets.save(fixtures.newTicket("Của tôi", TicketPriority.LOW, noDept));
        save("Không phải của tôi", fixtures.nhanVienMkt(), null);

        QueryResult r = adapter.execute(list(d), ActorContext.of(noDept, false));
        assertThat(r.status()).isEqualTo(QueryResult.Status.OK);
        assertThat(r.rows()).extracting(Row::title).contains("Của tôi").doesNotContain("Không phải của tôi");
        assertThat(r.scopeNote()).isEqualTo("Kết quả chỉ gồm ticket do bạn tạo.");
    }

    @Test
    void assigneeMatchesLinkedUserDisplayName() {
        UserAccount tech = new UserAccount();
        tech.setUsername("tech_binh");
        tech.setPasswordHash("{noop}x");
        tech.setRole(com.example.ticketing.auth.UserRole.Role.NHAN_VIEN);
        tech.setDepartment(fixtures.itDepartment());
        tech.setDisplayName("Trần Văn Bình");
        tech.setEnabled(true);
        tech.setApproved(true);
        tech = users.save(tech);
        Ticket t = fixtures.newTicket("Gán qua FK", TicketPriority.LOW, fixtures.nhanVienMkt());
        t.setAssignee(tech);
        t.setAssigneeName("tech_binh");
        tickets.save(t);

        QueryResult r = adapter.execute(list(d, filter(d, "assignee", FilterOp.CONTAINS, "Văn Bình")), actor(fixtures.admin()));
        assertThat(r.rows()).extracting(Row::title).contains("Gán qua FK");
    }

    @Test
    void departmentNameMatchesWithoutDiacritics() {
        com.example.ticketing.department.Department kt = new com.example.ticketing.department.Department();
        kt.setCode("KT");
        kt.setName("Phòng Kế toán");
        kt.setEnabled(true);
        kt = departmentRepository.save(kt);
        Ticket t = fixtures.newTicket("Ticket kế toán", TicketPriority.LOW, fixtures.nhanVienMkt());
        t.setDepartment(kt);
        tickets.save(t);

        QueryResult r = adapter.execute(list(d, filter(d, "department", FilterOp.CONTAINS, "ke toan")), actor(fixtures.admin()));
        assertThat(r.rows()).extracting(Row::title).contains("Ticket kế toán");
    }

    @Test
    void departmentMatchesByCode() {
        com.example.ticketing.department.Department kt = new com.example.ticketing.department.Department();
        kt.setCode("KTX");
        kt.setName("Phòng Kế toán");
        kt.setEnabled(true);
        kt = departmentRepository.save(kt);
        Ticket t = fixtures.newTicket("Ticket theo mã phòng", TicketPriority.LOW, fixtures.nhanVienMkt());
        t.setDepartment(kt);
        tickets.save(t);

        QueryResult r = adapter.execute(list(d, filter(d, "department", FilterOp.CONTAINS, "ktx")), actor(fixtures.admin()));
        assertThat(r.rows()).extracting(Row::title).contains("Ticket theo mã phòng");
    }

    @Test
    void semanticMatchWithNoHitsIsEmpty() {
        save("Chuột hỏng", fixtures.nhanVienMkt(), null);
        when(ragRetrievalService.retrieveContext(any(), anyString())).thenReturn(new RagContextDto.RetrievalResponse());
        QueryResult r = adapter.execute(list(d, filter(d, "text", FilterOp.SEMANTIC_MATCH, "máy in")),
                actor(fixtures.admin()));
        assertThat(r.status()).isEqualTo(QueryResult.Status.EMPTY);
    }
}
