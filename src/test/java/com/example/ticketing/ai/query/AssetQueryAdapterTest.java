package com.example.ticketing.ai.query;

import static com.example.ticketing.ai.query.QueryTestPlans.filter;
import static com.example.ticketing.ai.query.QueryTestPlans.list;
import static com.example.ticketing.ai.query.QueryTestPlans.plan;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.asset.Asset;
import com.example.ticketing.asset.Asset.AssetType;
import com.example.ticketing.asset.Asset.HealthStatus;
import com.example.ticketing.asset.AssetRepository;
import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssetQueryAdapterTest {

    @MockitoBean
    private EmbeddingService embeddingService;

    @Autowired
    private AssetRepository assets;

    @Autowired
    private AssetQueryAdapter adapter;

    private final ActorContext staff = ActorContext.of("nv", Role.NHAN_VIEN, "MKT", false);
    private final DomainDef d = AssetQueryAdapter.DEF;

    private void asset(String number, String name, AssetType type, HealthStatus health) {
        Asset a = new Asset();
        a.setAssetNumber(number);
        a.setName(name);
        a.setAssetType(type);
        a.setHealthStatus(health);
        a.setLocation("Data Center A");
        assets.save(a);
    }

    /** Restricts every query to this test's rows, whatever else the H2 context seeded. */
    private ResolvedFilter mine() {
        return filter(d, "assetNumber", FilterOp.CONTAINS, "AST-T0");
    }

    @BeforeEach
    void seed() {
        asset("AST-T01", "Dell PowerEdge R740", AssetType.SERVER, HealthStatus.CRITICAL);
        asset("AST-T02", "HP ProLiant DL380", AssetType.SERVER, HealthStatus.WARNING);
        asset("AST-T03", "Asus 304 50% pin", AssetType.LAPTOP, HealthStatus.HEALTHY);
    }

    @Test
    void listReturnsAllRowsWithTotal() {
        QueryResult r = adapter.execute(list(d, mine()), staff);
        assertThat(r.status()).isEqualTo(QueryResult.Status.OK);
        assertThat(r.totalCount()).isEqualTo(3);
        assertThat(r.rows()).extracting(Row::id).containsExactly("AST-T01", "AST-T02", "AST-T03");
        assertThat(r.rows().get(0).fields()).containsEntry("loại", "SERVER");
    }

    @Test
    void enumInFilterRunsInSql() {
        QueryResult r = adapter.execute(list(d, mine(),
                filter(d, "healthStatus", FilterOp.IN, HealthStatus.WARNING, HealthStatus.CRITICAL)), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactlyInAnyOrder("AST-T01", "AST-T02");
    }

    @Test
    void countUsesSqlCount() {
        QueryResult r = adapter.execute(plan(d, QueryAction.COUNT, 20, 0, null, mine(),
                filter(d, "assetType", FilterOp.EQ, AssetType.SERVER)), staff);
        assertThat(r.totalCount()).isEqualTo(2);
        assertThat(r.rows()).isEmpty();
    }

    @Test
    void pagingReportsHasMore() {
        QueryResult first = adapter.execute(plan(d, QueryAction.LIST, 2, 0, null, mine()), staff);
        assertThat(first.rows()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        QueryResult second = adapter.execute(plan(d, QueryAction.LIST, 2, 2, null, mine()), staff);
        assertThat(second.rows()).extracting(Row::id).containsExactly("AST-T03");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void textContainsIsCaseInsensitive() {
        QueryResult r = adapter.execute(list(d, mine(), filter(d, "name", FilterOp.CONTAINS, "proliant")), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("AST-T02");
    }

    @Test
    void likeWildcardsAreEscaped() {
        QueryResult percent = adapter.execute(list(d, mine(), filter(d, "name", FilterOp.CONTAINS, "50%")), staff);
        assertThat(percent.rows()).extracting(Row::id).containsExactly("AST-T03");
        QueryResult underscore = adapter.execute(list(d, mine(), filter(d, "name", FilterOp.CONTAINS, "_")), staff);
        assertThat(underscore.status()).isEqualTo(QueryResult.Status.EMPTY);
    }

    @Test
    void detailMatchesIdCaseInsensitive() {
        QueryResult r = adapter.execute(plan(d, QueryAction.DETAIL, 20, 0, "ast-t02"), staff);
        assertThat(r.rows()).extracting(Row::id).containsExactly("AST-T02");
    }

    @Test
    void detailWithNonMatchingFilterIsEmptyNotDenied() {
        QueryResult r = adapter.execute(plan(d, QueryAction.DETAIL, 20, 0, "AST-T02",
                filter(d, "healthStatus", FilterOp.EQ, HealthStatus.HEALTHY)), staff);
        assertThat(r.status()).isEqualTo(QueryResult.Status.EMPTY);
    }

    @Test
    void detailOfMissingIdIsEmptyNotDenied() {
        QueryResult r = adapter.execute(plan(d, QueryAction.DETAIL, 20, 0, "AST-NOPE"), staff);
        assertThat(r.status()).isEqualTo(QueryResult.Status.EMPTY);
    }
}
