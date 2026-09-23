package com.example.ticketing.auth;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.ticket.TicketTypes;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class UserAdminService {
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuditService userAuditService;

    public UserAdminService(
        UserAccountRepository userAccountRepository,
        PasswordEncoder passwordEncoder,
        UserAuditService userAuditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.userAuditService = userAuditService;
    }

    public UserAccount createUser(
        String username,
        String rawPassword,
        TicketTypes.TicketRole role,
        Boolean enabled,
        String displayName,
        String title,
        String avatarUrl,
        String email,
        String actorUsername,
        TicketTypes.TicketRole actorRole
    ) {
        if (userAccountRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }
        rejectAvatarChange(avatarUrl);
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setDisplayName(displayName);
        user.setTitle(title);
        user.setEmail(email);
        if (enabled != null) {
            user.setEnabled(enabled);
        }
        UserAccount created = userAccountRepository.save(user);
        userAuditService.log(
            UserAuditAction.USER_CREATED,
            actorUsername,
            actorRole,
            created.getUsername()
        );
        return created;
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> listUsers(Pageable pageable, String search) {
        if (search != null && !search.isBlank()) {
            return userAccountRepository.findByUsernameContainingIgnoreCase(search.trim(), pageable);
        }
        return userAccountRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<UserAudit> listAudit() {
        return userAuditService.listAll();
    }

    @Transactional(readOnly = true)
    public List<UserAudit> listAudit(String targetUsername) {
        return userAuditService.listForTarget(targetUsername);
    }

    @Transactional(readOnly = true)
    public List<String> listEngineers() {
        return userAccountRepository.findByRoleAndEnabledTrueOrderByUsernameAsc(TicketTypes.TicketRole.ENGINEER)
            .stream()
            .map(UserAccount::getUsername)
            .toList();
    }

    public UserAccount updateEnabled(
        Long id,
        boolean enabled,
        String actorUsername,
        TicketTypes.TicketRole actorRole
    ) {
        UserAccount user = getUser(id);
        user.setEnabled(enabled);
        userAuditService.log(
            enabled ? UserAuditAction.USER_ENABLED : UserAuditAction.USER_DISABLED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        return user;
    }

    public UserAccount updateRole(
        Long id,
        TicketTypes.TicketRole role,
        String actorUsername,
        TicketTypes.TicketRole actorRole
    ) {
        UserAccount user = getUser(id);
        user.setRole(role);
        userAuditService.log(
            UserAuditAction.ROLE_CHANGED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        return user;
    }

    public UserAccount updateProfile(
        Long id,
        String displayName,
        String title,
        String avatarUrl,
        String email,
        String actorUsername,
        TicketTypes.TicketRole actorRole
    ) {
        UserAccount user = getUser(id);
        rejectAvatarChange(user.getAvatarUrl(), avatarUrl);
        user.setDisplayName(displayName);
        user.setTitle(title);
        user.setEmail(email);
        userAuditService.log(
            UserAuditAction.PROFILE_UPDATED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        return user;
    }

    public UserAccount resetPassword(
        Long id,
        String rawPassword,
        String actorUsername,
        TicketTypes.TicketRole actorRole
    ) {
        UserAccount user = getUser(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userAuditService.log(
            UserAuditAction.PASSWORD_RESET,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        return user;
    }

    public void deleteUser(Long id, String actorUsername, TicketTypes.TicketRole actorRole) {
        UserAccount user = getUser(id);
        if (actorUsername != null && actorUsername.equals(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete your own account.");
        }
        userAuditService.log(
            UserAuditAction.USER_DELETED,
            actorUsername,
            actorRole,
            user.getUsername()
        );
        userAccountRepository.delete(user);
    }

    public UserAccount getUser(Long id) {
        return userAccountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private void rejectAvatarChange(String avatarUrl) {
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar changes are disabled.");
        }
    }

    private void rejectAvatarChange(String currentAvatarUrl, String requestedAvatarUrl) {
        if (!normalize(currentAvatarUrl).equals(normalize(requestedAvatarUrl))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar changes are disabled.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
