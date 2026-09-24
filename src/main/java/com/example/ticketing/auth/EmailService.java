package com.example.ticketing.auth;

import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.ticketing.auth.ProfileChange.ChangeSet;
import com.example.ticketing.ticket.Ticket;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Service gửi email notifications.
 * Sử dụng Spring Mail để gửi email.
 */
@Service
public class EmailService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    
    private final JavaMailSender mailSender;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    
    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;
    
    @Value("${spring.mail.username:noreply@example.com}")
    private String fromEmail;
    
    @Value("${app.company.name:IT Support}")
    private String companyName;
    
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    // ============================================================
    // TICKET EMAIL NOTIFICATIONS
    // ============================================================
    
    /**
     * Gửi email khi ticket được tạo mới.
     */
    @Async
    public void sendTicketCreatedEmail(Ticket ticket, String recipientEmail, String recipientName) {
        if (!emailEnabled) return;
        
        String subject = "[" + ticket.getTicketNumber() + "] Ticket mới đã được tạo";
        String body = buildTicketCreatedEmail(ticket, recipientName);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email khi ticket được gán cho user.
     */
    @Async
    public void sendTicketAssignedEmail(Ticket ticket, String recipientEmail, String recipientName, String assignedBy) {
        if (!emailEnabled) return;
        
        String subject = "[" + ticket.getTicketNumber() + "] Ticket đã được giao cho bạn";
        String body = buildTicketAssignedEmail(ticket, recipientName, assignedBy);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email khi ticket status thay đổi.
     */
    @Async
    public void sendTicketStatusChangedEmail(Ticket ticket, String recipientEmail, String recipientName,
                                            String oldStatus, String newStatus, String changedBy) {
        if (!emailEnabled) return;
        
        String subject = "[" + ticket.getTicketNumber() + "] Ticket status đã thay đổi";
        String body = buildTicketStatusChangedEmail(ticket, recipientName, oldStatus, newStatus, changedBy);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email khi ticket được resolved.
     */
    @Async
    public void sendTicketResolvedEmail(Ticket ticket, String recipientEmail, String recipientName, String resolvedBy) {
        if (!emailEnabled) return;
        
        String subject = "[" + ticket.getTicketNumber() + "] Ticket đã được giải quyết";
        String body = buildTicketResolvedEmail(ticket, recipientName, resolvedBy);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email khi ticket được closed.
     */
    @Async
    public void sendTicketClosedEmail(Ticket ticket, String recipientEmail, String recipientName, String closedBy) {
        if (!emailEnabled) return;
        
        String subject = "[" + ticket.getTicketNumber() + "] Ticket đã được đóng";
        String body = buildTicketClosedEmail(ticket, recipientName, closedBy);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email khi IT cần thông tin từ user.
     */
    @Async
    public void sendWaitingForInfoEmail(Ticket ticket, String recipientEmail, String recipientName, String message) {
        if (!emailEnabled) return;
        
        String subject = "[" + ticket.getTicketNumber() + "] Cần thông tin từ bạn";
        String body = buildWaitingForInfoEmail(ticket, recipientName, message);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email khi ticket được escalated.
     */
    @Async
    public void sendEscalatedEmail(Ticket ticket, String recipientEmail, String recipientName, 
                                   String escalatedBy, String reason) {
        if (!emailEnabled) return;
        
        String subject = "[URGENT] [" + ticket.getTicketNumber() + "] Ticket đã được escalate";
        String body = buildEscalatedEmail(ticket, recipientName, escalatedBy, reason);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    // ============================================================
    // SLA EMAIL NOTIFICATIONS
    // ============================================================
    
    /**
     * Gửi email SLA warning.
     */
    @Async
    public void sendSlaWarningEmail(Ticket ticket, String recipientEmail, String recipientName, String slaType) {
        if (!emailEnabled) return;
        
        String subject = "[SLA WARNING] [" + ticket.getTicketNumber() + "] " + slaType + " sắp hết hạn";
        String body = buildSlaWarningEmail(ticket, recipientName, slaType);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email SLA breach.
     */
    @Async
    public void sendSlaBreachedEmail(Ticket ticket, String recipientEmail, String recipientName, String slaType) {
        if (!emailEnabled) return;
        
        String subject = "[SLA BREACH] [" + ticket.getTicketNumber() + "] " + slaType + " đã bị vi phạm";
        String body = buildSlaBreachedEmail(ticket, recipientName, slaType);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    // ============================================================
    // ACCOUNT EMAIL NOTIFICATIONS
    // ============================================================
    
    /**
     * Gửi email thông báo profile changed.
     */
    @Async
    public void sendProfileChangeNotification(String recipientEmail, String displayName,
                                              String actorUsername, String actorRole,
                                              ChangeSet changes) {
        if (!emailEnabled) return;
        
        String subject = "Thong tin tai khoan da duoc thay doi";
        String body = buildProfileChangeEmail(displayName, actorUsername, actorRole, changes);
        
        sendEmail(recipientEmail, subject, body);
    }
    
    // ============================================================
    // EMAIL TEMPLATES
    // ============================================================
    
    private String buildTicketCreatedEmail(Ticket ticket, String recipientName) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "Ticket moi da duoc tao",
            "Xin chao " + recipientName + ",",
            "<p>Ticket moi da duoc tao trong he thong.</p>",
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Doi tuong:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getPriority() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Trang thai:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getStatus() + "</td></tr>" +
            "</table>",
            "<p><a href='" + ticketUrl + "' style='background:#007bff; color:white; padding:10px 20px; text-decoration:none; border-radius:4px;'>Xem chi tiet</a></p>",
            "Ticket da duoc tao thanh cong."
        );
    }
    
    private String buildTicketAssignedEmail(Ticket ticket, String recipientName, String assignedBy) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "Ban duoc gan cho ticket moi",
            "Xin chao " + recipientName + ",",
            "<p>Ban da duoc gan vao ticket moi boi " + assignedBy + ".</p>",
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Do uu tien:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getPriority() + "</td></tr>" +
            "</table>",
            "<p><a href='" + ticketUrl + "' style='background:#007bff; color:white; padding:10px 20px; text-decoration:none; border-radius:4px;'>Xem va xu ly ticket</a></p>",
            "Vui long kiem tra va bat dau xu ly ticket nay som nhat co the."
        );
    }
    
    private String buildTicketStatusChangedEmail(Ticket ticket, String recipientName, 
                                                String oldStatus, String newStatus, String changedBy) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "Trang thai ticket da thay doi",
            "Xin chao " + recipientName + ",",
            "<p>Trang thai ticket da duoc thay doi boi " + changedBy + ".</p>",
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Trang thai cu:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + oldStatus + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Trang thai moi:</b></td><td style='padding:8px; border:1px solid #ddd;'><b style='color:#28a745;'>" + newStatus + "</b></td></tr>" +
            "</table>",
            "<p><a href='" + ticketUrl + "' style='background:#007bff; color:white; padding:10px 20px; text-decoration:none; border-radius:4px;'>Xem chi tiet</a></p>",
            null
        );
    }
    
    private String buildTicketResolvedEmail(Ticket ticket, String recipientName, String resolvedBy) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "Ticket da duoc giai quyet",
            "Xin chao " + recipientName + ",",
            "<p>Ticket cua ban da duoc giai quyet boi " + resolvedBy + ".</p>",
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "</table>",
            "<p>Vui long kiem tra neu van de da duoc giai quyet, ban co the dong ticket.</p>" +
            "<p><a href='" + ticketUrl + "' style='background:#28a745; color:white; padding:10px 20px; text-decoration:none; border-radius:4px;'>Xem chi tiet</a></p>",
            "Neu van de chua duoc giai quyet, vui long phan hoi de chung toi co the tiep tuc ho tro."
        );
    }
    
    private String buildTicketClosedEmail(Ticket ticket, String recipientName, String closedBy) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "Ticket da duoc dong",
            "Xin chao " + recipientName + ",",
            "<p>Ticket da duoc dong boi " + closedBy + ".</p>",
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "</table>",
            "<p>Cam on ban da su dung dich vu ho tro cua chung toi.</p>",
            "Neu ban can ho tro them, vui long tao ticket moi."
        );
    }
    
    private String buildWaitingForInfoEmail(Ticket ticket, String recipientName, String message) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "Can thong tin tu ban",
            "Xin chao " + recipientName + ",",
            "<p>Chung toi can ban cung cap them thong tin de xu ly ticket.</p>" +
            (message != null ? "<p><i>\"" + message + "\"</i></p>" : ""),
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "</table>",
            "<p><a href='" + ticketUrl + "' style='background:#ffc107; color:#333; padding:10px 20px; text-decoration:none; border-radius:4px;'>Cung cap thong tin</a></p>",
            "Vui long phan hoi som nhat co the de chung toi co the tiep tuc xu ly ticket."
        );
    }
    
    private String buildEscalatedEmail(Ticket ticket, String recipientName, String escalatedBy, String reason) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "[QUAN TRONG] Ticket da duoc Escalate",
            "Xin chao " + recipientName + ",",
            "<p style='color:#dc3545;'><b>Ticket nay da duoc escalate boi " + escalatedBy + ".</b></p>" +
            (reason != null ? "<p>Ly do: " + reason + "</p>" : ""),
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Doi uu tien:</b></td><td style='padding:8px; border:1px solid #ddd;'><b style='color:#dc3545;'>" + ticket.getPriority() + "</b></td></tr>" +
            "</table>",
            "<p><a href='" + ticketUrl + "' style='background:#dc3545; color:white; padding:10px 20px; text-decoration:none; border-radius:4px;'>Xem va xu ly ngay</a></p>",
            "Day la ticket uu tien cao, vui long xu ly som nhat co the."
        );
    }
    
    private String buildSlaWarningEmail(Ticket ticket, String recipientName, String slaType) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "[CANH BAO SLA] " + slaType + " sap het han",
            "Xin chao " + recipientName + ",",
            "<p style='color:#ffc107;'><b>" + slaType + " cua ticket nay sap het han!</b></p>",
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Doi uu tien:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getPriority() + "</td></tr>" +
            "</table>",
            "<p><a href='" + ticketUrl + "' style='background:#ffc107; color:#333; padding:10px 20px; text-decoration:none; border-radius:4px;'>Kiem tra ngay</a></p>",
            "Vui long xu ly ticket nay truoc khi SLA bi vi pham."
        );
    }
    
    private String buildSlaBreachedEmail(Ticket ticket, String recipientName, String slaType) {
        String ticketUrl = baseUrl + "/tickets/" + ticket.getId();
        return buildHtmlEmail(
            "[VI PHAM SLA] " + slaType + " da bi vi pham",
            "Xin chao " + recipientName + ",",
            "<p style='color:#dc3545;'><b>" + slaType + " cua ticket nay da bi vi pham!</b></p>",
            "<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>So ticket:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTicketNumber() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Tieu de:</b></td><td style='padding:8px; border:1px solid #ddd;'>" + ticket.getTitle() + "</td></tr>" +
            "<tr><td style='padding:8px; border:1px solid #ddd;'><b>Doi uu tien:</b></td><td style='padding:8px; border:1px solid #ddd;'><b style='color:#dc3545;'>" + ticket.getPriority() + "</b></td></tr>" +
            "</table>",
            "<p><a href='" + ticketUrl + "' style='background:#dc3545; color:white; padding:10px 20px; text-decoration:none; border-radius:4px;'>Xem va xu ly ngay</a></p>",
            "Vui long xu ly ticket nay ngay lap tuc."
        );
    }
    
    private String buildProfileChangeEmail(String displayName, String actorUsername, String actorRole, ChangeSet changes) {
        return buildHtmlEmail(
            "Thong tin tai khoan da duoc thay doi",
            "Xin chao " + displayName + ",",
            "<p>Thong tin tai khoan cua ban da duoc thay doi boi " + actorUsername + " (" + actorRole + ").</p>",
            changes.hasChanges() ? buildChangesTable(changes) : "",
            null,
            "Neu ban khong thuc hien thay doi nay, vui long lien he nguoi quan tri ngay lap tuc."
        );
    }
    
    private String buildChangesTable(ChangeSet changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table style='width:100%; border-collapse: collapse; margin: 20px 0;'>");
        sb.append("<tr style='background:#f8f9fa;'><th style='padding:8px; border:1px solid #ddd;'>Truong</th><th style='padding:8px; border:1px solid #ddd;'>Gia tri cu</th><th style='padding:8px; border:1px solid #ddd;'>Gia tri moi</th></tr>");
        
        for (ProfileChange change : changes.getChanges()) {
            sb.append("<tr>");
            sb.append("<td style='padding:8px; border:1px solid #ddd;'>").append(change.getFieldName()).append("</td>");
            sb.append("<td style='padding:8px; border:1px solid #ddd;'>").append(change.getOldValue() != null ? change.getOldValue() : "(trong)").append("</td>");
            sb.append("<td style='padding:8px; border:1px solid #ddd;'>").append(change.getNewValue() != null ? change.getNewValue() : "(trong)").append("</td>");
            sb.append("</tr>");
        }
        
        sb.append("</table>");
        return sb.toString();
    }
    
    // ============================================================
    // HTML EMAIL BUILDER
    // ============================================================
    
    private String buildHtmlEmail(String title, String greeting, String intro, String content, String action, String footer) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 20px; text-align: center; border-radius: 8px 8px 0 0;">
                    <h1 style="color: white; margin: 0; font-size: 24px;">%s</h1>
                </div>
                <div style="background: #ffffff; padding: 30px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 8px 8px;">
                    %s
                    %s
                    %s
                    %s
                    <hr style="border: none; border-top: 1px solid #e0e0e0; margin: 20px 0;">
                    <p style="font-size: 12px; color: #666;">
                        Email nay duoc gui tu %s. Vui long khong tra loi email nay.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                title,
                title,
                greeting,
                intro,
                content != null ? content : "",
                action != null ? action : "",
                footer != null ? "<p style='margin-top:20px;'>" + footer + "</p>" : "",
                companyName
            );
    }
    
    // ============================================================
    // EMAIL SENDER
    // ============================================================
    
    private void sendEmail(String to, String subject, String body) {
        if (!emailEnabled) {
            log.debug("Email disabled, skipping: {} to {}", subject, to);
            return;
        }
        
        if (to == null || to.isBlank()) {
            log.warn("No recipient email, skipping: {}", subject);
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // true = HTML
            
            mailSender.send(message);
            log.info("Email sent: {} to {}", subject, to);
        } catch (MessagingException e) {
            log.error("Failed to send email: {} to {} - {}", subject, to, e.getMessage());
        }
    }
}
