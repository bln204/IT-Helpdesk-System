package com.example.ticketing.ai.query;

import com.example.ticketing.problem.Problem;
import com.example.ticketing.problem.ProblemRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class ProblemQueryAdapter extends JpaQueryAdapter<Problem> {

    /** Mirrors ProblemController#getAllProblems. */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("problem", "problem",
            "vấn đề gốc (problem) gây ra nhiều sự cố, có nguyên nhân gốc và cách khắc phục tạm",
            List.of(
                    FieldDef.text("problemNumber", "problemNumber", "mã problem, vd PRB-001"),
                    FieldDef.text("title", "title", "tiêu đề"),
                    FieldDef.enumeration("status", "status", Problem.ProblemStatus.class, "trạng thái"),
                    FieldDef.enumeration("priority", "priority", Problem.Priority.class, "độ ưu tiên"),
                    FieldDef.enumeration("impactLevel", "impactLevel", Problem.ImpactLevel.class, "mức ảnh hưởng"),
                    FieldDef.text("category", "category", "nhóm"),
                    FieldDef.text("assignedTo", "assignedTo", "người phụ trách"),
                    FieldDef.date("createdAt", "createdAt", "ngày tạo")),
            "createdAt", true);

    private final ProblemRepository repository;

    public ProblemQueryAdapter(ProblemRepository repository) {
        this.repository = repository;
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<Problem> repository() { return repository; }
    @Override protected String idPath() { return "problemNumber"; }

    @Override
    protected Row toRow(Problem p) {
        String details = "Nguyên nhân gốc: " + fmt(p.getRootCause()) + " | Cách khắc phục tạm: " + fmt(p.getWorkaround());
        return new Row(p.getProblemNumber(), p.getTitle(), fields(
                "trạng thái", p.getStatus(),
                "ưu tiên", p.getPriority(),
                "ảnh hưởng", p.getImpactLevel(),
                "người phụ trách", p.getAssignedTo(),
                "ngày tạo", p.getCreatedAt()),
                details);
    }
}
