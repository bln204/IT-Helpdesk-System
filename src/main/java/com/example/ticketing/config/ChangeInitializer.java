package com.example.ticketing.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.ticketing.change.ChangeRequest;
import com.example.ticketing.change.ChangeRequestRepository;

/**
 * Change Initializer - Seed default change categories.
 */
@Configuration
public class ChangeInitializer {

    private static final Logger log = LoggerFactory.getLogger(ChangeInitializer.class);

    @Bean
    @Profile("!test")
    public CommandLineRunner initializeChanges(ChangeRequestRepository changeRepository) {
        return args -> {
            // Create some sample change categories reference (in a real app, this would be a separate table)
            log.info("Change module initialized with categories:");
            log.info("- Hardware: Server upgrade, Storage expansion, Network equipment");
            log.info("- Software: OS patching, Application deployment, Database migration");
            log.info("- Network: Firewall rules, VPN configuration, DNS changes");
            log.info("- Security: Certificate renewal, Access control updates");
            log.info("- Infrastructure: Cloud resources, Virtualization, Backup systems");
        };
    }
}
