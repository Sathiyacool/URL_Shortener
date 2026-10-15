package com.schwab.agentic.shortener.service;

public class AliasConflictException extends RuntimeException {

    public AliasConflictException(String alias) {
        super("The requested alias is already in use: " + alias);
    }
}
