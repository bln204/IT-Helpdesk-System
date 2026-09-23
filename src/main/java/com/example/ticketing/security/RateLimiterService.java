package com.example.ticketing.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter for brute force protection.
 * Tracks failed login attempts per IP and username.
 */
@Service
public class RateLimiterService {
    
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 900; // 15 minutes
    private static final long ATTEMPT_WINDOW_SECONDS = 900; // 15 minutes
    
    // Track failed attempts: key -> (username, ip) -> AttemptData
    private final Map<String, AttemptData> attempts = new ConcurrentHashMap<>();
    // Track lockouts: key -> LockoutData
    private final Map<String, LockoutData> lockouts = new ConcurrentHashMap<>();
    
    public record AttemptData(int count, long firstAttemptTime) {}
    public record LockoutData(long lockedUntil) {}
    
    /**
     * Check if the given key (IP or username) is currently locked out.
     */
    public boolean isLockedOut(String key) {
        LockoutData lockout = lockouts.get(key);
        if (lockout == null) {
            return false;
        }
        if (Instant.now().getEpochSecond() >= lockout.lockedUntil()) {
            // Lockout expired, clean up
            lockouts.remove(key);
            attempts.remove(key);
            return false;
        }
        return true;
    }
    
    /**
     * Get remaining lockout time in seconds.
     */
    public long getRemainingLockoutSeconds(String key) {
        LockoutData lockout = lockouts.get(key);
        if (lockout == null) {
            return 0;
        }
        long remaining = lockout.lockedUntil() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
    
    /**
     * Record a failed login attempt.
     * Returns true if the account/IP is now locked out.
     */
    public synchronized boolean recordFailedAttempt(String key) {
        long now = Instant.now().getEpochSecond();
        
        // Clean up old attempts if window expired
        AttemptData current = attempts.get(key);
        if (current == null || (now - current.firstAttemptTime()) > ATTEMPT_WINDOW_SECONDS) {
            attempts.put(key, new AttemptData(1, now));
            return false;
        }
        
        int newCount = current.count() + 1;
        attempts.put(key, new AttemptData(newCount, current.firstAttemptTime()));
        
        if (newCount >= MAX_ATTEMPTS) {
            // Lock out
            long lockedUntil = now + LOCKOUT_DURATION_SECONDS;
            lockouts.put(key, new LockoutData(lockedUntil));
            return true;
        }
        
        return false;
    }
    
    /**
     * Clear failed attempts on successful login.
     */
    public void clearFailedAttempts(String key) {
        attempts.remove(key);
        lockouts.remove(key);
    }
    
    /**
     * Get remaining attempts before lockout.
     */
    public int getRemainingAttempts(String key) {
        AttemptData data = attempts.get(key);
        if (data == null) {
            return MAX_ATTEMPTS;
        }
        return Math.max(0, MAX_ATTEMPTS - data.count());
    }
    
    /**
     * Get current attempt count.
     */
    public int getAttemptCount(String key) {
        AttemptData data = attempts.get(key);
        return data == null ? 0 : data.count();
    }
}
