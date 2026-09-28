package com.example.ticketing.problem;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Problem Workaround Note Entity.
 */
@Entity
@Table(name = "problem_workaround_notes")
public class ProblemWorkaroundNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "author_name", length = 100)
    private String authorName;

    @Column(name = "author_username", length = 100)
    private String authorUsername;

    @Column(name = "author_role", length = 50)
    private String authorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", length = 30)
    private NoteType noteType = NoteType.WORKAROUND;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "effectiveness_rating")
    private Integer effectivenessRating;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== Enums ====================

    public enum NoteType {
        WORKAROUND("Workaround"),
        RESOLUTION_STEP("Bước giải quyết"),
        INVESTIGATION_NOTE("Ghi chú điều tra"),
        LESSON_LEARNED("Bài học kinh nghiệm");

        private final String label;
        NoteType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public ProblemWorkaroundNote() {
    }

    public ProblemWorkaroundNote(Problem problem, String authorName, String authorUsername, String body) {
        this.problem = problem;
        this.authorName = authorName;
        this.authorUsername = authorUsername;
        this.body = body;
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem problem) { this.problem = problem; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }
    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String authorRole) { this.authorRole = authorRole; }
    public NoteType getNoteType() { return noteType; }
    public void setNoteType(NoteType noteType) { this.noteType = noteType; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Integer getEffectivenessRating() { return effectivenessRating; }
    public void setEffectivenessRating(Integer effectivenessRating) { this.effectivenessRating = effectivenessRating; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "ProblemWorkaroundNote{" +
                "id=" + id +
                ", noteType=" + noteType +
                '}';
    }
}
