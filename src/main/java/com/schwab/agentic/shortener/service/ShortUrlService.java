package com.schwab.agentic.shortener.service;

import com.schwab.agentic.shortener.domain.ClickEvent;
import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.repository.ClickEventRepository;
import com.schwab.agentic.shortener.repository.ShortUrlRepository;
import com.schwab.agentic.shortener.api.ClickRecordResponse;
import com.schwab.agentic.shortener.api.ClickStatsResponse;
import com.schwab.agentic.shortener.api.CreateShortUrlRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShortUrlService.class);

    private static final String CODE_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int GENERATED_CODE_LENGTH = 7;
    private static final int CODE_GENERATION_ATTEMPTS = 5;
    private static final int MAX_REFERRER_LENGTH = 2048;
    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final Clock clock;
    private final Duration defaultExpiration;

    public ShortUrlService(
            ShortUrlRepository shortUrlRepository,
            ClickEventRepository clickEventRepository,
            Clock clock,
            @Value("${app.default-expiration:0s}") Duration defaultExpiration) {
        if (defaultExpiration == null || defaultExpiration.isNegative()) {
            throw new IllegalArgumentException("Default URL expiration must not be negative");
        }
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.clock = clock;
        this.defaultExpiration = defaultExpiration;
    }

    @Transactional
    public ShortUrl create(CreateShortUrlRequest request) {
        String destinationUrl = validateDestinationUrl(request.url());
        Instant now = clock.instant();
        Instant expiresAt = request.expiresAt();
        if (expiresAt == null && !defaultExpiration.isZero()) {
            expiresAt = now.plus(defaultExpiration);
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidDestinationUrlException("Expiration time must be in the future");
        }

        String code = request.alias() == null ? generateCode() : request.alias();
        if (shortUrlRepository.existsById(code)) {
            log.warn("Alias conflict code={}", code);
            throw new AliasConflictException(code);
        }

        ShortUrl saved = shortUrlRepository.save(new ShortUrl(code, destinationUrl, now, expiresAt));
        log.info("Short URL created code={} expiresAt={}", code, expiresAt);
        return saved;
    }

    @Transactional
    public ShortUrl recordClickAndResolve(String code, String referrer, String userAgent) {
        ShortUrl shortUrl = shortUrlRepository.findById(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        Instant clickedAt = clock.instant();
        if (shortUrl.getExpiresAt() != null && !shortUrl.getExpiresAt().isAfter(clickedAt)) {
            log.info("Redirect refused, link expired code={}", code);
            throw new ExpiredShortUrlException(code);
        }

        clickEventRepository.save(new ClickEvent(
                code,
                clickedAt,
                limit(referrer, MAX_REFERRER_LENGTH),
                limit(userAgent, MAX_USER_AGENT_LENGTH)));
        log.debug("Click recorded code={}", code);
        return shortUrl;
    }

    @Transactional(readOnly = true)
    public ClickStatsResponse getStats(String code) {
        if (!shortUrlRepository.existsById(code)) {
            throw new ShortUrlNotFoundException(code);
        }
        List<ClickRecordResponse> clicks = clickEventRepository
                .findTop20ByShortCodeOrderByClickedAtDesc(code)
                .stream()
                .map(click -> new ClickRecordResponse(
                        click.getClickedAt(), click.getReferrer(), click.getUserAgent()))
                .toList();
        return new ClickStatsResponse(clickEventRepository.countByShortCode(code), clicks);
    }

    private String validateDestinationUrl(String value) {
        String normalized = value.trim();
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new InvalidDestinationUrlException(
                        "Destination URL must be an absolute HTTP or HTTPS URL without credentials");
            }
            rejectLocalOrPrivateHost(uri.getHost());
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new InvalidDestinationUrlException("Destination URL is not a valid URI");
        }
    }

    private void rejectLocalOrPrivateHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.equals("localhost")
                || host.endsWith(".localhost")
                || host.equals("local")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".home.arpa")
                || host.endsWith(".lan")
                || host.endsWith(".test")
                || host.endsWith(".invalid")
                || host.endsWith(".example")) {
            throw new InvalidDestinationUrlException("Destination URL must not target a local or private host");
        }

        if (looksLikeNumericAddress(host)) {
            if (!host.matches("[0-9.]+")) {
                throw new InvalidDestinationUrlException("Destination URL contains an invalid IP address");
            }
            String[] octets = host.split("\\.", -1);
            if (octets.length != 4) {
                throw new InvalidDestinationUrlException("Destination URL contains an invalid IP address");
            }
            byte[] address = new byte[4];
            for (int index = 0; index < octets.length; index++) {
                try {
                    if (octets[index].length() > 1 && octets[index].startsWith("0")) {
                        throw new NumberFormatException();
                    }
                    int value = Integer.parseInt(octets[index]);
                    if (value > 255) {
                        throw new NumberFormatException();
                    }
                    address[index] = (byte) value;
                } catch (NumberFormatException exception) {
                    throw new InvalidDestinationUrlException("Destination URL contains an invalid IP address");
                }
            }
            rejectNonPublicAddress(address);
            return;
        }

        String ipv6Host = host;
        if (ipv6Host.startsWith("[") && ipv6Host.endsWith("]")) {
            ipv6Host = ipv6Host.substring(1, ipv6Host.length() - 1);
        }
        if (ipv6Host.contains(":")) {
            try {
                rejectNonPublicAddress(InetAddress.getByName(ipv6Host).getAddress());
            } catch (UnknownHostException exception) {
                throw new InvalidDestinationUrlException("Destination URL contains an invalid IP address");
            }
        }
    }

    private boolean looksLikeNumericAddress(String host) {
        String[] labels = host.split("\\.", -1);
        return List.of(labels).stream()
                .allMatch(label -> label.matches("(?i)(?:[0-9]+|0x[0-9a-f]+)"));
    }

    private void rejectNonPublicAddress(byte[] address) {
        boolean ipv4CompatibleIpv6 = address.length == 16;
        if (ipv4CompatibleIpv6) {
            for (int index = 0; index < 12; index++) {
                if (address[index] != 0) {
                    ipv4CompatibleIpv6 = false;
                    break;
                }
            }
        }
        if (ipv4CompatibleIpv6) {
            throw new InvalidDestinationUrlException("Destination URL contains an ambiguous IPv6 address");
        }
        if (address.length == 16
                && address[10] == (byte) 0xff
                && address[11] == (byte) 0xff) {
            byte[] ipv4Address = new byte[4];
            System.arraycopy(address, 12, ipv4Address, 0, ipv4Address.length);
            rejectNonPublicAddress(ipv4Address);
            return;
        }
        boolean privateIpv6 = address.length == 16
                && ((address[0] & 0xfe) == 0xfc
                        || (address[0] & 0xff) == 0xfe && (address[1] & 0xc0) == 0x80);
        try {
            InetAddress parsed = InetAddress.getByAddress(address);
            boolean sharedIpv4 = address.length == 4
                    && (address[0] & 0xff) == 100
                    && (address[1] & 0xff) >= 64
                    && (address[1] & 0xff) <= 127;
            if (parsed.isAnyLocalAddress()
                    || parsed.isLoopbackAddress()
                    || parsed.isLinkLocalAddress()
                    || parsed.isSiteLocalAddress()
                    || parsed.isMulticastAddress()
                    || privateIpv6
                    || sharedIpv4) {
                throw new InvalidDestinationUrlException("Destination URL must not target a local or private host");
            }
        } catch (UnknownHostException exception) {
            throw new InvalidDestinationUrlException("Destination URL contains an invalid IP address");
        }
    }

    private String generateCode() {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(GENERATED_CODE_LENGTH);
            for (int index = 0; index < GENERATED_CODE_LENGTH; index++) {
                code.append(CODE_CHARACTERS.charAt(RANDOM.nextInt(CODE_CHARACTERS.length())));
            }
            String candidate = code.toString();
            if (!shortUrlRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique short URL code");
    }

    private String limit(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
