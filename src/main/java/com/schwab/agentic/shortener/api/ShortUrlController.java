package com.schwab.agentic.shortener.api;

import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "URL Management", description = "Create short URLs and retrieve click analytics.")
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
    @Operation(
            summary = "Create a short URL",
            description = "Creates a short link for an absolute HTTP(S) destination. "
                    + "An omitted expiry uses the configured default.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Short URL created"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid destination, alias, or expiry",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Alias already exists",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
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
    @Operation(
            summary = "Get click analytics",
            description = "Returns the total clicks and up to 20 recent clicks. "
                    + "Requires the operator scope in the production profile.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analytics returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required in production"),
        @ApiResponse(responseCode = "403", description = "Operator scope required in production"),
        @ApiResponse(
                responseCode = "404",
                description = "Short code not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ClickStatsResponse stats(@PathVariable String code) {
        return shortUrlService.getStats(code);
    }
}
