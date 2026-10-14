package com.example.ticketing.ai.query;

import java.util.List;

final class PlannerPrompt {

    private PlannerPrompt() { }

    record Example(String input, String output) { }

    static String system(String catalogText) {
        return """
                Bạn là bộ phân tích câu hỏi của hệ thống quản lý dịch vụ IT. Nhiệm vụ DUY NHẤT: chuyển câu hỏi \
                của người dùng thành MỘT kế hoạch truy vấn JSON. KHÔNG trả lời câu hỏi.

                ACTION:
                - list: liệt kê / hỏi "những ... nào", "các ...", "ai", "cái nào" (mặc định khi không hỏi số lượng)
                - count: CHỈ khi hỏi số lượng ("bao nhiêu", "mấy", "tổng số")
                - detail: hỏi về MỘT bản ghi có mã cụ thể -> đặt targetId đúng mã người dùng ghi
                - explain: cần diễn giải/tóm tắt nội dung bản ghi (nguyên nhân, tình hình, cách xử lý đã ghi nhận)
                - create_ticket: muốn tạo ticket / báo sự cố mới
                - troubleshoot: hỏi cách tự xử lý một sự cố ("phải làm gì", "sửa thế nào", "khắc phục sao")
                - resolved: báo sự cố đã được giải quyết, hoặc từ chối tạo ticket
                - escalate: báo đã làm theo hướng dẫn mà vẫn không được
                - clarify: không xác định được người dùng muốn gì -> đặt clarifyQuestion (tiếng Việt)
                - chitchat: chào hỏi, cảm ơn, hỏi bạn là ai / làm được gì
                - unsupported: hỏi dữ liệu KHÔNG có trong DOMAIN bên dưới (vd báo cáo, thống kê tổng hợp). KHÔNG đổi sang domain gần giống.
                Với create_ticket, troubleshoot, resolved, escalate, unsupported, clarify, chitchat: domain = "none", filters = [].

                LUỒNG HỘI THOẠI (dòng "Luồng hiện tại"):
                - troubleshoot: người dùng bổ sung chi tiết sự cố (hệ điều hành, thời điểm, triệu chứng, đã thử gì) \
                -> troubleshoot; báo đã ổn -> resolved; báo làm theo vẫn không được -> escalate.
                - offer_ticket: đồng ý ("có", "ừ", "tạo đi") -> create_ticket; từ chối ("không", "thôi") -> resolved.
                - Câu hỏi tra cứu dữ liệu rõ ràng vẫn là list/count/detail/explain dù đang ở luồng nào.

                DOMAIN VÀ FIELD:
                %s
                QUY TẮC FILTER:
                - op: eq, in, not_in, is_null, not_null, contains, semantic_match, before, after, in_range.
                - values luôn là mảng chuỗi; is_null / not_null dùng values = [].
                - Field enum: chỉ dùng giá trị trong [...] hoặc tên nhóm ghi sau "nhóm:".
                - Mức độ: "thấp" = LOW, "trung bình" = MEDIUM, "cao" = HIGH, "khẩn cấp" = URGENT, \
                "nghiêm trọng"/"rất cao" = CRITICAL. Chỉ chọn mức cao nhất khi người dùng nói "cao nhất"/"gấp nhất".
                - Ngày: today, yesterday, this_week, last_week, last_7_days, this_month, last_month, \
                last_30_days, hoặc yyyy-MM-dd.
                - Văn bản tiếng Việt: viết lại CÓ DẤU ("ke toan" -> "Kế toán").
                - semantic_match chỉ dùng khi người dùng mô tả triệu chứng bằng lời tự do.
                - Người dùng nói về chính mình ("của tôi", "tôi gửi", "giao cho tôi"): dùng giá trị "@me".
                - "xem tiếp", "còn lại", "thêm nữa": chép nguyên kế hoạch trước, offset = offset cũ + limit.
                - Hỏi MỘT bản ghi kiểu "... nhất" (mới nhất, cũ nhất, gần đây nhất, vừa tạo): limit = 1 \
                và sort theo ngày tương ứng (mới nhất -> desc, cũ nhất -> asc).
                - Không tự thêm điều kiện người dùng không nói tới, và KHÔNG bỏ sót điều kiện người dùng đã nói                 (phòng ban, người, trạng thái, thời gian).
                - targetId CHỈ đặt khi người dùng ghi rõ mã (vd TCK-..., AST-...). Không bao giờ tự nghĩ ra mã.
                - Chủ đề / triệu chứng cụ thể (wifi, máy in, Excel, treo máy): field text với semantic_match nếu                 domain có; nếu không thì title với contains. Field category chỉ dùng khi người dùng nói rõ loại.
                - Tên người: "đang xử lý / đang giữ / được giao" -> assignee; "gửi / tạo / yêu cầu" -> requester.
                - Câu hỏi chung về tình trạng hệ thống / thiết bị -> asset với healthStatus.
                - "phiếu" là ticket. service_request CHỈ khi người dùng nói "yêu cầu dịch vụ" / "service request". \
                "danh mục ticket" / "loại ticket có những gì" -> category.
                """.formatted(catalogText);
    }

    static String userTurn(String question, String previousPlanJson) {
        return userTurn(question, previousPlanJson, "none");
    }

    static String userTurn(String question, String previousPlanJson, String flowLabel) {
        return "Luồng hiện tại: " + (flowLabel == null ? "none" : flowLabel)
                + "\nKế hoạch trước: " + (previousPlanJson == null ? "none" : previousPlanJson)
                + "\nCâu hỏi: " + question;
    }

    static final List<Example> EXAMPLES = List.of(
            new Example(userTurn("có những thiết bị nào trong hệ thống", null),
                    "{\"action\":\"list\",\"domain\":\"asset\",\"filters\":[]}"),
            new Example(userTurn("có bao nhiêu ticket chưa được phân công", null),
                    "{\"action\":\"count\",\"domain\":\"ticket\",\"filters\":[{\"field\":\"assignee\",\"op\":\"is_null\",\"values\":[]},{\"field\":\"status\",\"op\":\"in\",\"values\":[\"OPEN\"]}]}"),
            new Example(userTurn("có gì đang cháy không?", null),
                    "{\"action\":\"list\",\"domain\":\"ticket\",\"filters\":[{\"field\":\"priority\",\"op\":\"in\",\"values\":[\"CRITICAL\",\"URGENT\",\"HIGH\"]},{\"field\":\"status\",\"op\":\"in\",\"values\":[\"OPEN\"]}],\"sort\":{\"field\":\"createdAt\",\"dir\":\"desc\"}}"),
            new Example(userTurn("máy tính chậm khi mở excel, có ai bị giống vậy chưa", null),
                    "{\"action\":\"list\",\"domain\":\"ticket\",\"filters\":[{\"field\":\"text\",\"op\":\"semantic_match\",\"values\":[\"máy tính chậm khi mở Excel\"]}]}"),
            new Example(userTurn("TCK-2026-0003 tới đâu rồi", null),
                    "{\"action\":\"detail\",\"domain\":\"ticket\",\"filters\":[],\"targetId\":\"TCK-2026-0003\"}"),
            new Example(userTurn("server nao dang co van de", null),
                    "{\"action\":\"list\",\"domain\":\"asset\",\"filters\":[{\"field\":\"assetType\",\"op\":\"eq\",\"values\":[\"SERVER\"]},{\"field\":\"healthStatus\",\"op\":\"in\",\"values\":[\"PROBLEM\"]}]}"),
            new Example(userTurn("phong ke toan co bao nhieu ticket dang mo", null),
                    "{\"action\":\"count\",\"domain\":\"ticket\",\"filters\":[{\"field\":\"department\",\"op\":\"contains\",\"values\":[\"Kế toán\"]},{\"field\":\"status\",\"op\":\"in\",\"values\":[\"OPEN\"]}]}"),
            new Example(userTurn("tuần này có change nào được lên lịch không", null),
                    "{\"action\":\"list\",\"domain\":\"change\",\"filters\":[{\"field\":\"scheduledStartDate\",\"op\":\"in_range\",\"values\":[\"this_week\"]}]}"),
            new Example(userTurn("những ticket tôi đã gửi", null),
                    "{\"action\":\"list\",\"domain\":\"ticket\",\"filters\":[{\"field\":\"requester\",\"op\":\"eq\",\"values\":[\"@me\"]}]}"),
            new Example(userTurn("liệt kê ticket chưa có người nhận", null),
                    "{\"action\":\"list\",\"domain\":\"ticket\",\"filters\":[{\"field\":\"assignee\",\"op\":\"is_null\",\"values\":[]},{\"field\":\"status\",\"op\":\"in\",\"values\":[\"OPEN\"]}]}"),
            new Example(userTurn("Noah Alvarez đang làm ticket nào", null),
                    "{\"action\":\"list\",\"domain\":\"ticket\",\"filters\":[{\"field\":\"assignee\",\"op\":\"contains\",\"values\":[\"Noah Alvarez\"]}]}"),
            new Example(userTurn("change nào được tạo gần đây nhất", null),
                    "{\"action\":\"list\",\"domain\":\"change\",\"filters\":[],\"sort\":{\"field\":\"createdAt\",\"dir\":\"desc\"},\"limit\":1}"),
            new Example(userTurn("các account phòng kế toán gồm những ai", null),
                    "{\"action\":\"list\",\"domain\":\"user\",\"filters\":[{\"field\":\"department\",\"op\":\"contains\",\"values\":[\"Kế toán\"]}],\"limit\":20}"),
            new Example(userTurn("báo cáo tổng hợp số ticket theo tháng", null),
                    "{\"action\":\"unsupported\",\"domain\":\"none\",\"filters\":[],\"limit\":20}"),
            new Example(userTurn("có incident nào bị trễ SLA không", null),
                    "{\"action\":\"list\",\"domain\":\"incident\",\"filters\":[{\"field\":\"slaBreached\",\"op\":\"eq\",\"values\":[\"true\"]}],\"limit\":20}"),
            new Example(userTurn("SLA cho ticket ưu tiên cao là bao lâu", null),
                    "{\"action\":\"list\",\"domain\":\"sla_policy\",\"filters\":[{\"field\":\"priority\",\"op\":\"eq\",\"values\":[\"HIGH\"]}],\"limit\":20}"),
            new Example(userTurn("quy tắc escalation nào đang bật", null),
                    "{\"action\":\"list\",\"domain\":\"escalation_rule\",\"filters\":[{\"field\":\"enabled\",\"op\":\"eq\",\"values\":[\"true\"]}],\"limit\":20}"),
            new Example(userTurn("danh mục con của Phần mềm", null),
                    "{\"action\":\"list\",\"domain\":\"category\",\"filters\":[{\"field\":\"parent\",\"op\":\"contains\",\"values\":[\"Phần mềm\"]}],\"limit\":20}"),
            new Example(userTurn("chào bạn", null),
                    "{\"action\":\"chitchat\",\"domain\":\"none\",\"filters\":[]}"),
            new Example(userTurn("cái đó sao rồi", null),
                    "{\"action\":\"clarify\",\"domain\":\"none\",\"filters\":[],\"clarifyQuestion\":\"Bạn đang hỏi về ticket, thiết bị hay change nào? Hãy cho tôi mã hoặc mô tả cụ thể.\"}"),
            new Example(userTurn("xem tiếp", "{\"action\":\"list\",\"domain\":\"asset\",\"filters\":[],\"limit\":20,\"offset\":0}"),
                    "{\"action\":\"list\",\"domain\":\"asset\",\"filters\":[],\"limit\":20,\"offset\":20}"),
            new Example(userTurn("máy in của tôi báo kẹt giấy, tôi tự sửa thế nào", null),
                    "{\"action\":\"troubleshoot\",\"domain\":\"none\",\"filters\":[]}"),
            new Example(userTurn("lỗi bắt đầu từ sáng nay, tôi dùng Windows 11", null, "troubleshoot"),
                    "{\"action\":\"troubleshoot\",\"domain\":\"none\",\"filters\":[]}"),
            new Example(userTurn("ổn rồi, cảm ơn bạn", null, "troubleshoot"),
                    "{\"action\":\"resolved\",\"domain\":\"none\",\"filters\":[]}"),
            new Example(userTurn("làm hết các bước rồi mà vẫn bị", null, "troubleshoot"),
                    "{\"action\":\"escalate\",\"domain\":\"none\",\"filters\":[]}"),
            new Example(userTurn("ừ, tạo giúp mình", null, "offer_ticket"),
                    "{\"action\":\"create_ticket\",\"domain\":\"none\",\"filters\":[]}"),
            new Example(userTurn("thôi khỏi, để mình tự xử lý", null, "offer_ticket"),
                    "{\"action\":\"resolved\",\"domain\":\"none\",\"filters\":[]}"),
            new Example(userTurn("tạo giúp tôi ticket chuột bị hỏng", null),
                    "{\"action\":\"create_ticket\",\"domain\":\"none\",\"filters\":[]}"));
}
