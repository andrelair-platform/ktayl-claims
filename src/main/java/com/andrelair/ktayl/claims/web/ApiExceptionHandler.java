package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.domain.OutOfCoverException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps ACL failures to RFC-7807 problem responses. */
@RestControllerAdvice
class ApiExceptionHandler {

    /** A valid request the business rejects ⇒ 422. */
    @ExceptionHandler(OutOfCoverException.class)
    ProblemDetail outOfCover(OutOfCoverException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Claim out of cover");
        return pd;
    }

    /** Missing the mandatory {@code Idempotency-Key} ⇒ 400. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Missing required header");
        return pd;
    }
}
