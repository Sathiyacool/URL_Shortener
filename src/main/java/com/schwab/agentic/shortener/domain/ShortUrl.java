package com.schwab.agentic.shortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "short_urls")
public class ShortUrl {

    @Id
    @Column(length = 32, nullable = false)
    private String code;

    @Column(name = "destination_url", length = 2048, nullable = false)
    private String destinationUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected ShortUrl() {
    }

    public ShortUrl(String code, String destinationUrl, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.destinationUrl = destinationUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getCode() {
        return code;
    }

    public String getDestinationUrl() {
        return destinationUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
