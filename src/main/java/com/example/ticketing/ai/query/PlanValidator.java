package com.example.ticketing.ai.query;

import com.example.ticketing.ai.query.QueryPlan.PlanFilter;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Turns the untrusted LLM plan into a typed plan, or into a clarify question when it cannot. */
@Component
public class PlanValidator {

    public static final String DEFAULT_CLARIFY =
            "Bạn có thể nói rõ hơn bạn muốn tìm thông tin gì không? Ví dụ: ticket, thiết bị, sự cố lớn, "
                    + "yêu cầu dịch vụ, chính sách SLA.";

    private final DomainCatalog catalog;
    private final Clock clock;
    private final int pageSize;
    private final int maxLimit;

    @Autowired
    public PlanValidator(DomainCatalog catalog,
                         @Value("${ai.query.page-size:20}") int pageSize,
                         @Value("${ai.query.max-limit:50}") int maxLimit) {
        this(catalog, Clock.systemDefaultZone(), pageSize, maxLimit);
    }

    PlanValidator(DomainCatalog catalog, Clock clock, int pageSize, int maxLimit) {
        this.catalog = catalog;
        this.clock = clock;
        this.pageSize = pageSize;
        this.maxLimit = maxLimit;
    }

    /** Also drops a targetId the user never typed (in this question or the previous plan): the LLM invented it. */
    public ValidatedPlan validate(QueryPlan plan, String question) {
        return validate(plan, question, null);
    }

    /**
     * Also rejects a targetId the user never typed (in this question or the previous plan): the LLM invented it,
     * and answering about some other record would be worse than asking which one.
     */
    public ValidatedPlan validate(QueryPlan plan, String question, QueryPlan previous) {
        // targetId only means something for detail/explain; on a list/count it is noise ("none"), not an invented code.
        boolean recordQuestion = plan != null
                && QueryAction.parse(plan.action()).map(a -> a == QueryAction.DETAIL || a == QueryAction.EXPLAIN).orElse(false);
        if (plan == null || isBlank(plan.targetId()) || question == null || !recordQuestion) return validate(plan);
        String id = Text.fold(plan.targetId().trim()).toUpperCase(Locale.ROOT);
        boolean typed = Text.fold(question).toUpperCase(Locale.ROOT).contains(id);
        boolean carried = previous != null && previous.targetId() != null
                && Text.fold(previous.targetId().trim()).toUpperCase(Locale.ROOT).equals(id);
        if (typed || carried) return validate(plan);
        String label = catalog.domain(plan.domain()).map(DomainDef::label).orElse(null);
        return ValidatedPlan.clarify(label == null ? DEFAULT_CLARIFY
                : "Bạn đang hỏi về " + label + " nào? Hãy cho tôi mã cụ thể.", plan);
    }

    public ValidatedPlan validate(QueryPlan plan) {
        if (plan == null) return ValidatedPlan.clarify(DEFAULT_CLARIFY, null);
        Optional<QueryAction> parsed = QueryAction.parse(plan.action());
        if (parsed.isEmpty()) return ValidatedPlan.clarify(DEFAULT_CLARIFY, plan);
        QueryAction action = parsed.get();

        switch (action) {
            case CLARIFY:
                return ValidatedPlan.clarify(isBlank(plan.clarifyQuestion()) ? DEFAULT_CLARIFY : plan.clarifyQuestion(), plan);
            case CHITCHAT:
            case CREATE_TICKET:
            case TROUBLESHOOT:
            case RESOLVED:
            case ESCALATE:
            case UNSUPPORTED:
                return ValidatedPlan.noData(action, plan);
            default:
                break;
        }

        Optional<DomainDef> domainOpt = catalog.domain(plan.domain());
        if (domainOpt.isEmpty()) {
            return ValidatedPlan.clarify("Bạn muốn tìm thông tin về "
                    + String.join(", ", catalog.labels()) + "?", plan);
        }
        DomainDef domain = domainOpt.get();

        List<ResolvedFilter> filters = new ArrayList<>();
        for (PlanFilter pf : plan.filters() == null ? List.<PlanFilter>of() : plan.filters()) {
            try {
                ResolvedFilter rf = resolve(domain, pf);
                // "role in [every role]" filters nothing but reads as a restriction in the answer: drop it.
                boolean coversAll = rf.field().type() == FieldType.ENUM && rf.op() == FilterOp.IN
                        && rf.values().size() == rf.field().enumNames().size();
                if (!coversAll) filters.add(rf);
            } catch (InvalidPlanException e) {
                return ValidatedPlan.clarify(e.getMessage(), plan);
            }
        }

        boolean recordQuestion = action == QueryAction.DETAIL || action == QueryAction.EXPLAIN;
        String targetId = !recordQuestion || isBlank(plan.targetId()) ? null : plan.targetId().trim();
        if (recordQuestion && targetId == null) {
            // The model sometimes puts the record code in an eq filter ("errorCode = KE-001") instead of targetId.
            for (ResolvedFilter f : filters) {
                if (f.op() == FilterOp.EQ && f.values().size() == 1
                        && GroundingChecker.CODE.matcher(String.valueOf(f.values().get(0))).matches()) {
                    targetId = String.valueOf(f.values().get(0));
                    filters.remove(f);
                    break;
                }
            }
        }
        if (action == QueryAction.DETAIL && targetId == null) action = QueryAction.LIST;

        String sortPath = domain.defaultSortPath();
        boolean sortDesc = domain.defaultSortDesc();
        if (plan.sort() != null) {
            Optional<FieldDef> sf = domain.field(plan.sort().field());
            if (sf.isPresent() && sf.get().path() != null && sf.get().type() != FieldType.SEMANTIC) {
                String dir = plan.sort().dir();
                // No direction given: keep the domain's natural order (newest first for createdAt), not ascending.
                sortDesc = "desc".equalsIgnoreCase(dir)
                        || (!"asc".equalsIgnoreCase(dir) && sf.get().path().equals(domain.defaultSortPath())
                                && domain.defaultSortDesc());
                sortPath = sf.get().path();
            }
        }

        int limit = plan.limit() == null ? pageSize : Math.max(1, Math.min(maxLimit, plan.limit()));
        int offset = plan.offset() == null ? 0 : Math.max(0, plan.offset());
        offset = offset - offset % limit;

        return new ValidatedPlan(action, domain, filters, sortPath, sortDesc, limit, offset, targetId, null, plan);
    }

    private ResolvedFilter resolve(DomainDef domain, PlanFilter pf) {
        FieldDef named = domain.field(pf.field()).orElseThrow(() -> new InvalidPlanException(
                "Tôi không có thông tin về '" + pf.field() + "' của " + domain.label() + ". Có thể lọc theo: "
                        + domain.fields().stream().map(FieldDef::name).collect(Collectors.joining(", ")) + "."));
        FilterOp op = FilterOp.parse(pf.op()).orElseThrow(() -> unsupported(named, pf.op()));
        FieldDef field = named;
        // Small models often attach semantic_match to a text field, or eq to a date: repair the obvious intent.
        if (op == FilterOp.SEMANTIC_MATCH && field.type() != FieldType.SEMANTIC) {
            Optional<FieldDef> semantic = domain.fields().stream().filter(x -> x.type() == FieldType.SEMANTIC).findFirst();
            if (semantic.isPresent()) field = semantic.get();
            else op = FilterOp.CONTAINS;
        }
        if (op == FilterOp.EQ && field.type() == FieldType.DATE) op = FilterOp.IN_RANGE;
        // "in [true]" on a yes/no field means eq.
        if (op == FilterOp.IN && field.type() == FieldType.BOOLEAN && pf.values() != null && pf.values().size() == 1) {
            op = FilterOp.EQ;
        }
        if (!field.ops().contains(op)) throw unsupported(field, pf.op());

        List<String> raw = pf.values() == null ? List.of()
                : pf.values().stream().filter(v -> !isBlank(v))
                        .map(v -> java.text.Normalizer.normalize(v.trim(), java.text.Normalizer.Form.NFC)).toList();

        if (op == FilterOp.IS_NULL || op == FilterOp.NOT_NULL) {
            return new ResolvedFilter(field, op, List.of());
        }
        if (raw.isEmpty()) {
            throw new InvalidPlanException("Thiếu giá trị để lọc theo " + field.description() + ".");
        }
        return switch (field.type()) {
            case SEMANTIC -> new ResolvedFilter(field, op, List.of(String.join(" ", raw)));
            case ENUM -> resolveEnum(field, op, raw);
            case DATE -> resolveDate(field, op, raw);
            case TEXT, CUSTOM -> new ResolvedFilter(field, op, List.copyOf(raw));
            case BOOLEAN -> resolveBoolean(field, op, raw);
        };
    }

    private static final Set<String> TRUE_WORDS = Set.of("true", "co", "yes", "bat", "dang bat", "dang hoat dong",
            "hoat dong", "dang ap dung", "ap dung", "da", "roi", "da bat", "enabled", "active", "on", "1", "dung");
    private static final Set<String> FALSE_WORDS = Set.of("false", "khong", "no", "tat", "da tat", "chua", "ngung",
            "da ngung", "khong hoat dong", "dang tat", "chua bat", "disabled", "inactive", "off", "0", "sai",
            "khong dang ap dung");

    private static ResolvedFilter resolveBoolean(FieldDef field, FilterOp op, List<String> raw) {
        String key = Text.fold(raw.get(0)).trim().toLowerCase(Locale.ROOT);
        if (TRUE_WORDS.contains(key)) return new ResolvedFilter(field, op, List.of(Boolean.TRUE));
        if (FALSE_WORDS.contains(key)) return new ResolvedFilter(field, op, List.of(Boolean.FALSE));
        throw new InvalidPlanException("Giá trị '" + raw.get(0) + "' không hợp lệ cho " + field.description()
                + ". Hãy trả lời có hoặc không.");
    }

    private ResolvedFilter resolveEnum(FieldDef field, FilterOp op, List<String> raw) {
        Set<Object> constants = new LinkedHashSet<>();
        for (String value : raw) {
            String key = Text.fold(value).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            List<String> names = field.groups().getOrDefault(key, List.of(key));
            for (String name : names) {
                constants.add(constant(field, name).orElseThrow(() -> new InvalidPlanException(
                        "Giá trị '" + value + "' không hợp lệ cho " + field.description() + ". Giá trị hợp lệ: "
                                + String.join(", ", field.enumNames()) + ".")));
            }
        }
        FilterOp effective = op == FilterOp.EQ && constants.size() > 1 ? FilterOp.IN : op;
        return new ResolvedFilter(field, effective, List.copyOf(constants));
    }

    private static Optional<Object> constant(FieldDef field, String name) {
        for (Enum<?> c : field.enumType().getEnumConstants()) {
            if (c.name().equals(name)) return Optional.of(c);
        }
        return Optional.empty();
    }

    private ResolvedFilter resolveDate(FieldDef field, FilterOp op, List<String> raw) {
        LocalDate today = LocalDate.now(clock);
        DateRange first = DateTokens.resolve(raw.get(0), today);
        if (first == null) throw invalidDate(field, raw.get(0));
        if (op == FilterOp.IN_RANGE && raw.size() > 1) {
            DateRange last = DateTokens.resolve(raw.get(raw.size() - 1), today);
            if (last == null) throw invalidDate(field, raw.get(raw.size() - 1));
            return new ResolvedFilter(field, op, List.of(new DateRange(first.from(), last.toExclusive())));
        }
        return new ResolvedFilter(field, op, List.of(first));
    }

    private static InvalidPlanException invalidDate(FieldDef field, String value) {
        return new InvalidPlanException("Tôi không hiểu mốc thời gian '" + value + "' cho " + field.description() + ".");
    }

    private static InvalidPlanException unsupported(FieldDef field, String op) {
        return new InvalidPlanException("Không hỗ trợ kiểu lọc '" + op + "' cho " + field.description() + ".");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static final class InvalidPlanException extends RuntimeException {
        InvalidPlanException(String message) {
            super(message);
        }
    }
}
