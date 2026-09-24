package com.example.ticketing.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository cho NotificationPreferences entity.
 */
@Repository
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, Long> {
    
    /**
     * Tìm preferences theo user.
     */
    Optional<NotificationPreferences> findByUserId(Long userId);
    
    /**
     * Tìm preferences theo username.
     */
    Optional<NotificationPreferences> findByUserUsername(String username);
    
    /**
     * Kiểm tra preferences có tồn tại không.
     */
    boolean existsByUserId(Long userId);
}
