package com.example.ticketing.ai.query;

import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.servicerequest.ServiceCatalogItem;
import com.example.ticketing.servicerequest.ServiceCatalogRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class ServiceCatalogQueryAdapter extends JpaQueryAdapter<ServiceCatalogItem> {

    /** ServiceRequestController#getServiceCatalog has no @PreAuthorize: any authenticated user. */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("service_catalog", "dịch vụ",
            "danh mục các dịch vụ IT người dùng có thể yêu cầu",
            List.of(
                    FieldDef.text("name", "name", "tên dịch vụ"),
                    FieldDef.text("category", "category", "nhóm dịch vụ"),
                    FieldDef.bool("requiresApproval", "requiresApproval", "cần phê duyệt"),
                    FieldDef.bool("enabled", "enabled", "đang cung cấp")),
            "name", false);

    private final ServiceCatalogRepository repository;

    public ServiceCatalogQueryAdapter(ServiceCatalogRepository repository) {
        this.repository = repository;
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<ServiceCatalogItem> repository() { return repository; }
    @Override protected String idPath() { return "name"; }

    /** Same rows as the UI catalog: ServiceRequestService#getPublicServices (enabled and public). */
    @Override
    protected Scope<ServiceCatalogItem> scope(ActorContext actor) {
        return Scope.of((root, q, cb) -> cb.and(cb.isTrue(root.get("enabled")), cb.isTrue(root.get("isPublic"))), null);
    }

    @Override
    protected Row toRow(ServiceCatalogItem s) {
        return new Row(s.getName(), fmt(s.getCategory()), fields(
                "SLA phản hồi (phút)", s.getResponseSlaMinutes(),
                "SLA xử lý (phút)", s.getResolutionSlaMinutes(),
                "cần phê duyệt", yesNo(s.getRequiresApproval()),
                "đang cung cấp", yesNo(s.getEnabled())),
                s.getDescription());
    }
}
