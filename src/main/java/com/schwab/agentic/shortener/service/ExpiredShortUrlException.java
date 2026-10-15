package com.schwab.agentic.shortener.service;

public class ExpiredShortUrlException extends RuntimeException {

    public ExpiredShortUrlException(String code) {
        super("The short URL has expired: " + code);
    }
}
