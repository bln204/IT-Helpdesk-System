package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryExecutorTest {

    private final DomainDef d = StubAdapter.ticketLike();
    private final ActorContext staff = ActorContext.of("nv", Role.NHAN_VIEN, "MKT", false);

    private final com.example.ticketing.auth.UserAccountRepository users =
            org.mockito.Mockito.mock(com.example.ticketing.auth.UserAccountRepository.class);

    private QueryExecutor executor(DomainQueryAdapter adapter) {
        return new QueryExecutor(new DomainCatalog(List.of(adapter)), users);
    }

    @Test
    void meExpandsToUsernameAndDisplayName() {
        com.example.ticketing.auth.UserAccount u = new com.example.ticketing.auth.UserAccount();
        u.setUsername("nv");
        u.setDisplayName("Nguyễn Văn A");
        org.mockito.Mockito.when(users.findByUsername("nv")).thenReturn(java.util.Optional.of(u));
        List<ValidatedPlan> seen = new java.util.ArrayList<>();
        DomainQueryAdapter capture = new DomainQueryAdapter() {
            public DomainDef definition() { return d; }
            public AccessRule accessRule() { return AccessRule.allRoles(); }
            public QueryResult execute(ValidatedPlan plan, ActorContext actor) { seen.add(plan); return QueryResult.count(0, null); }
        };
        executor(capture).execute(QueryTestPlans.list(d, QueryTestPlans.filter(d, "assignee", FilterOp.EQ, "@me")), staff);
        assertThat(seen.get(0).filters().get(0).values()).containsExactly("nv", "Nguyễn Văn A");
    }

    @Test
    void roleGateDeniesBeforeAdapterRuns() {
        DomainQueryAdapter adapter = new StubAdapter(d, AccessRule.of(Role.ADMIN)); // execute() would throw
        QueryResult r = executor(adapter).execute(QueryTestPlans.list(d), staff);
        assertThat(r.status()).isEqualTo(QueryResult.Status.DENIED);
        assertThat(r.message()).isEqualTo("Bạn không có quyền xem ticket. Chức năng này dành cho Admin.");
    }

    @Test
    void adapterExceptionBecomesError() {
        QueryResult r = executor(new StubAdapter(d)).execute(QueryTestPlans.list(d), staff);
        assertThat(r.status()).isEqualTo(QueryResult.Status.ERROR);
    }

    @Test
    void adapterResultPassesThrough() {
        DomainQueryAdapter ok = new DomainQueryAdapter() {
            public DomainDef definition() { return d; }
            public AccessRule accessRule() { return AccessRule.allRoles(); }
            public QueryResult execute(ValidatedPlan plan, ActorContext actor) { return QueryResult.count(3, null); }
        };
        assertThat(executor(ok).execute(QueryTestPlans.list(d), staff).totalCount()).isEqualTo(3);
    }
}
