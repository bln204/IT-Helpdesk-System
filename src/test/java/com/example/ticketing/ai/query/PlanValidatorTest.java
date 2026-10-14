package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.query.QueryPlan.PlanFilter;
import com.example.ticketing.ai.query.QueryPlan.PlanSort;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanValidatorTest {

    // Wednesday 2026-10-14
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final DomainCatalog catalog = new DomainCatalog(List.of(new StubAdapter(StubAdapter.ticketLike())));
    private final PlanValidator validator = new PlanValidator(catalog, CLOCK, 20, 50);

    private static QueryPlan plan(String action, String domain, PlanFilter... filters) {
        return new QueryPlan(action, domain, List.of(filters), null, null, null, null, null);
    }

    private static PlanFilter f(String field, String op, String... values) {
        return new PlanFilter(field, op, List.of(values));
    }

    @Test
    void unknownActionBecomesClarify() {
        ValidatedPlan v = validator.validate(plan("dance", "ticket"));
        assertThat(v.action()).isEqualTo(QueryAction.CLARIFY);
        assertThat(v.clarifyQuestion()).isEqualTo(PlanValidator.DEFAULT_CLARIFY);
    }

    @Test
    void nullPlanBecomesClarify() {
        assertThat(validator.validate(null).action()).isEqualTo(QueryAction.CLARIFY);
    }

    @Test
    void unknownDomainBecomesClarifyListingDomains() {
        ValidatedPlan v = validator.validate(plan("list", "spaceship"));
        assertThat(v.action()).isEqualTo(QueryAction.CLARIFY);
        assertThat(v.clarifyQuestion()).contains("ticket");
    }

    @Test
    void unknownFieldBecomesClarifyNamingTheField() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("salary", "eq", "1")));
        assertThat(v.action()).isEqualTo(QueryAction.CLARIFY);
        assertThat(v.clarifyQuestion()).contains("salary").contains("status");
    }

    @Test
    void groupExpandsAndEqBecomesIn() {
        ValidatedPlan v = validator.validate(plan("count", "ticket", f("status", "eq", "OPEN")));
        ResolvedFilter rf = v.filters().get(0);
        assertThat(rf.op()).isEqualTo(FilterOp.IN);
        assertThat(rf.values()).containsExactly(TicketStatus.NEW, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);
    }

    @Test
    void foldedVietnameseGroupAlias() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("status", "in", "đang mở")));
        assertThat(v.filters().get(0).values()).contains(TicketStatus.NEW);
    }

    @Test
    void enumValueIsCaseInsensitive() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("status", "eq", "new")));
        assertThat(v.filters().get(0).values()).containsExactly(TicketStatus.NEW);
        assertThat(v.filters().get(0).op()).isEqualTo(FilterOp.EQ);
    }

    @Test
    void invalidEnumValueBecomesClarifyListingValidValues() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("status", "eq", "HAPPY")));
        assertThat(v.action()).isEqualTo(QueryAction.CLARIFY);
        assertThat(v.clarifyQuestion()).contains("HAPPY").contains("NEW");
    }

    @Test
    void opNotAllowedForFieldBecomesClarify() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("status", "contains", "NEW")));
        assertThat(v.action()).isEqualTo(QueryAction.CLARIFY);
    }

    @Test
    void dateTokenResolvesAgainstClock() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("createdAt", "in_range", "this_week")));
        assertThat(v.filters().get(0).values())
                .containsExactly(new DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19)));
    }

    @Test
    void twoLiteralDatesFormInclusiveRange() {
        ValidatedPlan v = validator.validate(plan("list", "ticket",
                f("createdAt", "in_range", "2026-10-01", "2026-10-05")));
        assertThat(v.filters().get(0).values())
                .containsExactly(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 6)));
    }

    @Test
    void unparseableDateBecomesClarify() {
        assertThat(validator.validate(plan("list", "ticket", f("createdAt", "after", "someday"))).action())
                .isEqualTo(QueryAction.CLARIFY);
    }

    @Test
    void isNullIgnoresValues() {
        ValidatedPlan v = validator.validate(plan("count", "ticket", f("assignee", "is_null", "whatever")));
        assertThat(v.filters().get(0).values()).isEmpty();
    }

    @Test
    void semanticJoinsValuesIntoOneText() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("text", "semantic_match", "máy chậm", "Excel")));
        assertThat(v.filters().get(0).values()).containsExactly("máy chậm Excel");
    }

    @Test
    void limitAndOffsetAreClampedAndAligned() {
        QueryPlan p = new QueryPlan("list", "ticket", List.of(), null, 500, 25, null, null);
        ValidatedPlan v = validator.validate(p);
        assertThat(v.limit()).isEqualTo(50);
        assertThat(v.offset()).isZero();
        QueryPlan q = new QueryPlan("list", "ticket", List.of(), null, null, 45, null, null);
        ValidatedPlan w = validator.validate(q);
        assertThat(w.limit()).isEqualTo(20);
        assertThat(w.offset()).isEqualTo(40);
    }

    @Test
    void sortFallsBackToDomainDefaultWhenFieldUnknown() {
        QueryPlan p = new QueryPlan("list", "ticket", List.of(), new PlanSort("bogus", "asc"), null, null, null, null);
        ValidatedPlan v = validator.validate(p);
        assertThat(v.sortPath()).isEqualTo("createdAt");
        assertThat(v.sortDesc()).isTrue();
        QueryPlan q = new QueryPlan("list", "ticket", List.of(), new PlanSort("priority", "asc"), null, null, null, null);
        assertThat(validator.validate(q).sortPath()).isEqualTo("priority");
    }

    @Test
    void detailWithoutTargetBecomesList() {
        assertThat(validator.validate(plan("detail", "ticket")).action()).isEqualTo(QueryAction.LIST);
    }

    @Test
    void noDataActionsSkipDomain() {
        assertThat(validator.validate(plan("chitchat", "none")).action()).isEqualTo(QueryAction.CHITCHAT);
        assertThat(validator.validate(plan("create_ticket", "none")).action()).isEqualTo(QueryAction.CREATE_TICKET);
        ValidatedPlan c = validator.validate(new QueryPlan("clarify", "none", List.of(), null, null, null, null, "Bạn hỏi gì?"));
        assertThat(c.clarifyQuestion()).isEqualTo("Bạn hỏi gì?");
    }

    @Test
    void semanticMatchOnTextFieldMovesToDomainSemanticField() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("title", "semantic_match", "treo máy")));
        assertThat(v.action()).isEqualTo(QueryAction.LIST);
        assertThat(v.filters().get(0).field().type()).isEqualTo(FieldType.SEMANTIC);
        assertThat(v.filters().get(0).values()).containsExactly("treo máy");
    }

    @Test
    void semanticMatchWithoutSemanticFieldBecomesContains() {
        DomainCatalog noSemantic = new DomainCatalog(List.of(new StubAdapter(new DomainDef("problem", "problem", "x",
                List.of(FieldDef.text("title", "title", "tiêu đề")), "title", false))));
        ValidatedPlan v = new PlanValidator(noSemantic, CLOCK, 20, 50)
                .validate(plan("list", "problem", f("title", "semantic_match", "mạng")));
        assertThat(v.filters().get(0).op()).isEqualTo(FilterOp.CONTAINS);
    }

    @Test
    void eqOnDateBecomesInRange() {
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("createdAt", "eq", "today")));
        assertThat(v.filters().get(0).op()).isEqualTo(FilterOp.IN_RANGE);
        assertThat(v.filters().get(0).values())
                .containsExactly(new DateRange(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15)));
    }

    @Test
    void inventedTargetIdAsksWhichRecordInsteadOfListingEverything() {
        QueryPlan p = new QueryPlan("explain", "ticket", List.of(), null, null, null, "TCK-2026-0001", null);
        ValidatedPlan invented = validator.validate(p, "hệ thống có ổn không", null);
        assertThat(invented.action()).isEqualTo(QueryAction.CLARIFY);
        assertThat(invented.clarifyQuestion()).contains("ticket nào");
        assertThat(validator.validate(p, "tck-2026-0001 sao rồi", null).targetId()).isEqualTo("TCK-2026-0001");
    }

    @Test
    void targetIdFromPreviousPlanIsKeptForFollowUp() {
        QueryPlan previous = new QueryPlan("detail", "ticket", List.of(), null, 20, 0, "TCK-2026-0003", null);
        QueryPlan p = new QueryPlan("detail", "ticket", List.of(), null, null, null, "TCK-2026-0003", null);
        ValidatedPlan v = validator.validate(p, "ai đang xử lý nó?", previous);
        assertThat(v.action()).isEqualTo(QueryAction.DETAIL);
        assertThat(v.targetId()).isEqualTo("TCK-2026-0003");
    }

    @Test
    void textValuesAreNfcNormalized() {
        String nfd = java.text.Normalizer.normalize("Kế toán", java.text.Normalizer.Form.NFD);
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("title", "contains", nfd)));
        assertThat(v.filters().get(0).values()).containsExactly("Kế toán");
    }

    @Test
    void recordCodeInEqFilterBecomesTargetForDetail() {
        ValidatedPlan v = validator.validate(plan("explain", "ticket", f("title", "eq", "TCK-2026-0001")));
        assertThat(v.action()).isEqualTo(QueryAction.EXPLAIN);
        assertThat(v.targetId()).isEqualTo("TCK-2026-0001");
        assertThat(v.filters()).isEmpty();
        // a list keeps its filter; plain words are never treated as codes
        assertThat(validator.validate(plan("list", "ticket", f("title", "eq", "TCK-2026-0001"))).targetId()).isNull();
        assertThat(validator.validate(plan("detail", "ticket", f("title", "eq", "Wi-Fi"))).action()).isEqualTo(QueryAction.LIST);
    }

    @Test
    void resolvedAndEscalateNeedNoDomain() {
        assertThat(validator.validate(plan("resolved", "none")).action()).isEqualTo(QueryAction.RESOLVED);
        assertThat(validator.validate(plan("escalate", "none")).action()).isEqualTo(QueryAction.ESCALATE);
        assertThat(validator.validate(plan("escalate", "none")).domain()).isNull();
    }

    @Test
    void catalogPromptListsFieldsAndGroups() {
        String text = catalog.promptText();
        assertThat(text).contains("ticket").contains("status").contains("NEW|ASSIGNED")
                .contains("nhóm: DANG_MO, OPEN").contains("createdAt [date]").contains("text [semantic]");
    }

    @Test
    void listPlanIgnoresPlaceholderTargetId() {
        QueryPlan p = new QueryPlan("list", "ticket", List.of(), new PlanSort("createdAt", null), null, null, "none", null);
        ValidatedPlan v = validator.validate(p, "hãy cho tôi biết ticket mới nhất là gì", null);
        assertThat(v.action()).isEqualTo(QueryAction.LIST);
        assertThat(v.targetId()).isNull();
    }

    @Test
    void missingSortDirectionUsesDomainDefault() {
        QueryPlan p = new QueryPlan("list", "ticket", List.of(), new PlanSort("createdAt", null), null, null, null, null);
        ValidatedPlan v = validator.validate(p);
        assertThat(v.sortPath()).isEqualTo("createdAt");
        assertThat(v.sortDesc()).isTrue();
        QueryPlan asc = new QueryPlan("list", "ticket", List.of(), new PlanSort("createdAt", "asc"), null, null, null, null);
        assertThat(validator.validate(asc).sortDesc()).isFalse();
    }

    @Test
    void unsupportedTopicNeedsNoDomain() {
        assertThat(validator.validate(plan("unsupported", "none")).action()).isEqualTo(QueryAction.UNSUPPORTED);
    }

    @Test
    void filterCoveringEveryEnumValueIsDropped() {
        String[] all = java.util.Arrays.stream(com.example.ticketing.ticket.TicketTypes.TicketPriority.values())
                .map(Enum::name).toArray(String[]::new);
        ValidatedPlan v = validator.validate(plan("list", "ticket", f("priority", "in", all), f("title", "contains", "máy in")));
        assertThat(v.filters()).extracting(rf -> rf.field().name()).containsExactly("title");
    }

    @Test
    void unknownDomainClarifyUsesVietnameseLabels() {
        DomainCatalog two = new DomainCatalog(List.of(new StubAdapter(StubAdapter.ticketLike()),
                new StubAdapter(new DomainDef("known_error", "known error", "x",
                        List.of(FieldDef.text("title", "title", "tiêu đề")), "title", false))));
        ValidatedPlan v = new PlanValidator(two, CLOCK, 20, 50).validate(plan("list", "spaceship"));
        assertThat(v.clarifyQuestion()).contains("known error").doesNotContain("known_error");
    }

    private PlanValidator boolValidator() {
        DomainDef d = new DomainDef("sla_policy", "chính sách SLA", "x",
                List.of(FieldDef.bool("enabled", "enabled", "đang áp dụng")), "enabled", false);
        return new PlanValidator(new DomainCatalog(List.of(new StubAdapter(d))), CLOCK, 20, 50);
    }

    @Test
    void booleanAcceptsNaturalWords() {
        PlanValidator v = boolValidator();
        for (String yes : new String[] {"true", "có", "Đang hoạt động", "bật", "đang áp dụng"}) {
            assertThat(v.validate(plan("list", "sla_policy", f("enabled", "eq", yes))).filters().get(0).values())
                    .as(yes).containsExactly(Boolean.TRUE);
        }
        for (String no : new String[] {"false", "không", "đã tắt", "chưa", "ngừng"}) {
            assertThat(v.validate(plan("list", "sla_policy", f("enabled", "eq", no))).filters().get(0).values())
                    .as(no).containsExactly(Boolean.FALSE);
        }
        assertThat(v.validate(plan("list", "sla_policy", f("enabled", "eq", "màu xanh"))).action())
                .isEqualTo(QueryAction.CLARIFY);
    }

    @Test
    void booleanAcceptsMorePhrasingsAndSingleValueIn() {
        PlanValidator v = boolValidator();
        for (String yes : new String[] {"đã bật", "enabled", "active", "on", "1", "đang áp dụng", "đúng"}) {
            assertThat(v.validate(plan("list", "sla_policy", f("enabled", "eq", yes))).filters().get(0).values())
                    .as(yes).containsExactly(Boolean.TRUE);
        }
        for (String no : new String[] {"đang tắt", "chưa bật", "disabled", "inactive", "off", "0", "sai",
                "không đang áp dụng"}) {
            assertThat(v.validate(plan("list", "sla_policy", f("enabled", "eq", no))).filters().get(0).values())
                    .as(no).containsExactly(Boolean.FALSE);
        }
        ValidatedPlan in = v.validate(plan("list", "sla_policy", f("enabled", "in", "true")));
        assertThat(in.action()).isEqualTo(QueryAction.LIST);
        assertThat(in.filters().get(0).op()).isEqualTo(FilterOp.EQ);
    }

    @Test
    void defaultClarifyDoesNotListAStaleSetOfDomains() {
        assertThat(PlanValidator.DEFAULT_CLARIFY).doesNotContain("known error").doesNotContain("team)");
    }
}
