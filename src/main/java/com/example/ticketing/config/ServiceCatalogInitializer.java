package com.example.ticketing.config;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.ticketing.servicerequest.ServiceCatalogItem;
import com.example.ticketing.servicerequest.ServiceCatalogRepository;

/**
 * Service Catalog Initializer - Seed default services.
 */
@Configuration
public class ServiceCatalogInitializer {

    private static final Logger log = LoggerFactory.getLogger(ServiceCatalogInitializer.class);

    @Bean
    @Profile("!test")
    public CommandLineRunner initializeServiceCatalog(ServiceCatalogRepository catalogRepository) {
        return args -> {
            if (catalogRepository.count() == 0) {
                log.info("Seeding default service catalog...");
                seedServiceCatalog(catalogRepository);
                log.info("Service catalog seeding completed");
            } else {
                log.info("Service catalog already exists, skipping seed");
            }
        };
    }

    private void seedServiceCatalog(ServiceCatalogRepository repository) {
        List<ServiceCatalogItem> services = Arrays.asList(
            // ==================== Access Management ====================
            createService(
                "Yêu cầu quyền truy cập phần mềm",
                "Yêu cầu cấp quyền truy cập vào các ứng dụng nội bộ",
                "Access Management",
                "🔐",
                "#6366f1",
                480,    // 8h response
                2880,   // 48h resolution
                false,  // not billable
                true    // is public
            ),
            createService(
                "Yêu cầu quyền truy cập thư mục chia sẻ",
                "Yêu cầu truy cập thư mục chia sẻ trên mạng nội bộ",
                "Access Management",
                "📁",
                "#6366f1",
                240,    // 4h response
                1440,   // 24h resolution
                false,
                true
            ),
            createService(
                "Reset mật khẩu",
                "Yêu cầu reset mật khẩu tài khoản",
                "Access Management",
                "🔑",
                "#10b981",
                60,     // 1h response
                120,    // 2h resolution
                false,
                true
            ),

            // ==================== Hardware ====================
            createService(
                "Yêu cầu máy tính mới",
                "Yêu cầu cấp phát máy tính mới cho nhân viên",
                "Hardware",
                "💻",
                "#f59e0b",
                1440,   // 24h response
                10080,  // 7 days resolution
                true,   // billable
                true
            ),
            createService(
                "Yêu cầu thiết bị ngoại vi",
                "Bàn phím, chuột, màn hình, tai nghe...",
                "Hardware",
                "🖨️",
                "#f59e0b",
                480,    // 8h response
                2880,   // 48h resolution
                true,
                true
            ),
            createService(
                "Sửa chữa thiết bị",
                "Yêu cầu sửa chữa hoặc bảo trì thiết bị",
                "Hardware",
                "🔧",
                "#ef4444",
                120,    // 2h response
                1440,   // 24h resolution
                true,
                true
            ),

            // ==================== Software ====================
            createService(
                "Cài đặt phần mềm",
                "Yêu cầu cài đặt phần mềm mới",
                "Software",
                "📦",
                "#8b5cf6",
                480,    // 8h response
                2880,   // 48h resolution
                false,
                true
            ),
            createService(
                "Yêu cầu license phần mềm",
                "Yêu cầu mua license phần mềm mới",
                "Software",
                "📜",
                "#8b5cf6",
                480,    // 8h response
                4320,   // 3 days resolution
                true,
                true
            ),

            // ==================== Network ====================
            createService(
                "Yêu cầu VPN",
                "Yêu cầu tài khoản VPN để làm việc từ xa",
                "Network",
                "🌐",
                "#06b6d4",
                240,    // 4h response
                720,    // 12h resolution
                false,
                true
            ),
            createService(
                "Sự cố mạng",
                "Báo cáo sự cố kết nối mạng",
                "Network",
                "📡",
                "#ef4444",
                30,     // 30min response
                240,    // 4h resolution
                false,
                true
            ),

            // ==================== Account ====================
            createService(
                "Tạo tài khoản email mới",
                "Yêu cầu tạo tài khoản email cho nhân viên mới",
                "Account",
                "📧",
                "#3b82f6",
                480,    // 8h response
                1440,   // 24h resolution
                false,
                true
            ),
            createService(
                "Khóa/Mở tài khoản",
                "Yêu cầu khóa hoặc mở khóa tài khoản",
                "Account",
                "🔒",
                "#3b82f6",
                60,     // 1h response
                120,    // 2h resolution
                false,
                true
            ),

            // ==================== Support ====================
            createService(
                "Hỗ trợ kỹ thuật chung",
                "Các yêu cầu hỗ trợ kỹ thuật khác",
                "Support",
                "❓",
                "#64748b",
                480,    // 8h response
                2880,   // 48h resolution
                false,
                true
            ),
            createService(
                "Đào tạo sử dụng phần mềm",
                "Yêu cầu đào tạo sử dụng phần mềm nội bộ",
                "Support",
                "📚",
                "#14b8a6",
                1440,   // 24h response
                4320,   // 3 days resolution
                false,
                true
            )
        );

        repository.saveAll(services);
        log.info("Created {} default service catalog items", services.size());
    }

    private ServiceCatalogItem createService(
            String name, String description, String category, String icon, String color,
            int responseMinutes, int resolutionMinutes, boolean billable, boolean isPublic) {
        ServiceCatalogItem service = new ServiceCatalogItem();
        service.setName(name);
        service.setDescription(description);
        service.setCategory(category);
        service.setIcon(icon);
        service.setColor(color);
        service.setResponseSlaMinutes(responseMinutes);
        service.setResolutionSlaMinutes(resolutionMinutes);
        service.setBillable(billable);
        service.setIsPublic(isPublic);
        service.setEnabled(true);
        service.setRequiresApproval(billable); // Billable items need approval
        service.setCreatedBy("system");
        return service;
    }
}
