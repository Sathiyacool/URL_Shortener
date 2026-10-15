package com.schwab.agentic.shortener.api;

import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.service.ShortUrlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class RedirectController {

    private final ShortUrlService shortUrlService;

    public RedirectController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @GetMapping("/{code:[A-Za-z0-9_-]{4,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        ShortUrl shortUrl = shortUrlService.recordClickAndResolve(
                code,
                request.getHeader(HttpHeaders.REFERER),
                request.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, shortUrl.getDestinationUrl())
                .build();
    }
}
