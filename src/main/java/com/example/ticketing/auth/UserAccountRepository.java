package com.example.ticketing.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.ticketing.ticket.TicketTypes;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);

    List<UserAccount> findByRoleAndEnabledTrueOrderByUsernameAsc(TicketTypes.TicketRole role);

    Page<UserAccount> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
}
