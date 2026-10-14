package com.example.ticketing.ai.query;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Rejects a Pass-2 answer that cites a code or a count the DATA block does not contain. */
@Component
public class GroundingChecker {

    private static final Logger log = LoggerFactory.getLogger(GroundingChecker.class);
    // Only the system's record prefixes count as codes; "RJ-45", "SHA-256" or "Wi-Fi" are ordinary words.
    static final Pattern CODE = Pattern.compile(
            "\\b(?:TCK|KE|PRB|CHG|AST|INC|SR)-(?=[A-Z0-9-]*\\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\\b");

    /** True when every code-like token in the answer also appears in the data the LLM was given. */
    public boolean codesGrounded(String answer, String data) {
        if (answer == null) return false;
        Set<String> allowed = codes(data);
        for (String code : codes(answer)) {
            if (!allowed.contains(code)) {
                log.warn("Grounding: câu trả lời nhắc mã {} không có trong dữ liệu", code);
                return false;
            }
        }
        return true;
    }

    public boolean isGrounded(String answer, String data, String label, long totalCount, int rowCount) {
        if (!codesGrounded(answer, data)) return false;
        Matcher m = Pattern.compile("(\\d+)\\s+" + Pattern.quote(label),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(answer);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            if (n != totalCount && n != rowCount) {
                log.warn("Grounding: câu trả lời nói {} {} nhưng dữ liệu có {} (trang {})", n, label, totalCount, rowCount);
                return false;
            }
        }
        return true;
    }

    private static Set<String> codes(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        Matcher m = CODE.matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }
}
