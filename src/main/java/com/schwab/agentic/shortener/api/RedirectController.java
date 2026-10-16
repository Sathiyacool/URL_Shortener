package com.schwab.agentic.shortener.api;

import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@Tag(name = "Redirects", description = "Resolve active short links and record click events.")
public class RedirectController {

    private final ShortUrlService shortUrlService;

    public RedirectController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @GetMapping("/{code:[A-Za-z0-9_-]{4,32}}")
    @Operation(
            summary = "Redirect to the destination",
            description = "Returns 302 and records a click only while the short URL is active.")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirect to the destination URL"),
        @ApiResponse(
                responseCode = "404",
                description = "Short code not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "410",
                description = "Short URL has expired",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
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
