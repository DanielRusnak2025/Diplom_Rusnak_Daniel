package org.example;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_history")
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PasswordHistory() {}

    public PasswordHistory(User user, String passwordHash, LocalDateTime createdAt) {
        this.user         = user;
        this.passwordHash = passwordHash;
        this.createdAt    = createdAt;
    }

    public int getId()                  { return id; }
    public User getUser()               { return user; }
    public String getPasswordHash()     { return passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
