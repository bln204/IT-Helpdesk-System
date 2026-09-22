package com.example.ticketing.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Service for sending real emails via SMTP.
 * Supports both simple text emails and HTML emails.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${email.enabled:true}")
    private boolean emailEnabled;

    @Value("${email.from.name:IT Ticketing System}")
    private String fromName;

    @Value("${email.from.address:noreply@itticketing.local}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send a simple text email asynchronously.
     * 
     * @param to Recipient email address
     * @param subject Email subject
     * @param text Plain text email body
     * @return true if email was sent successfully, false otherwise
     */
    @Async
    public void sendEmail(String to, String subject, String text) {
        if (!emailEnabled) {
            log.info("Email sending is disabled. Would send to: {}, subject: {}", to, subject);
            return;
        }

        if (to == null || to.isBlank()) {
            log.warn("Cannot send email: recipient address is empty");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(formatFromAddress());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Email sent successfully to: {}, subject: {}", to, subject);
        } catch (MailException e) {
            log.error("Failed to send email to: {}, subject: {}, error: {}", to, subject, e.getMessage());
        }
    }

    /**
     * Send an HTML email asynchronously.
     * 
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML email body
     * @return true if email was sent successfully, false otherwise
     */
    @Async
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        if (!emailEnabled) {
            log.info("Email sending is disabled. Would send HTML email to: {}, subject: {}", to, subject);
            return;
        }

        if (to == null || to.isBlank()) {
            log.warn("Cannot send HTML email: recipient address is empty");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(formatFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = isHtml

            mailSender.send(message);
            log.info("HTML email sent successfully to: {}, subject: {}", to, subject);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send HTML email to: {}, subject: {}, error: {}", to, subject, e.getMessage());
        }
    }

    /**
     * Send profile change notification email.
     * 
     * @param to Recipient email address
     * @param displayName User's display name
     * @param actorUsername Who made the change
     * @param actorRole Role of the person who made the change
     * @param changes Description of what was changed
     */
    public void sendProfileChangeNotification(String to, String displayName, 
                                            String actorUsername, String actorRole, String changes) {
        String actorLabel = formatActorRole(actorRole);
        String subject = String.format("[IT Ticketing] Thông tin tài khoản của bạn đã được thay đổi");
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .changes { background-color: #fff; border: 1px solid #ddd; padding: 15px; margin: 15px 0; border-radius: 5px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                    .highlight { color: #4CAF50; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>🔔 Thông báo thay đổi thông tin tài khoản</h2>
                    </div>
                    <div class="content">
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Thông tin tài khoản của bạn đã được thay đổi bởi <span class="highlight">%s "%s"</span>.</p>
                        <div class="changes">
                            <h4>📋 Các thông tin đã được thay đổi:</h4>
                            <p>%s</p>
                        </div>
                        <p>Nếu bạn không thực hiện thay đổi này, vui lòng liên hệ với quản trị viên ngay lập tức để bảo vệ tài khoản của bạn.</p>
                    </div>
                    <div class="footer">
                        <p>Đây là email tự động từ <strong>Hệ thống IT Ticketing</strong>.</p>
                        <p>Vui lòng không trả lời email này.</p>
                    </div>
                </div>
            </body>
            </html>
            """, 
            displayName != null ? displayName : "User",
            actorLabel,
            actorUsername,
            changes != null && !changes.isBlank() ? changes : "Không xác định");

        sendHtmlEmail(to, subject, htmlContent);
    }

    /**
     * Send account approval notification email.
     */
    public void sendAccountApprovalEmail(String to, String displayName, String approverUsername) {
        String subject = String.format("[IT Ticketing] Tài khoản của bạn đã được phê duyệt");
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .success-icon { font-size: 48px; text-align: center; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>✅ Tài khoản đã được phê duyệt</h2>
                    </div>
                    <div class="content">
                        <div class="success-icon">🎉</div>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Chúc mừng! Tài khoản của bạn đã được <strong>Admin "%s"</strong> phê duyệt.</p>
                        <p>Bây giờ bạn có thể đăng nhập vào hệ thống IT Ticketing để sử dụng các dịch vụ.</p>
                    </div>
                    <div class="footer">
                        <p>Đây là email tự động từ <strong>Hệ thống IT Ticketing</strong>.</p>
                    </div>
                </div>
            </body>
            </html>
            """, 
            displayName != null ? displayName : "User",
            approverUsername);

        sendHtmlEmail(to, subject, htmlContent);
    }

    /**
     * Send account rejection notification email.
     */
    public void sendAccountRejectionEmail(String to, String displayName, String rejecterUsername, String reason) {
        String subject = String.format("[IT Ticketing] Tài khoản của bạn đã bị từ chối");
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #f44336; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .reason-box { background-color: #fff; border: 1px solid #f44336; padding: 15px; margin: 15px 0; border-radius: 5px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>❌ Tài khoản bị từ chối</h2>
                    </div>
                    <div class="content">
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Rất tiếc, tài khoản của bạn đã bị <strong>Admin "%s"</strong> từ chối.</p>
                        <div class="reason-box">
                            <h4>Lý do:</h4>
                            <p>%s</p>
                        </div>
                        <p>Vui lòng liên hệ với quản trị viên để được hỗ trợ thêm.</p>
                    </div>
                    <div class="footer">
                        <p>Đây là email tự động từ <strong>Hệ thống IT Ticketing</strong>.</p>
                    </div>
                </div>
            </body>
            </html>
            """, 
            displayName != null ? displayName : "User",
            rejecterUsername,
            reason != null ? reason : "Không có lý do được cung cấp");

        sendHtmlEmail(to, subject, htmlContent);
    }

    /**
     * Send account pending approval notification email (to admin).
     */
    public void sendPendingAccountEmailToAdmin(String adminEmail, String createdUsername, 
                                              String displayName, String requesterUsername) {
        String subject = String.format("[IT Ticketing] Yêu cầu phê duyệt tài khoản mới");
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #FF9800; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .info-box { background-color: #fff; border: 1px solid #ddd; padding: 15px; margin: 15px 0; border-radius: 5px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>📋 Yêu cầu phê duyệt tài khoản mới</h2>
                    </div>
                    <div class="content">
                        <p>Kính gửi Admin,</p>
                        <p>Trưởng phòng <strong>"%s"</strong> đã tạo một tài khoản mới và cần bạn phê duyệt.</p>
                        <div class="info-box">
                            <h4>Thông tin tài khoản:</h4>
                            <p><strong>Username:</strong> %s</p>
                            <p><strong>Họ tên:</strong> %s</p>
                            <p><strong>Người yêu cầu:</strong> %s</p>
                        </div>
                        <p>Vui lòng đăng nhập vào hệ thống để phê duyệt hoặc từ chối yêu cầu này.</p>
                    </div>
                    <div class="footer">
                        <p>Đây là email tự động từ <strong>Hệ thống IT Ticketing</strong>.</p>
                    </div>
                </div>
            </body>
            </html>
            """, 
            requesterUsername,
            createdUsername,
            displayName != null ? displayName : createdUsername,
            requesterUsername);

        sendHtmlEmail(adminEmail, subject, htmlContent);
    }

    private String formatFromAddress() {
        if (fromName != null && !fromName.isBlank()) {
            return fromName + " <" + fromAddress + ">";
        }
        return fromAddress;
    }

    private String formatActorRole(String role) {
        if (role == null) return "Người dùng";
        return switch (role) {
            case "ADMIN" -> "Admin";
            case "GIAM_DOC" -> "Giám đốc";
            case "TRUONG_PHONG" -> "Trưởng phòng";
            case "NHAN_VIEN" -> "Nhân viên";
            default -> role;
        };
    }

    /**
     * Check if email sending is enabled.
     */
    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    /**
     * Check if email is properly configured.
     */
    public boolean isEmailConfigured() {
        return smtpUsername != null && !smtpUsername.isBlank();
    }
}
