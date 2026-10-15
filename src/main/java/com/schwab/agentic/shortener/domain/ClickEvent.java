package com.schwab.agentic.shortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "url_clicks",
        indexes = @Index(name = "idx_url_clicks_code_time", columnList = "short_code, clicked_at"))
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", length = 32, nullable = false)
    private String shortCode;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(length = 2048)
    private String referrer;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected ClickEvent() {
    }

    public ClickEvent(String shortCode, Instant clickedAt, String referrer, String userAgent) {
        this.shortCode = shortCode;
        this.clickedAt = clickedAt;
        this.referrer = referrer;
        this.userAgent = userAgent;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
