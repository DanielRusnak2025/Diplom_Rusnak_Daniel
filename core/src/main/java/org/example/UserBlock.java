package org.example;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_block")
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "block_type", length = 10, nullable = false)
    private BlockType blockType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_key")
    private Region region;

    @Column(length = 255)
    private String publisher;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UserBlock() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public BlockType getBlockType() { return blockType; }
    public void setBlockType(BlockType blockType) { this.blockType = blockType; }

    public Tag getTag() { return tag; }
    public void setTag(Tag tag) { this.tag = tag; }

    public Region getRegion() { return region; }
    public void setRegion(Region region) { this.region = region; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
