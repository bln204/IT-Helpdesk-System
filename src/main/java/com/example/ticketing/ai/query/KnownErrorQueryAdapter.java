package com.example.ticketing.ai.query;

import com.example.ticketing.ai.KnownErrorRagRetrievalService;
import com.example.ticketing.ai.RagContextDto;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.problem.KnownError;
import com.example.ticketing.problem.KnownErrorRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class KnownErrorQueryAdapter extends JpaQueryAdapter<KnownError> {

        /** Mirrors ProblemController#getKnownErrors. */
        static final AccessRule ACCESS = AccessRule.allRoles();

        static final DomainDef DEF = new DomainDef("known_error", "known error",
                        "lỗi đã biết (known error) kèm triệu chứng, cách khắc phục tạm và các bước sửa",
                        List.of(
                                        FieldDef.text("errorCode", "errorCode", "mã known error, vd KE-001"),
                                        FieldDef.text("title", "title", "tiêu đề"),
                                        FieldDef.enumeration("status", "status", KnownError.KnownErrorStatus.class,
                                                        "trạng thái"),
                                        FieldDef.text("category", "category", "nhóm"),
                                        FieldDef.text("symptoms", "symptoms", "triệu chứng"),
                                        FieldDef.date("createdAt", "createdAt", "ngày tạo"),
                                        FieldDef.semantic("mô tả lỗi bằng lời tự do (tìm theo ý nghĩa)")),
                        "createdAt", true);

        private final KnownErrorRepository repository;
        private final KnownErrorRagRetrievalService ragService;
        private final double semanticMinScore;

        public KnownErrorQueryAdapter(KnownErrorRepository repository,
                        KnownErrorRagRetrievalService ragService,
                        @Value("${ai.query.semantic-min-score:0.65}") double semanticMinScore) {
                this.repository = repository;
                this.ragService = ragService;
                this.semanticMinScore = semanticMinScore;
        }

        @Override
        public DomainDef definition() {
                return DEF;
        }

        @Override
        public AccessRule accessRule() {
                return ACCESS;
        }

        @Override
        protected JpaSpecificationExecutor<KnownError> repository() {
                return repository;
        }

        @Override
        protected String idPath() {
                return "errorCode";
        }

        @Override
        protected List<Long> semanticIds(String text, ActorContext actor) {
                return semanticHits(ragService.retrieveContext(
                                new RagContextDto.RetrievalRequest(text, 20, semanticMinScore), actor.username()));
        }

        @Override
        protected Row toRow(KnownError k) {
                String details = "Triệu chứng: " + fmt(k.getSymptoms()) + " | Cách khắc phục tạm: "
                                + fmt(k.getWorkaround())
                                + " | Các bước sửa: " + fmt(k.getFixSteps());
                return new Row(k.getErrorCode(), k.getTitle(), fields(
                                "trạng thái", k.getStatus(),
                                "nhóm", k.getCategory(),
                                "số lần xảy ra", k.getOccurrenceCount()),
                                details);
        }
}
