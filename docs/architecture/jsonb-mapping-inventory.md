# JSONB Mapping Inventory

**Checkpoint:** CP-3.3.14-R2 (reconcile verification status)
**Scope:** Audit toàn bộ JSON/JSONB columns trong PostgreSQL schema và JPA entity mapping tương ứng.
**Last updated:** 2026-10-13 (CP-3.3.16-S — chuẩn hóa thuật ngữ và dead-schema conclusions theo CP-3.3.15 / CP-3.3.16 / CP-3.3.16-R)

> **Runtime verification status (CP-3.3.14-R2):** `assets.specifications` hiện tại được ghi là `VERIFIED_POSTGRES` (CP-3.3.14). Tuy nhiên, `AssetPostgresJsonbIntegrationTest` chưa được chạy trên PostgreSQL thật — test bị skip do `IT_TEST_PG` không được đặt. Trạng thái của `assets.specifications` được điều chỉnh xuống `STATIC_VERIFIED` để phản ánh đúng bằng chứng thực tế: test đã được viết và compile thành công, nhưng chưa có runtime execution. Để nâng lên `VERIFIED_POSTGRES`, cần set `IT_TEST_PG=true` + credentials và chạy `mvn -o test -Dtest=AssetPostgresJsonbIntegrationTest`. Xem mục 2 và 4.5 để biết chi tiết.

## 1. Tóm tắt

- **10 cột JSONB** trong PostgreSQL schema (tính theo `JSONB` keyword trong Flyway migrations V1–V71).
- **0 cột `json` (plain)** — toàn bộ schema dùng `JSONB`, không có cột nào khai báo `json` (không phải `jsonb`).
- **7 entity field mapping** trên 6 entity khớp với 7 trong 10 cột JSONB.
- **3 cột JSONB không có entity mapping** — dead schema (`saved_reports.query_params`, `saved_reports.filters`, `report_exports.parameters`). Xem mục 5.
- **0 field** hiện tại có `@Column(columnDefinition = "jsonb")` mà thiếu `@JdbcTypeCode(SqlTypes.JSON)`.
  - **6 mapping** (`ServiceCatalogItem.formFields`, `ServiceRequest.formData`, `ChangeRequest.riskFactors`, `ChangeTimeline.eventData`, `ReportConfig.config`, `AiMessage.metadata`) đã verify trên PostgreSQL thật qua CP-3.3.5/7/8/9/10/11.
  - **1 mapping** (`Asset.specifications`) chưa verify runtime — test đã viết và compile thành công, nhưng chưa chạy PostgreSQL. Xem mục 2, 3, 4.5.
- **1 field** (`Asset.specifications`) dùng `Map<String, Object>` thay vì `String`, khác biệt duy nhất so với 6 mapping `String` còn lại.
- **42 entity class** tổng cộng trong production source set; tất cả đều dùng **FIELD access** (không có `@Access` override, `@Id` đặt trên field).
- **Tổng số đã reconciled = 10 = 7 (entity mapping) + 3 (dead schema, EXCEPTION).** Số liệu này khớp với danh sách canonical ở mục 3.

## 2. Trạng thái xác minh

| Trạng thái | Ý nghĩa |
|---|---|
| `VERIFIED_POSTGRES` | Đã được kiểm tra trên PostgreSQL thật qua integration test đã chạy thành công (CP-3.3.5/7/8/9/10/11). Không áp dụng cho `Asset.specifications` vì runtime test chưa được thực thi. |
| `STATIC_VERIFIED` | Mapping và schema đã đối chiếu tĩnh (entity + migration), nhưng chưa có integration test PostgreSQL cho mapping này. |
| `UNVERIFIED` | Chưa đủ bằng chứng để xác nhận mapping đúng. |
| `EXCEPTION` | Có lý do kỹ thuật cụ thể khiến quy tắc chung không áp dụng. Ghi rõ lý do. |

## 3. Bảng inventory

| # | Table.column | SQL type | Entity.field | Java type | `@JdbcTypeCode` | Write path (production) | Status | Checkpoint / Evidence | Finding |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `service_catalog.form_fields` | JSONB | `ServiceCatalogItem.formFields` | `String` | `SqlTypes.JSON` ✅ | `ServiceCatalogInitializer.repository.saveAll(services)` (startup) | `VERIFIED_POSTGRES` | CP-3.3.10 | OK |
| 2 | `service_requests.form_data` | JSONB | `ServiceRequest.formData` | `String` | `SqlTypes.JSON` ✅ | `ServiceRequestService.createRequest/updateRequest` | `VERIFIED_POSTGRES` | CP-3.3.9 | OK |
| 3 | `change_requests.risk_factors` | JSONB | `ChangeRequest.riskFactors` | `String` | `SqlTypes.JSON` ✅ | `ChangeService` create/update | `VERIFIED_POSTGRES` | CP-3.3.8 | OK |
| 4 | `change_timeline.event_data` | JSONB | `ChangeTimeline.eventData` | `String` | `SqlTypes.JSON` ✅ | `ChangeTimelineService.recordEvent` (CREATE + UPDATE) | `VERIFIED_POSTGRES` | CP-3.3.7 | OK |
| 5 | `assets.specifications` | JSONB | `Asset.specifications` | `Map<String, Object>` | `SqlTypes.JSON` ✅ | `AssetController` create/update (REST) + `AssetService` | `STATIC_VERIFIED` | CP-3.3.14 — test authored + compile OK, runtime NOT executed | Test authored; runtime pending. Xem 4.5. |
| 6 | `report_configs.config` | JSONB | `ReportConfig.config` | `String` | `SqlTypes.JSON` ✅ | `ReportService.createReport` + `updateReport` (CREATE + UPDATE) | `VERIFIED_POSTGRES` | CP-3.3.11 | OK |
| 7 | `ai_message.metadata` | JSONB | `AiMessage.metadata` | `String` | `SqlTypes.JSON` ✅ | `AiMessage` service paths + manifest appender | `VERIFIED_POSTGRES` | CP-3.3.5 | OK |
| 8 | `saved_reports.query_params` | JSONB | (none) | — | — | Không có | `EXCEPTION` | CP-3.3.12 + CP-3.3.13 audit | Dead schema — xem mục 5 |
| 9 | `saved_reports.filters` | JSONB | (none) | — | — | Không có | `EXCEPTION` | CP-3.3.12 + CP-3.3.13 audit | Dead schema — xem mục 5 |
| 10 | `report_exports.parameters` | JSONB | (none) | — | — | Không có | `EXCEPTION` | CP-3.3.12 + CP-3.3.13 audit | Dead schema — xem mục 5 |

**Tổng kết bảng trên:** 10 dòng (canonical), trong đó 7 cột có entity mapping, 3 cột là dead schema. Số liệu này đã được reconcile từ danh sách canonical chứ không lấy từ số cũ.

## 4. Chi tiết từng mapping

### 4.1 `service_catalog.form_fields`
- **Migration:** `V48__create_service_requests.sql` line 36 — `form_fields JSONB`
- **Entity field:** `ServiceCatalogItem.formFields` (String)
- **Annotation:**
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "form_fields", columnDefinition = "jsonb")
  private String formFields;
  ```
- **Production write path:** `ServiceCatalogInitializer` (line 205) `repository.saveAll(services)`. Trong seed path, `createService()` không gọi `setFormFields(...)` nên tất cả rows được seed với `formFields = null`.
- **Verified by:** `ServiceCatalogItemPostgresJsonbIntegrationTest` (CP-3.3.10) — 5/5 PASS, 4/4 baseline errors trước fix.

### 4.2 `service_requests.form_data`
- **Migration:** `V48__create_service_requests.sql` line 118 — `form_data JSONB`
- **Entity field:** `ServiceRequest.formData` (String)
- **Annotation:**
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "form_data", columnDefinition = "jsonb")
  private String formData;
  ```
- **Production write path:** `ServiceRequestService` create + update.
- **Verified by:** `ServiceRequestPostgresJsonbIntegrationTest` (CP-3.3.9).

### 4.3 `change_requests.risk_factors`
- **Migration:** `V50__create_changes.sql` line 38 — `risk_factors JSONB`
- **Entity field:** `ChangeRequest.riskFactors` (String)
- **Annotation:**
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "risk_factors", columnDefinition = "jsonb")
  private String riskFactors;
  ```
- **Production write path:** `ChangeService` create + update.
- **Verified by:** `ChangeRequestPostgresJsonbIntegrationTest` (CP-3.3.8).

### 4.4 `change_timeline.event_data`
- **Migration:** `V50__create_changes.sql` line 211 — `event_data JSONB`
- **Entity field:** `ChangeTimeline.eventData` (String)
- **Annotation:**
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "event_data", columnDefinition = "jsonb")
  private String eventData;
  ```
- **Production write path:** `ChangeTimelineService.recordEvent` (cả INSERT và UPDATE paths).
- **Verified by:** `ChangeTimelinePostgresJsonbIntegrationTest` (CP-3.3.7).

### 4.5 `assets.specifications`
- **Migration:** `V51__create_assets.sql` line 38 — `specifications JSONB`
- **Entity field:** `Asset.specifications` (`Map<String, Object>`)
- **Annotation:**
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> specifications;
  ```
- **Production write path:** `AssetController` (REST) create + update; `AssetService.createAsset/updateAsset`.
- **Evidence available:**
  - Test authored: `AssetPostgresJsonbIntegrationTest` (CP-3.3.14) — 5 nested test groups.
  - Test compiled: ✅ BUILD SUCCESS (103 files).
  - Test runtime: ⏭️ **SKIPPED** — `@EnabledIfEnvironmentVariable(named = "IT_TEST_PG", matches = "true")` gates execution; `IT_TEST_PG` not set → class skipped by JUnit. Runtime has not been executed.
  - Annotation structure: `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")` — same pattern as 6 confirmed mappings.
- **Status rationale:** `STATIC_VERIFIED`. The test is authored and compiles, which provides strong static evidence (correct annotation pattern, correct service paths, correct flush/clear/assert strategy). However, `VERIFIED_POSTGRES` requires actual runtime execution on PostgreSQL, which has not occurred. The 6 other mappings have both static evidence AND runtime execution.
- **Runtime verification required to reach `VERIFIED_POSTGRES`:**
  ```
  IT_TEST_PG=true
  IT_TEST_PG_URL=jdbc:postgresql://localhost:5432/itticketing_cp3314_test
  IT_TEST_PG_USERNAME=postgres
  IT_TEST_PG_PASSWORD=<password>
  CREATE DATABASE itticketing_cp3314_test;
  mvn -o test -Dtest=AssetPostgresJsonbIntegrationTest
  ```

### 4.6 `report_configs.config`
- **Migration:** `V53__create_reports.sql` line 26 — `config JSONB`
- **Entity field:** `ReportConfig.config` (String)
- **Annotation:**
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String config;
  ```
- **Production write path:** `ReportService.createReport` + `updateReport` (cả INSERT và UPDATE). V53 INSERT seed 5 default report templates.
- **Verified by:** `ReportConfigPostgresJsonbIntegrationTest` (CP-3.3.11) — 7/7 PASS, 4/7 baseline errors trước fix.

### 4.7 `ai_message.metadata`
- **Migration:** `V71__create_ai_session.sql` line 46 — `metadata JSONB`
- **Entity field:** `AiMessage.metadata` (String)
- **Annotation:**
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private String metadata;
  ```
- **Production write path:** AI session + manifest appender (AI/RAG).
- **Verified by:** `AiMessagePostgresJsonbIntegrationTest` (CP-3.3.5).

## 5. Dead schema — cột JSONB không có entity mapping

Ba cột JSONB sau tồn tại trong Flyway migration nhưng **không có JPA entity mapping** và **không có Java code** nào đọc/ghi (xác minh qua grep toàn project):

| Column | Source | Java code reference |
|---|---|---|
| `saved_reports.query_params` | V53 line 67 | (none) |
| `saved_reports.filters` | V53 line 70 | (none) |
| `report_exports.parameters` | V53 line 104 | (none) |

**Bằng chứng:**
- `SavedReport.java` không tồn tại (Glob `**/SavedReport.java` → 0 file).
- `ReportExport.java` không tồn tại (Glob `**/ReportExport.java` → 0 file).
- Grep `INSERT INTO saved_reports` / `INSERT INTO report_exports` / `UPDATE saved_reports` / `UPDATE report_exports` → 0 matches.
- `ReportExportService` chỉ tạo CSV/Excel bytes trong memory; không touch DB.

**Phạm vi audit và giới hạn bằng chứng (CP-3.3.15 / CP-3.3.16):**
- Phạm vi đã kiểm tra: source Java trong `com.example.ticketing` và toàn bộ Flyway migration trong `src/main/resources/db/migration`.
- **Không** nằm trong phạm vi đã kiểm tra: BI tools kết nối trực tiếp PostgreSQL, DBA scripts, cron jobs, backup/restore procedures, custom front-end / middleware phát sinh ad-hoc SQL, third-party API consumers.
- Việc không tìm thấy controller / entity / service / repository nội bộ **không chứng minh** rằng các bảng này không có consumer bên ngoài codebase.

**Trạng thái áp dụng (CP-3.3.16-S):**

| Trạng thái | Định nghĩa |
|---|---|
| `NO_INTERNAL_REFERENCE_FOUND` | Audit không tìm thấy đường đọc / ghi nội bộ trong phạm vi đã kiểm tra (source Java + Flyway migrations). |
| `EXTERNAL_DEPENDENCY_UNKNOWN` | Đường nội bộ không tồn tại trong phạm vi đã kiểm tra, nhưng consumer bên ngoài (BI tool, DBA script, cron job, backup/restore, third-party) chưa được xác minh từ codebase. |
| `CANDIDATE_FOR_DEPRECATION` | Đối tượng được đánh dấu là ứng viên để xem xét ngừng hỗ trợ trong tương lai. Đây là marker mở câu hỏi deprecation, **không phải** quyết định xóa và **không phải** cam kết sẽ có migration. |
| `KEEP_PENDING_CONFIRMATION` | Trạng thái hành động an toàn: giữ nguyên schema cho đến khi `EXTERNAL_DEPENDENCY_UNKNOWN` được giải quyết bằng bằng chứng ghi nhận. Mọi thao tác xóa bị chặn bởi điều kiện tiên quyết ở mục 5.9. |

**Áp dụng vào 2 bảng / 3 cột:**

| Đối tượng | Trạng thái |
|---|---|
| `saved_reports` (bảng) | `NO_INTERNAL_REFERENCE_FOUND` + `EXTERNAL_DEPENDENCY_UNKNOWN` → `CANDIDATE_FOR_DEPRECATION` AND `KEEP_PENDING_CONFIRMATION` |
| `report_exports` (bảng) | `NO_INTERNAL_REFERENCE_FOUND` + `EXTERNAL_DEPENDENCY_UNKNOWN` → `CANDIDATE_FOR_DEPRECATION` AND `KEEP_PENDING_CONFIRMATION` |
| `saved_reports.query_params` | thừa hưởng từ bảng: `NO_INTERNAL_REFERENCE_FOUND` + `EXTERNAL_DEPENDENCY_UNKNOWN` |
| `saved_reports.filters` | thừa hưởng từ bảng: `NO_INTERNAL_REFERENCE_FOUND` + `EXTERNAL_DEPENDENCY_UNKNOWN` |
| `report_exports.parameters` | thừa hưởng từ bảng: `NO_INTERNAL_REFERENCE_FOUND` + `EXTERNAL_DEPENDENCY_UNKNOWN` |

Hai nhãn `CANDIDATE_FOR_DEPRECATION` và `KEEP_PENDING_CONFIRMATION` **không mâu thuẫn**: nhãn thứ nhất ghi nhận câu hỏi deprecation đang mở; nhãn thứ hai ghi nhận rằng schema phải được giữ nguyên cho đến khi câu hỏi đó được đóng.

**Nhãn bị cấm (CP-3.3.16):** các nhãn sau **không được phép** xuất hiện trong documentation, code comment, commit message hay bất kỳ tài liệu nào tham chiếu CP-3.3.15 trong khi investigation còn mở:
`SAFE_TO_DROP`, `UNUSED_IN_ALL_ENVIRONMENTS`, `REMOVABLE`, `OBSOLETE`, `READY_FOR_REMOVAL`, và mọi cách diễn đạt tương đương ngụ ý rằng việc xóa đã được phê duyệt. Quyết định xóa chỉ có thể đến sau khi đủ điều kiện tiên quyết ở mục 5.9 và được owner phê duyệt một migration version cụ thể.

### 5.9 Điều kiện tiên quyết trước mọi DROP TABLE / DROP COLUMN

CP-3.3.15 / CP-3.3.16-S **không** tạo và **không** cho phép tạo migration DROP TABLE / DROP COLUMN trong checkpoint hiện tại. Trước khi bất kỳ checkpoint tương lai nào có thể đề xuất / viết / merge migration xóa `saved_reports` hoặc `report_exports` (hoặc một trong ba cột JSONB `query_params`, `filters`, `parameters`), cần hoàn tất và lưu bằng chứng cho đủ 7 nhóm kiểm tra sau:

1. **Xác minh consumer bên ngoài với database / system owner.** BI tool, công cụ phân tích, integration bên thứ ba — từng cái một — đã được rà soát với người chịu trách nhiệm; bằng chứng "no consumer" được lưu cùng checkpoint.
2. **Review API contract và consumer tích hợp liên quan.** Mọi endpoint dưới `/api/reports/*` (hiện tại và tương lai) đã được đọc lại để xác nhận không DTO / response shape nào phụ thuộc dữ liệu từ `saved_reports` hoặc `report_exports`.
3. **Kiểm tra BI tools, DBA scripts, cron jobs và quy trình vận hành.** Không có script / job nào (trong hoặc ngoài repository) đang đọc hoặc ghi hai bảng này. Bằng chứng được lưu cùng checkpoint.
4. **Kiểm tra backup / restore và khảo sát khả năng khôi phục dữ liệu.** Quy trình backup / restore hiện hành đã được xác nhận không snapshot nguyên bảng hai bảng này, hoặc nếu có thì chủ sở hữu quy trình đã đồng ý với tác động. Khả năng khôi phục dữ liệu từ bản backup cũ (để rollback nếu cần) đã được đánh giá.
5. **Đánh giá tác động FK `saved_reports.report_config_id` → `report_configs.id`.** FK này (V53) nhắm vào `report_configs.id` — bảng đang được sử dụng bởi `ReportService` / `ReportConfigRepository` / `ReportController` và có 5 V53 seed rows mà `ReportConfigPostgresJsonbIntegrationTest` (CP-3.3.11) kiểm tra. Xóa `saved_reports` mà không đánh giá FK sẽ làm gãy `report_configs` hoặc test CP-3.3.11.
6. **Xác minh toàn bộ migration hiện có và trạng thái triển khai thực tế.** Rà soát V1–V71 (đặc biệt V53 và mọi migration sau) để chắc chắn không migration nào vô tình tái tạo dependency trên `saved_reports` / `report_exports`. Trạng thái triển khai thực tế (dev / staging / prod) của các migration đó đã được xác nhận.
7. **Có quyết định KEEP / DEPRECATE / DROP được người chịu trách nhiệm phê duyệt bằng văn bản**, kèm version migration mới (nếu DROP) hoặc deprecation window (nếu DEPRECATE).

Đây là preset phá hủy dữ liệu. Bỏ qua bất kỳ mục nào trong 7 mục trên là không tương thích với quy tắc CP-3.3.16 và với taxonomy ở §5.8.

**Các hành động không được đề xuất trong khi 7 điều kiện còn mở (CP-3.3.16-S):**
- Thêm cột `deprecated_at` vào `saved_reports` hoặc `report_exports` khi chưa xác định được consumer cần nó.
- Tạo view chuyển tiếp (compatibility view) cho "consumer tương lai" chưa được đặt tên.
- Đổi kiểu cột `JSONB → TEXT` (hoặc ngược lại) trên ba cột JSONB của hai bảng với lý do "không có entity dùng".
- Đánh dấu `@Deprecated` trong JavaDoc hay comment trên bất kỳ entity / service / repository nào với lý do "internal references are absent".
- Viết migration DROP TABLE / DROP COLUMN cho hai bảng / ba cột nói trên.

## 6. Các cột TEXT thường bị nhầm là JSON

Một số cột TEXT có comment chỉ ra rằng content là JSON-formatted nhưng column type thực sự là TEXT. Không thuộc phạm vi rule JSONB:

| Column | SQL type | Note |
|---|---|---|
| `ticket_timeline.metadata` | TEXT | `TicketTimeline.metadata` với `@Column(columnDefinition = "TEXT")` — code và schema khớp |
| `known_errors.symptoms` | TEXT | Comment "JSON array of common symptoms" nhưng schema là TEXT |
| `known_errors.affected_systems` | TEXT | Comment "JSON array of affected systems" nhưng schema là TEXT |

Các cột này không bị ảnh hưởng bởi rule JSONB mapping vì schema không phải JSONB.

## 7. Architecture Test Audit (CP-3.3.13)

### 7.1 Coverage Assessment

`JsonbMappingArchitectureTest` (CP-3.3.12) kiểm tra tất cả entity trong `com.example.ticketing` package tree bằng classpath scan. Kết quả audit:

| Khía cạnh | Kết quả |
|---|---|
| Classpath scan bao phủ entity | ✅ 42 entity trong production source set được quét qua `discoverEntityClassNames()` — đi qua `file://` protocol trên `target/classes` và `target/test-classes`. Không phát hiện vấn đề. |
| FIELD vs PROPERTY access | ✅ 42/42 entity đều dùng FIELD access (không có `@Access` override, `@Id` ở field). `hasJsonbColumn(Field)` và `hasJsonTypeMapping(Field)` hoạt động đúng cho FIELD access strategy. |
| Annotation trên field vs getter | ✅ Không có entity dùng PROPERTY access, không có `@Column`/`@JdbcTypeCode` trên getter. Tất cả annotation đặt trên field. |
| Converter/custom Hibernate type | ⚠️ **Giới hạn ghi nhận:** Test chỉ kiểm tra `@JdbcTypeCode(SqlTypes.JSON)`. Nếu dự án sử dụng custom `org.hibernate.type.descriptor.jdbc.JdbcType` hoặc JPA `AttributeConverter<..., ?>`, test sẽ không phát hiện. Hiện tại dự án **không sử dụng** converter hoặc custom type cho JSON columns, nên giới hạn này không ảnh hưởng. Ghi nhận để mở rộng nếu tương lai cần. |
| Negative fixture dùng cùng logic | ✅ `JsonbMappingViolationFixture` là class `String` field với `@Column(columnDefinition = "jsonb")` nhưng không có `@JdbcTypeCode`. Test `violationFixtureClassIsDetected()` dùng `hasJsonbColumn()` + `hasJsonTypeMapping()` — cùng logic với entity scan. Fixture nằm trong `src/test/java` (không có production JAR). |
| Error message đủ chi tiết | ✅ `formatViolation()` trả về entity name, field name, column name, và columnDefinition. Tuy nhiên, error message gốc (trong `fail()`) dùng `String.join` với `\n`, nên nếu nhiều violation thì error sẽ liệt kê đầy đủ. |

### 7.2 Giới hạn ghi nhận

1. **Dead-schema columns không được test phát hiện:** `saved_reports.query_params`, `saved_reports.filters`, `report_exports.parameters` không có entity tương ứng, nên không nằm trong phạm vi classpath scan. Architecture test chỉ bảo vệ entity mapping, không bảo vệ schema rác.
2. **Property access:** Nếu entity tương lai dùng PROPERTY access (annotation trên getter), `hasJsonbColumn(Field)` + `hasJsonTypeMapping(Field)` sẽ trả `false` cho các field private không có annotation. Test hiện tại không hỗ trợ property access — phải mở rộng nếu dự án chuyển sang getter annotation.
3. **Custom JdbcType:** Test không kiểm tra custom `JdbcType` implementation. Nếu dự án sử dụng `org.hibernate.type.descriptor.jdbc.JdbcType` (không phải `@JdbcTypeCode`), test sẽ không phát hiện. Hiện tại không ảnh hưởng.

### 7.3 Positive test evidence

- `everyJsonbEntityFieldHasJsonTypeMapping()` — quét 42 entity, 0 violation → PASS.
- `knownGoodJsonbMappingsAreStillAnnotated()` — kiểm tra 7 mapping cố định (theo tên class + field), 0 failure → PASS.
- `violationFixtureClassIsDetected()` — fixture ném violation đúng → PASS.

## 8. Lịch sử

| Checkpoint | Phát hiện | Trạng thái |
|---|---|---|
| CP-3.3.5 | `AiMessage.metadata` thiếu `@JdbcTypeCode(SqlTypes.JSON)` → SQLSTATE 42804 trên INSERT/UPDATE | Fixed + verified |
| CP-3.3.7 | `ChangeTimeline.eventData` thiếu `@JdbcTypeCode(SqlTypes.JSON)` | Fixed + verified |
| CP-3.3.8 | `ChangeRequest.riskFactors` thiếu `@JdbcTypeCode(SqlTypes.JSON)` | Fixed + verified |
| CP-3.3.9 | `ServiceRequest.formData` thiếu `@JdbcTypeCode(SqlTypes.JSON)` | Fixed + verified |
| CP-3.3.10 | `ServiceCatalogItem.formFields` thiếu `@JdbcTypeCode(SqlTypes.JSON)` | Fixed + verified |
| CP-3.3.11 | `ReportConfig.config` thiếu `@JdbcTypeCode(SqlTypes.JSON)` | Fixed + verified |
| CP-3.3.12 | Inventory + static regression test | Documented |
| CP-3.3.13 | Reconciliation audit: discovered previous count was 9, actual is **10** JSONB columns. No plain `json` columns exist. Architecture test coverage verified. Dead-schema 3 columns confirmed (no Java write/read path found). `@DisplayName` updated to CP-3.3.13. | Documented |
| CP-3.3.14 | Tạo `AssetPostgresJsonbIntegrationTest` xác minh `Asset.specifications` (`Map<String, Object>`) round-trip qua PostgreSQL JSONB. Test cover: structured Map round-trip, NULL handling, UPDATE path, malformed JSONB rejection, catalog check. | Documented |
| CP-3.3.14-R | Runtime verification: chạy test với `IT_TEST_PG=true`. Test bị skip do env var không được đặt. Safety gates hoạt động đúng. | Runtime not executed |
| CP-3.3.14-R2 | Reconciliation: phát hiện `assets.specifications` được ghi là `VERIFIED_POSTGRES` nhưng runtime chưa thực sự chạy. Điều chỉnh xuống `STATIC_VERIFIED`. Giữ nguyên 6 mapping khác. Cập nhật tóm tắt, bảng, section 4.5 và history. | Documented |
| CP-3.3.15 | Dead-schema audit: xác nhận `saved_reports`, `report_exports` và ba cột JSONB `saved_reports.query_params`, `saved_reports.filters`, `report_exports.parameters` thuộc nhóm `NO_INTERNAL_REFERENCE_FOUND`. Kết luận không tạo DROP TABLE plan trong checkpoint này. | Documented |
| CP-3.3.16 | Lần đầu chuẩn hóa kết luận CP-3.3.15 vào tài liệu. Do search root lệch một cấp so với Git repo root, checkpoint tạo file mới ở `e:\IT-Ticketing-System\docs\architecture\jsonb-mapping-inventory.md` thay vì nhận diện file canonical đã có trong repo. Hai file cùng tên tồn tại ở hai vị trí. | Documented (file ngoài repo, không trong cây tracked) |
| CP-3.3.16-R | Read-only audit: phát hiện sự tồn tại của file canonical (CP-3.3.14-R2) trong Git repo tại `docs/architecture/jsonb-mapping-inventory.md`, xác nhận cả hai file đều untracked trong Git (không có commit nào chứa path này). Ghi nhận mâu thuẫn `NEW` của CP-3.3.16 và yêu cầu checkpoint tiếp theo hợp nhất. | Audit (read-only) |
| CP-3.3.16-S | Hợp nhất: cập nhật file canonical trong repo với taxonomy bảo thủ từ CP-3.3.15 / CP-3.3.16 (`NO_INTERNAL_REFERENCE_FOUND`, `EXTERNAL_DEPENDENCY_UNKNOWN`, `CANDIDATE_FOR_DEPRECATION`, `KEEP_PENDING_CONFIRMATION`), danh sách nhãn bị cấm, và 7 điều kiện tiên quyết trước mọi DROP TABLE / DROP COLUMN. Không thay đổi mapping status của bất kỳ mapping nào; `Asset.specifications` vẫn là `STATIC_VERIFIED`. File ngoài repo giữ nguyên không động đến. | Documented (canonical, file này) |
