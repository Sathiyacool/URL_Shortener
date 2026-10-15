package com.schwab.agentic.shortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schwab.agentic.shortener.api.CreateShortUrlRequest;
import com.schwab.agentic.shortener.domain.ClickEvent;
import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.repository.ClickEventRepository;
import com.schwab.agentic.shortener.repository.ShortUrlRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    private ShortUrlService shortUrlService;

    @BeforeEach
    void configureClock() {
        shortUrlService = new ShortUrlService(
                shortUrlRepository,
                clickEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO);
    }

    @Test
    void createsShortUrlWithGeneratedCode() {
        when(shortUrlRepository.existsById(any())).thenReturn(false);
        when(shortUrlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = shortUrlService.create(
                new CreateShortUrlRequest("https://example.com/path", null, NOW.plusSeconds(60)));

        org.junit.jupiter.api.Assertions.assertTrue(result.getCode().matches("[A-Za-z0-9]{7}"));
        assertEquals("https://example.com/path", result.getDestinationUrl());
        assertEquals(NOW, result.getCreatedAt());
        assertEquals(NOW.plusSeconds(60), result.getExpiresAt());
    }

    @Test
    void acceptsCanonicalPublicIpv4Destination() {
        when(shortUrlRepository.existsById(any())).thenReturn(false);
        when(shortUrlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = shortUrlService.create(new CreateShortUrlRequest("https://8.8.8.8/dns", null, null));

        assertEquals("https://8.8.8.8/dns", result.getDestinationUrl());
    }

    @Test
    void appliesDefaultExpirationWhenRequestOmitsExpiration() {
        when(shortUrlRepository.existsById(any())).thenReturn(false);
        when(shortUrlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ShortUrlService serviceWithDefaultExpiration = new ShortUrlService(
                shortUrlRepository,
                clickEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(30));

        ShortUrl result = serviceWithDefaultExpiration.create(
                new CreateShortUrlRequest("https://example.com/path", null, null));

        assertEquals(NOW.plus(Duration.ofDays(30)), result.getExpiresAt());
    }

    @Test
    void explicitExpirationOverridesConfiguredDefault() {
        ShortUrlService serviceWithDefaultExpiration = new ShortUrlService(
                shortUrlRepository,
                clickEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(30));
        when(shortUrlRepository.existsById("promo")).thenReturn(false);
        when(shortUrlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = serviceWithDefaultExpiration.create(
                new CreateShortUrlRequest("https://example.com/path", "promo", NOW.plusSeconds(3600)));

        assertEquals(NOW.plusSeconds(3600), result.getExpiresAt());
    }

    @Test
    void rejectsUnsupportedUrlSchemes() {
        assertThrows(
                InvalidDestinationUrlException.class,
                () -> shortUrlService.create(new CreateShortUrlRequest("javascript:alert(1)", null, null)));

        verify(shortUrlRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/path",
        "https://127.0.0.1/",
        "https://10.0.0.5/",
        "http://192.168.1.10/",
        "http://169.254.169.254/latest/meta-data",
        "http://100.64.0.1/",
        "http://2130706433/",
        "http://0x7f000001/",
        "http://0x7f.0.0.1/",
        "http://0177.0.0.1/",
        "http://127.000.0.1/",
        "https://user:password@example.com/",
        "https://[::1]/",
        "https://[fc00::1]/",
        "https://[fe80::1]/",
        "https://[::ffff:192.168.1.1]/",
        "https://[::192.168.1.1]/",
        "https://service.internal/path",
        "https://printer.local/"
    })
    void rejectsLocalPrivateOrAmbiguousAddressDestinations(String destination) {
        assertThrows(
                InvalidDestinationUrlException.class,
                () -> shortUrlService.create(new CreateShortUrlRequest(destination, null, null)));

        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void rejectsExpirationThatIsNotInTheFuture() {
        assertThrows(
                InvalidDestinationUrlException.class,
                () -> shortUrlService.create(
                        new CreateShortUrlRequest("https://example.com", null, NOW)));
    }

    @Test
    void rejectsAnExistingAlias() {
        when(shortUrlRepository.existsById("promo")).thenReturn(true);

        assertThrows(
                AliasConflictException.class,
                () -> shortUrlService.create(
                        new CreateShortUrlRequest("https://example.com", "promo", null)));
    }

    @Test
    void recordsClickWhenResolvingAnActiveUrl() {
        ShortUrl shortUrl = new ShortUrl("active1", "https://example.com", NOW, null);
        when(shortUrlRepository.findById("active1")).thenReturn(Optional.of(shortUrl));

        ShortUrl resolved = shortUrlService.recordClickAndResolve("active1", "https://referrer.example", "test-agent");

        assertEquals(shortUrl, resolved);
        ArgumentCaptor<ClickEvent> clickCaptor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).save(clickCaptor.capture());
        assertEquals("active1", clickCaptor.getValue().getShortCode());
        assertEquals(NOW, clickCaptor.getValue().getClickedAt());
        assertEquals("https://referrer.example", clickCaptor.getValue().getReferrer());
        assertEquals("test-agent", clickCaptor.getValue().getUserAgent());
    }

    @Test
    void doesNotRecordClickForAnExpiredUrl() {
        when(shortUrlRepository.findById("expired1"))
                .thenReturn(Optional.of(new ShortUrl("expired1", "https://example.com", NOW.minusSeconds(60), NOW)));

        assertThrows(
                ExpiredShortUrlException.class,
                () -> shortUrlService.recordClickAndResolve("expired1", null, null));

        verify(clickEventRepository, never()).save(any());
    }
}
