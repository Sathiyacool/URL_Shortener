package com.schwab.agentic.guardrails;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SecretScanner {

    private static final List<SecretPattern> PATTERNS = List.of(
            new SecretPattern(
                    "PRIVATE_KEY_BLOCK",
                    Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
            new SecretPattern("AWS_ACCESS_KEY", Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b")),
            new SecretPattern(
                    "CREDENTIAL_ASSIGNMENT",
                    Pattern.compile(
                            "(?i)\\b(?:api[_-]?key|access[_-]?token|secret|password)\\b\\s*[:=]\\s*['\"]?[A-Za-z0-9_./+=-]{12,}")));

    public Optional<String> findSecret(String content) {
        if (content == null) {
            return Optional.empty();
        }
        return PATTERNS.stream()
                .filter(pattern -> pattern.pattern().matcher(content).find())
                .map(SecretPattern::findingCode)
                .findFirst();
    }

    private record SecretPattern(String findingCode, Pattern pattern) {
    }
}
