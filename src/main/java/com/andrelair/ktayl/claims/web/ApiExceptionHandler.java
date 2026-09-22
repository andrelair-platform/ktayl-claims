package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.domain.AuthorityException;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
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

    /** A reserve/settlement above the actor's authority ⇒ 403 (threat T7). */
    @ExceptionHandler(AuthorityException.class)
    ProblemDetail authority(AuthorityException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Above authority");
        return pd;
    }

    /** A lifecycle step from a state that doesn't allow it ⇒ 409. */
    @ExceptionHandler(IllegalTransitionException.class)
    ProblemDetail illegalTransition(IllegalTransitionException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Illegal claim transition");
        return pd;
    }

    /** Unknown claim ⇒ 404. */
    @ExceptionHandler(ClaimNotFoundException.class)
    ProblemDetail notFound(ClaimNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Claim not found");
        return pd;
    }
}
