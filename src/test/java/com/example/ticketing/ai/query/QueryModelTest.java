package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryModelTest {

    @Test
    void actionAndOpParseIgnoreCaseAndRejectUnknown() {
        assertThat(QueryAction.parse("list")).contains(QueryAction.LIST);
        assertThat(QueryAction.parse(" Create_Ticket ")).contains(QueryAction.CREATE_TICKET);
        assertThat(QueryAction.parse("bogus")).isEmpty();
        assertThat(QueryAction.parse(null)).isEmpty();
        assertThat(FilterOp.parse("is_null")).contains(FilterOp.IS_NULL);
        assertThat(FilterOp.SEMANTIC_MATCH.wire()).isEqualTo("semantic_match");
    }

    @Test
    void queryResultComputesStatusAndHasMore() {
        Row row = new Row("A-1", "x", Map.of(), null);
        QueryResult page = QueryResult.of(List.of(row, row), 5, 0, null);
        assertThat(page.status()).isEqualTo(QueryResult.Status.OK);
        assertThat(page.hasMore()).isTrue();
        assertThat(QueryResult.of(List.of(row), 3, 2, null).hasMore()).isFalse();
        assertThat(QueryResult.of(List.of(), 0, 0, null).status()).isEqualTo(QueryResult.Status.EMPTY);
        assertThat(QueryResult.count(0, null).status()).isEqualTo(QueryResult.Status.EMPTY);
        assertThat(QueryResult.count(8, null).totalCount()).isEqualTo(8);
    }

    @Test
    void accessRuleAllowsListedRolesAndExplainsDenial() {
        AccessRule rule = AccessRule.of(Role.ADMIN, Role.GIAM_DOC, Role.TRUONG_PHONG);
        assertThat(rule.allows(ActorContext.of("tp", Role.TRUONG_PHONG, "MKT", false))).isTrue();
        assertThat(rule.allows(ActorContext.of("nv", Role.NHAN_VIEN, "MKT", false))).isFalse();
        assertThat(rule.allows(null)).isFalse();
        assertThat(rule.deniedMessage("danh sách team"))
                .isEqualTo("Bạn không có quyền xem danh sách team. Chức năng này dành cho Admin, Giám đốc, Trưởng phòng.");
        assertThat(AccessRule.allRoles().allows(ActorContext.of("nv", Role.NHAN_VIEN, "MKT", false))).isTrue();
    }

    @Test
    void fieldDefExposesEnumNamesAndSortedGroups() {
        FieldDef status = FieldDef.enumeration("status", "status", TicketStatus.class, "trạng thái")
                .withGroups(Map.of("OPEN", List.of("NEW"), "DONE", List.of("CLOSED")));
        assertThat(status.enumNames()).contains("NEW", "CLOSED");
        assertThat(status.groups().keySet()).containsExactly("DONE", "OPEN");
        assertThat(status.ops()).containsExactlyInAnyOrder(FilterOp.EQ, FilterOp.IN, FilterOp.NOT_IN);
    }

    @Test
    void domainFieldLookupIsCaseInsensitive() {
        DomainDef d = new DomainDef("asset", "thiết bị", "tài sản",
                List.of(FieldDef.text("name", "name", "tên")), "name", false);
        assertThat(d.field("NAME")).isPresent();
        assertThat(d.field("nope")).isEmpty();
        assertThat(d.field(null)).isEmpty();
    }

    @Test
    void foldRemovesVietnameseMarks() {
        assertThat(Text.fold("Đang mở")).isEqualTo("Dang mo");
        assertThat(Text.fold("chưa được phân công")).isEqualTo("chua duoc phan cong");
    }
}
