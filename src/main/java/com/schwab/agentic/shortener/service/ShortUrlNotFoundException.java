package com.schwab.agentic.shortener.service;

public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String code) {
        super("No short URL exists for code: " + code);
    }
}
