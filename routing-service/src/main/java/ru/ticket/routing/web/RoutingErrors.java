package ru.ticket.routing.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.ticket.routing.domain.RoutingFailure;

@RestControllerAdvice(assignableTypes = RoadController.class)
public class RoutingErrors {
    record ErrorBody(String error, int status) {}

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> malformedInput() {
        return failure(new RoutingFailure(RoutingFailure.Reason.INVALID_ROUTE));
    }

    @ExceptionHandler(RoutingFailure.class)
    public ResponseEntity<ErrorBody> failure(RoutingFailure failure) {
        var status = switch (failure.reason()) {
            case INVALID_ROUTE, CAR_TOO_LONG, WALK_TOO_LONG -> HttpStatus.BAD_REQUEST;
            case NO_PATH -> HttpStatus.UNPROCESSABLE_ENTITY;
            case RATE_LIMITED, BUSY -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(new ErrorBody(failure.getMessage(), status.value()));
    }
}
