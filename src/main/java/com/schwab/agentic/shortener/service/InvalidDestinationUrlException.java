package com.schwab.agentic.shortener.service;

public class InvalidDestinationUrlException extends RuntimeException {

    public InvalidDestinationUrlException(String message) {
        super(message);
    }
}
