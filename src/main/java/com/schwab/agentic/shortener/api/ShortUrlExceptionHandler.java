package com.schwab.agentic.shortener.api;

import com.schwab.agentic.shortener.service.AliasConflictException;
import com.schwab.agentic.shortener.service.ExpiredShortUrlException;
import com.schwab.agentic.shortener.service.InvalidDestinationUrlException;
import com.schwab.agentic.shortener.service.ShortUrlNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ShortUrlExceptionHandler {

    @ExceptionHandler(InvalidDestinationUrlException.class)
    ProblemDetail invalidDestination(InvalidDestinationUrlException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AliasConflictException.class)
    ProblemDetail aliasConflict(AliasConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ShortUrlNotFoundException.class)
    ProblemDetail notFound(ShortUrlNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ExpiredShortUrlException.class)
    ProblemDetail expired(ExpiredShortUrlException exception) {
        return problem(HttpStatus.GONE, exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
