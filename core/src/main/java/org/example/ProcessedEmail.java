package org.example;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_email")
public class ProcessedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "message_id_hash", length = 64, nullable = false, unique = true)
    private String messageIdHash;

    @Column(name = "sender_domain", length = 253, nullable = false)
    private String senderDomain;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "links_total", nullable = false)
    private int linksTotal = 0;

    @Column(name = "links_rejected", nullable = false)
    private int linksRejected = 0;

    @Column(name = "links_saved", nullable = false)
    private int linksSaved = 0;

    public ProcessedEmail() {}

    public ProcessedEmail(String messageIdHash, String senderDomain,
                          LocalDateTime receivedAt, LocalDateTime processedAt) {
        this.messageIdHash = messageIdHash;
        this.senderDomain  = senderDomain;
        this.receivedAt    = receivedAt;
        this.processedAt   = processedAt;
    }

    public int getId()                  { return id; }
    public String getMessageIdHash()    { return messageIdHash; }
    public String getSenderDomain()     { return senderDomain; }
    public LocalDateTime getReceivedAt(){ return receivedAt; }
    public LocalDateTime getProcessedAt(){ return processedAt; }
    public int getLinksTotal()          { return linksTotal; }
    public int getLinksRejected()       { return linksRejected; }
    public int getLinksSaved()          { return linksSaved; }

    public void setMessageIdHash(String v)    { this.messageIdHash = v; }
    public void setSenderDomain(String v)     { this.senderDomain = v; }
    public void setReceivedAt(LocalDateTime v){ this.receivedAt = v; }
    public void setProcessedAt(LocalDateTime v){ this.processedAt = v; }
    public void setLinksTotal(int v)          { this.linksTotal = v; }
    public void setLinksRejected(int v)       { this.linksRejected = v; }
    public void setLinksSaved(int v)          { this.linksSaved = v; }
}
