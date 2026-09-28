package com.example.ticketing.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho ChangeTask entity.
 */
@Repository
public interface ChangeTaskRepository extends JpaRepository<ChangeTask, Long> {

    List<ChangeTask> findByChangeRequestIdOrderByTaskOrderAsc(Long changeRequestId);
}
