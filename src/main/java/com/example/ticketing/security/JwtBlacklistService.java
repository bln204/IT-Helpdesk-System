package com.example.ticketing.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
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
    
    // Set of usernames whose all tokens should be rejected
    // When a user is deactivated, all their tokens are invalid
    private final Set<String> revokedUsernames = ConcurrentHashMap.newKeySet();
    
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
     * Revoke all tokens for a specific user.
     * Call this when a user is deactivated or needs to be logged out everywhere.
     * 
     * @param username The username whose tokens should be revoked
     */
    public void revokeAllUserTokens(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        revokedUsernames.add(username);
    }
    
    /**
     * Check if a username has been revoked (all their tokens are invalid).
     * 
     * @param username The username to check
     * @return true if the user's tokens should be rejected
     */
    public boolean isUserRevoked(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return revokedUsernames.contains(username);
    }
    
    /**
     * Restore a user's tokens (remove from revoked list).
     * Call this when a user is reactivated.
     * 
     * @param username The username to restore
     */
    public void restoreUserTokens(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        revokedUsernames.remove(username);
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
     * Get count of revoked users (for monitoring).
     */
    public int getRevokedUsersCount() {
        return revokedUsernames.size();
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
