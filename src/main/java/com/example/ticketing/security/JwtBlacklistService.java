package com.example.ticketing.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory JWT blacklist service for token revocation.
 * 
 * In production, this should be backed by Redis for distributed deployment
 * and proper cache invalidation across multiple instances.
 */
@Service
public class JwtBlacklistService {
    
    // Map of JTI (JWT ID) -> expiration timestamp
    private final Map<String, Long> blacklistedTokens = new ConcurrentHashMap<>();
    
    // Cleanup interval: run cleanup every 5 minutes
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;
    
    public JwtBlacklistService() {
        // Start background cleanup thread
        Thread cleanupThread = new Thread(this::cleanupLoop, "jwt-blacklist-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    /**
     * Add a token to the blacklist.
     * 
     * @param jti JWT ID (unique identifier for the token)
     * @param expiresAtSeconds Token expiration time in epoch seconds
     */
    public void blacklist(String jti, long expiresAtSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        blacklistedTokens.put(jti, expiresAtSeconds);
    }
    
    /**
     * Check if a token is blacklisted.
     * 
     * @param jti JWT ID
     * @return true if the token is blacklisted
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Long expiresAt = blacklistedTokens.get(jti);
        if (expiresAt == null) {
            return false;
        }
        
        // Check if the token's expiration time has passed
        if (Instant.now().getEpochSecond() >= expiresAt) {
            // Token already expired, remove from blacklist
            blacklistedTokens.remove(jti);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get count of blacklisted tokens (for monitoring).
     */
    public int getBlacklistedCount() {
        cleanup(); // Clean up expired entries first
        return blacklistedTokens.size();
    }
    
    /**
     * Clean up expired entries from the blacklist.
     */
    private void cleanup() {
        long now = Instant.now().getEpochSecond();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
    }
    
    /**
     * Background loop to periodically clean up expired entries.
     */
    private void cleanupLoop() {
        while (true) {
            try {
                Thread.sleep(CLEANUP_INTERVAL_MS);
                cleanup();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
