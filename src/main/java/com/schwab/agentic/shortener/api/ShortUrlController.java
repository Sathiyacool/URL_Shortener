package com.schwab.agentic.shortener.api;

import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.service.ShortUrlService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/urls")
public class ShortUrlController {

    private final ShortUrlService shortUrlService;
    private final String publicBaseUrl;

    public ShortUrlController(
            ShortUrlService shortUrlService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.shortUrlService = shortUrlService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        ShortUrl shortUrl = shortUrlService.create(request);
        String shortUrlText = UriComponentsBuilder.fromUriString(publicBaseUrl)
                .pathSegment(shortUrl.getCode())
                .build()
                .toUriString();
        ShortUrlResponse response = new ShortUrlResponse(
                shortUrl.getCode(),
                shortUrlText,
                shortUrl.getDestinationUrl(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt());
        return ResponseEntity.created(URI.create(shortUrlText)).body(response);
    }

    @GetMapping("/{code}/stats")
    public ClickStatsResponse stats(@PathVariable String code) {
        return shortUrlService.getStats(code);
    }
}
