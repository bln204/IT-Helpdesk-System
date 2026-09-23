package com.example.ticketing.auth;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.ticketing.ticket.TicketTypes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserAdminController {
    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PostMapping
    public ResponseEntity<UserDtos.UserResponse> createUser(
        @Valid @RequestBody UserDtos.UserCreateRequest request,
        Authentication authentication
    ) {
        requireAdminOrEngineer(authentication);
        if (!hasAdminRole(authentication) && request.getRole() == com.example.ticketing.ticket.TicketTypes.TicketRole.ENGINEER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can create engineers.");
        }
        TicketTypes.TicketRole actorRole = resolveRole(authentication);
        UserAccount user = userAdminService.createUser(
            request.getUsername(),
            request.getPassword(),
            request.getRole(),
            request.getEnabled(),
            request.getDisplayName(),
            request.getTitle(),
            request.getAvatarUrl(),
            request.getEmail(),
            authentication.getName(),
            actorRole
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDtos.UserResponse.from(user));
    }

    @GetMapping
    public Page<UserDtos.UserResponse> listUsers(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String search
    ) {
        requireAdminOrEngineer(authentication);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("username").ascending());
        return userAdminService.listUsers(pageRequest, search).map(UserDtos.UserResponse::from);
    }

    @GetMapping("/engineers")
    public List<String> listEngineers() {
        return userAdminService.listEngineers();
    }

    @GetMapping("/audit")
    public List<UserDtos.UserAuditResponse> listAudit(
        @RequestParam(required = false) String targetUsername,
        Authentication authentication
    ) {
        requireAdminOrEngineer(authentication);
        if (targetUsername == null || targetUsername.isBlank()) {
            return userAdminService.listAudit().stream()
                .map(UserDtos.UserAuditResponse::from)
                .toList();
        }
        return userAdminService.listAudit(targetUsername).stream()
            .map(UserDtos.UserAuditResponse::from)
            .toList();
    }

    @PatchMapping("/{id}/enabled")
    public UserDtos.UserResponse updateEnabled(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.UserEnabledRequest request,
        Authentication authentication
    ) {
        requireAdminOrEngineer(authentication);
        TicketTypes.TicketRole actorRole = resolveRole(authentication);
        return UserDtos.UserResponse.from(
            userAdminService.updateEnabled(
                id,
                request.getEnabled(),
                authentication.getName(),
                actorRole
            )
        );
    }

    @PatchMapping("/{id}/role")
    public UserDtos.UserResponse updateRole(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.UserRoleUpdateRequest request,
        Authentication authentication
    ) {
        requireAdminOrEngineer(authentication);
        if (!hasAdminRole(authentication)) {
            if (request.getRole() != com.example.ticketing.ticket.TicketTypes.TicketRole.REQUESTER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Engineers can only assign requester role.");
            }
            UserAccount target = userAdminService.getUser(id);
            if (target.getRole() != com.example.ticketing.ticket.TicketTypes.TicketRole.REQUESTER) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Engineers cannot change admin or engineer accounts."
                );
            }
        }
        TicketTypes.TicketRole actorRole = resolveRole(authentication);
        return UserDtos.UserResponse.from(
            userAdminService.updateRole(
                id,
                request.getRole(),
                authentication.getName(),
                actorRole
            )
        );
    }

    @PatchMapping("/{id}/profile")
    public UserDtos.UserResponse updateProfile(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.UserProfileUpdateRequest request,
        Authentication authentication
    ) {
        requireAdminOrEngineer(authentication);
        if (!hasAdminRole(authentication)) {
            UserAccount target = userAdminService.getUser(id);
            if (target.getRole() != com.example.ticketing.ticket.TicketTypes.TicketRole.REQUESTER) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Engineers cannot edit admin or engineer profiles."
                );
            }
        }
        TicketTypes.TicketRole actorRole = resolveRole(authentication);
        return UserDtos.UserResponse.from(
            userAdminService.updateProfile(
                id,
                request.getDisplayName(),
                request.getTitle(),
                request.getAvatarUrl(),
                request.getEmail(),
                authentication.getName(),
                actorRole
            )
        );
    }
    @PatchMapping("/{id}/password")
    public UserDtos.UserResponse resetPassword(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.UserPasswordResetRequest request,
        Authentication authentication
    ) {
        requireAdminOrEngineer(authentication);
        if (!hasAdminRole(authentication)) {
            UserAccount target = userAdminService.getUser(id);
            if (target.getRole() != com.example.ticketing.ticket.TicketTypes.TicketRole.REQUESTER) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Engineers can only reset requester passwords."
                );
            }
        }
        return UserDtos.UserResponse.from(
            userAdminService.resetPassword(
                id,
                request.getPassword(),
                authentication.getName(),
                resolveRole(authentication)
            )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireAdminOrEngineer(authentication);
        if (!hasAdminRole(authentication)) {
            UserAccount target = userAdminService.getUser(id);
            if (target.getRole() != com.example.ticketing.ticket.TicketTypes.TicketRole.REQUESTER) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Engineers can only delete requester accounts."
                );
            }
        }
        TicketTypes.TicketRole actorRole = resolveRole(authentication);
        userAdminService.deleteUser(id, authentication.getName(), actorRole);
        return ResponseEntity.noContent().build();
    }

    private void requireAdminOrEngineer(Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_ENGINEER")) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or engineer role required.");
    }

    private boolean hasAdminRole(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    private boolean hasRole(Authentication authentication, String role) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private com.example.ticketing.ticket.TicketTypes.TicketRole resolveRole(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (role.startsWith("ROLE_")) {
                return com.example.ticketing.ticket.TicketTypes.TicketRole.valueOf(role.substring(5));
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authenticated user does not have a role.");
    }
}
