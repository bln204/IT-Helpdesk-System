package com.example.ticketing.ticket;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho TicketTimeline entity.
 */
@Repository
public interface TicketTimelineRepository extends JpaRepository<TicketTimeline, Long> {
    
    /**
     * Lấy timeline của một ticket, sắp xếp theo thời gian giảm dần.
     */
    @Query("SELECT t FROM TicketTimeline t WHERE t.ticket.id = :ticketId ORDER BY t.createdAt DESC")
    List<TicketTimeline> findByTicketIdOrderByCreatedAtDesc(@Param("ticketId") Long ticketId);
    
    /**
     * Lấy timeline của một ticket với phân trang.
     */
    @Query("SELECT t FROM TicketTimeline t WHERE t.ticket.id = :ticketId ORDER BY t.createdAt DESC")
    Page<TicketTimeline> findByTicketId(@Param("ticketId") Long ticketId, Pageable pageable);
    
    /**
     * Lấy timeline theo category.
     */
    @Query("SELECT t FROM TicketTimeline t WHERE t.ticket.id = :ticketId AND t.eventCategory = :category ORDER BY t.createdAt DESC")
    List<TicketTimeline> findByTicketIdAndCategory(@Param("ticketId") Long ticketId, 
                                                   @Param("category") TicketTimeline.EventCategory category);
    
    /**
     * Lấy timeline theo event type.
     */
    @Query("SELECT t FROM TicketTimeline t WHERE t.ticket.id = :ticketId AND t.eventType = :type ORDER BY t.createdAt DESC")
    List<TicketTimeline> findByTicketIdAndEventType(@Param("ticketId") Long ticketId,
                                                     @Param("type") TicketTimeline.EventType type);
    
    /**
     * Lấy timeline của nhiều tickets (cho dashboard).
     */
    @Query("SELECT t FROM TicketTimeline t WHERE t.ticket.id IN :ticketIds ORDER BY t.createdAt DESC")
    List<TicketTimeline> findByTicketIds(@Param("ticketIds") List<Long> ticketIds);
    
    /**
     * Lấy timeline gần đây nhất của một ticket.
     */
    @Query("SELECT t FROM TicketTimeline t WHERE t.ticket.id = :ticketId ORDER BY t.createdAt DESC LIMIT 1")
    TicketTimeline findLatestByTicketId(@Param("ticketId") Long ticketId);
    
    /**
     * Đếm events trong timeline của một ticket.
     */
    long countByTicketId(Long ticketId);
    
    /**
     * Đếm events theo category.
     */
    long countByTicketIdAndEventCategory(Long ticketId, TicketTimeline.EventCategory category);
    
    /**
     * Lấy comments từ timeline.
     */
    @Query("SELECT t FROM TicketTimeline t WHERE t.ticket.id = :ticketId AND t.eventCategory = 'COMMENT' ORDER BY t.createdAt ASC")
    List<TicketTimeline> findComments(@Param("ticketId") Long ticketId);
    
    /**
     * Lấy timeline của user.
     */
    @Query("""
        SELECT t FROM TicketTimeline t 
        WHERE t.actorUsername = :username 
        ORDER BY t.createdAt DESC
        """)
    Page<TicketTimeline> findByActorUsername(@Param("username") String username, Pageable pageable);
    
    /**
     * Xóa timeline của một ticket.
     */
    void deleteByTicketId(Long ticketId);
}
