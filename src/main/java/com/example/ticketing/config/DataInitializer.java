package com.example.ticketing.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.sla.SlaPolicy;
import com.example.ticketing.sla.SlaPolicyRepository;

/**
 * Data Initializer - Seed default SLA policies nếu chưa có.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    @Profile("!test")
    public CommandLineRunner initializeData(SlaPolicyRepository slaPolicyRepository) {
        return args -> {
            // Chỉ seed nếu chưa có SLA policies
            if (slaPolicyRepository.count() == 0) {
                log.info("Seeding default SLA policies...");
                seedSlaPolicies(slaPolicyRepository);
                log.info("SLA policies seeding completed");
            } else {
                log.info("SLA policies already exist, skipping seed");
            }
        };
    }

    private void seedSlaPolicies(SlaPolicyRepository repository) {
        List<SlaPolicy> defaultPolicies = Arrays.asList(
            // CRITICAL - 1h response, 4h resolution
            createPolicy(
                "Incident Critical SLA",
                "SLA mặc định cho Incident Critical - 1 giờ phản hồi, 4 giờ giải quyết",
                TicketPriority.CRITICAL,
                60,      // 1 giờ
                240,     // 4 giờ
                75,      // Warning 75%
                true     // isDefault
            ),
            
            // HIGH - 4h response, 8h resolution
            createPolicy(
                "Incident High SLA",
                "SLA mặc định cho Incident High - 4 giờ phản hồi, 8 giờ giải quyết",
                TicketPriority.HIGH,
                240,     // 4 giờ
                480,     // 8 giờ
                75,      // Warning 75%
                true     // isDefault
            ),
            
            // MEDIUM - 8h response, 3 days resolution
            createPolicy(
                "Incident Medium SLA",
                "SLA mặc định cho Incident Medium - 8 giờ phản hồi, 3 ngày giải quyết",
                TicketPriority.MEDIUM,
                480,     // 8 giờ
                4320,    // 3 ngày
                75,      // Warning 75%
                true     // isDefault
            ),
            
            // LOW - 1 day response, 5 days resolution
            createPolicy(
                "Incident Low SLA",
                "SLA mặc định cho Incident Low - 1 ngày phản hồi, 5 ngày giải quyết",
                TicketPriority.LOW,
                1440,    // 1 ngày
                7200,    // 5 ngày
                75,      // Warning 75%
                true     // isDefault
            ),
            
            // URGENT - 30min response, 2h resolution
            createPolicy(
                "Incident Urgent SLA",
                "SLA mặc định cho Incident Urgent - 30 phút phản hồi, 2 giờ giải quyết",
                TicketPriority.URGENT,
                30,      // 30 phút
                120,     // 2 giờ
                75,      // Warning 75%
                true     // isDefault
            ),
            
            // Service Request - sử dụng MEDIUM priority làm mặc định
            createPolicy(
                "Service Request SLA",
                "SLA mặc định cho Service Request - 8 giờ phản hồi, 3 ngày giải quyết",
                TicketPriority.MEDIUM,
                480,     // 8 giờ
                4320,    // 3 ngày
                75,      // Warning 75%
                false    // isDefault (chỉ là backup)
            )
        );

        repository.saveAll(defaultPolicies);
        log.info("Created {} default SLA policies", defaultPolicies.size());
    }

    private SlaPolicy createPolicy(String name, String description, TicketPriority priority,
                                   int responseMinutes, int resolutionMinutes,
                                   int warningThreshold, boolean isDefault) {
        SlaPolicy policy = new SlaPolicy();
        policy.setName(name);
        policy.setDescription(description);
        policy.setPriority(priority);
        policy.setResponseMinutes(responseMinutes);
        policy.setResolutionMinutes(resolutionMinutes);
        policy.setWarningThreshold(warningThreshold);
        policy.setBusinessHoursOnly(false);
        policy.setIsDefault(isDefault);
        policy.setEnabled(true);
        policy.setCreatedBy("system");
        return policy;
    }
}
