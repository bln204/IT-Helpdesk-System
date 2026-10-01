package com.example.ticketing.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho ChangeTimeline entity.
 */
@Repository
public interface ChangeTimelineRepository extends JpaRepository<ChangeTimeline, Long> {

    List<ChangeTimeline> findByChangeRequestIdOrderByCreatedAtAsc(Long changeRequestId);

    List<ChangeTimeline> findByChangeRequestIdOrderByCreatedAtDesc(Long changeRequestId);
}
