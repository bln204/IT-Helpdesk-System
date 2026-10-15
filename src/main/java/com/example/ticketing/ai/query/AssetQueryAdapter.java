package com.example.ticketing.ai.query;

import com.example.ticketing.asset.Asset;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorTiers;
import com.example.ticketing.authorization.AssetAuthorization;
import com.example.ticketing.asset.AssetRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class AssetQueryAdapter extends JpaQueryAdapter<Asset> {

    /** Mọi role; phạm vi dòng do AssetAuthorization.readScope quyết định. */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("asset", "thiết bị",
            "tài sản/thiết bị IT: máy chủ, laptop, máy in, thiết bị mạng, license phần mềm",
            List.of(
                    FieldDef.text("assetNumber", "assetNumber", "mã thiết bị, vd AST-001"),
                    FieldDef.text("name", "name", "tên thiết bị"),
                    FieldDef.enumeration("assetType", "assetType", Asset.AssetType.class, "loại thiết bị"),
                    FieldDef.enumeration("status", "status", Asset.AssetStatus.class, "trạng thái vận hành"),
                    FieldDef.enumeration("healthStatus", "healthStatus", Asset.HealthStatus.class, "tình trạng sức khỏe")
                            .withGroups(Map.of("PROBLEM", List.of("WARNING", "CRITICAL"))),
                    FieldDef.text("location", "location", "vị trí"),
                    FieldDef.text("assignedTo", "assignedTo", "người đang sử dụng"),
                    FieldDef.text("assignedDepartment", "assignedDepartment", "phòng ban sử dụng"),
                    FieldDef.text("manufacturer", "manufacturer", "hãng sản xuất"),
                    FieldDef.date("warrantyExpiryDate", "warrantyExpiryDate", "ngày hết bảo hành"),
                    FieldDef.date("createdAt", "createdAt", "ngày tạo")),
            "assetNumber", false);

    private final AssetRepository repository;
    private final AssetAuthorization authz;

    public AssetQueryAdapter(AssetRepository repository, AssetAuthorization authz) {
        this.repository = repository;
        this.authz = authz;
    }

    @Override
    protected Scope<Asset> scope(ActorContext actor) {
        return ActorTiers.seesAll(actor)
                ? Scope.all()
                : Scope.of(authz.readScope(actor), "Kết quả chỉ gồm thiết bị bạn đang sử dụng hoặc của phòng ban bạn.");
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<Asset> repository() { return repository; }
    @Override protected String idPath() { return "assetNumber"; }

    @Override
    protected Row toRow(Asset a) {
        return new Row(a.getAssetNumber(), a.getName(), fields(
                "loại", a.getAssetType(),
                "trạng thái", a.getStatus(),
                "sức khỏe", a.getHealthStatus(),
                "vị trí", a.getLocation(),
                "người dùng", a.getAssignedTo(),
                "phòng ban", a.getAssignedDepartment()),
                joinDetails(a.getDescription(), a.getHealthNotes()));
    }

    private static String joinDetails(String description, String healthNotes) {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isBlank()) sb.append("Mô tả: ").append(description);
        if (healthNotes != null && !healthNotes.isBlank()) sb.append(sb.isEmpty() ? "" : " | ")
                .append("Ghi chú sức khỏe: ").append(healthNotes);
        return sb.isEmpty() ? null : sb.toString();
    }
}
