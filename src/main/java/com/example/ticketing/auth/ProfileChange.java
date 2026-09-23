package com.example.ticketing.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single field change in a user profile.
 */
public class ProfileChange {
    private String fieldName;      // Tên trường (VD: "Họ tên", "Chức danh", "Email")
    private String oldValue;      // Giá trị trước khi thay đổi
    private String newValue;      // Giá trị sau khi thay đổi

    public ProfileChange() {}

    public ProfileChange(String fieldName, String oldValue, String newValue) {
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    /**
     * Formats the change as a readable string.
     */
    public String toDisplayString() {
        return String.format("%s: %s → %s", 
            fieldName, 
            (oldValue != null && !oldValue.isBlank()) ? oldValue : "(trống)",
            (newValue != null && !newValue.isBlank()) ? newValue : "(trống)");
    }

    /**
     * Container for multiple profile changes.
     */
    public static class ChangeSet {
        private List<ProfileChange> changes = new ArrayList<>();

        public void addChange(String fieldName, String oldValue, String newValue) {
            // Only add if there's an actual change
            String normalizedOld = normalize(oldValue);
            String normalizedNew = normalize(newValue);
            
            if (!normalizedOld.equals(normalizedNew)) {
                changes.add(new ProfileChange(fieldName, oldValue, newValue));
            }
        }

        public List<ProfileChange> getChanges() {
            return changes;
        }

        public boolean hasChanges() {
            return !changes.isEmpty();
        }

        public int getChangeCount() {
            return changes.size();
        }

        /**
         * Generates HTML content for displaying all changes.
         */
        public String toHtmlContent() {
            if (changes.isEmpty()) {
                return "<p>Không có thông tin nào được thay đổi.</p>";
            }

            StringBuilder html = new StringBuilder();
            html.append("<table style=\"width: 100%; border-collapse: collapse; margin-top: 10px;\">");
            html.append("<thead>");
            html.append("<tr style=\"background-color: #f5f5f5;\">");
            html.append("<th style=\"padding: 10px; text-align: left; border: 1px solid #ddd;\">Trường thông tin</th>");
            html.append("<th style=\"padding: 10px; text-align: left; border: 1px solid #ddd;\">Trước đây</th>");
            html.append("<th style=\"padding: 10px; text-align: left; border: 1px solid #ddd;\">Sau thay đổi</th>");
            html.append("</tr>");
            html.append("</thead>");
            html.append("<tbody>");

            for (ProfileChange change : changes) {
                String oldDisplay = formatValueForDisplay(change.getOldValue());
                String newDisplay = formatValueForDisplay(change.getNewValue());
                
                // Special styling for password field
                boolean isPasswordField = "Mật khẩu".equals(change.getFieldName()) || 
                                        "Mat khau".equals(change.getFieldName()) ||
                                        "Password".equals(change.getFieldName());
                
                String rowStyle = isPasswordField ? "background-color: #fff3e0;" : "";
                String valueStyle = isPasswordField ? 
                    "padding: 10px; border: 1px solid #ddd; color: #e65100; font-weight: bold; font-family: monospace; font-size: 14px;" :
                    "padding: 10px; border: 1px solid #ddd;";
                
                html.append("<tr style=\"").append(rowStyle).append("\">");
                html.append("<td style=\"padding: 10px; border: 1px solid #ddd; font-weight: bold;\">")
                    .append(escapeHtml(change.getFieldName())).append("</td>");
                html.append("<td style=\"").append(valueStyle).append(" color: #d32f2f;\">")
                    .append(oldDisplay).append("</td>");
                html.append("<td style=\"").append(valueStyle).append(" color: #388e3c;\">")
                    .append(newDisplay).append("</td>");
                html.append("</tr>");
            }

            html.append("</tbody>");
            html.append("</table>");
            return html.toString();
        }

        /**
         * Generates plain text content for displaying all changes.
         */
        public String toTextContent() {
            if (changes.isEmpty()) {
                return "Không có thông tin nào được thay đổi.";
            }

            StringBuilder text = new StringBuilder();
            for (int i = 0; i < changes.size(); i++) {
                ProfileChange change = changes.get(i);
                text.append(String.format("%d. %s\n", i + 1, change.toDisplayString()));
                text.append("   ---\n");
            }
            return text.toString();
        }

        private String normalize(String value) {
            return value == null ? "" : value.trim();
        }

        private String formatValueForDisplay(String value) {
            if (value == null || value.isBlank()) {
                return "<em style=\"color: #999;\">(trống)</em>";
            }
            return escapeHtml(value);
        }

        private String escapeHtml(String value) {
            if (value == null) return "";
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
        }
    }
}
