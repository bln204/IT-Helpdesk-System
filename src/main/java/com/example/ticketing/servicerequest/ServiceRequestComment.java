package com.example.ticketing.servicerequest;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Service Request Comment Entity.
 */
@Entity
@Table(name = "service_request_comments")
public class ServiceRequestComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @Column(name = "author_name", length = 100)
    private String authorName;

    @Column(name = "author_username", length = 100)
    private String authorUsername;

    @Column(name = "author_role", length = 50)
    private String authorRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== Enums ====================

    public enum Visibility {
        PUBLIC,  // Visible to requester
        INTERNAL // IT staff only
    }

    // ==================== Constructors ====================

    public ServiceRequestComment() {
    }

    public ServiceRequestComment(ServiceRequest serviceRequest, String authorName, String authorUsername, String body) {
        this.serviceRequest = serviceRequest;
        this.authorName = authorName;
        this.authorUsername = authorUsername;
        this.body = body;
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ServiceRequest getServiceRequest() { return serviceRequest; }
    public void setServiceRequest(ServiceRequest serviceRequest) { this.serviceRequest = serviceRequest; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }
    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String authorRole) { this.authorRole = authorRole; }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "ServiceRequestComment{" +
                "id=" + id +
                ", authorUsername='" + authorUsername + '\'' +
                '}';
    }
}
