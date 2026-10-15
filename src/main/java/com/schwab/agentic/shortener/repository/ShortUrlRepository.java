package com.schwab.agentic.shortener.repository;

import com.schwab.agentic.shortener.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, String> {
}
