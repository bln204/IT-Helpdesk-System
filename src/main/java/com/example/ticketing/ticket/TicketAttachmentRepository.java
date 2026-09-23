package com.example.ticketing.ticket;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    
    List<TicketAttachment> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    
    void deleteByTicketId(Long ticketId);
    
    long countByTicketId(Long ticketId);
}
