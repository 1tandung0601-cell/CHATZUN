package com.chatapp.exception;

import com.chatapp.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Lỗi validation: @NotBlank, @Email, @Size,...
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(
            MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();

        return new ErrorResponse(false, message);
    }

    // Lỗi đăng nhập
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthenticationException(
            AuthenticationException e) {

        return new ErrorResponse(
                false,
                e.getMessage()
        );
    }

    // Lỗi dữ liệu đã tồn tại
    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateResourceException(
            DuplicateResourceException e) {

        return new ErrorResponse(
                false,
                e.getMessage()
        );
    }
    // Xử lý lỗi HTTP do tầng Service chủ động ném ra.
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse>
    handleResponseStatusException(ResponseStatusException e) {

        HttpStatus status = HttpStatus.valueOf(
                e.getStatusCode().value()
        );

        String message = e.getReason() != null
                ? e.getReason()
                : "Đã xảy ra lỗi";

        return org.springframework.http.ResponseEntity
                .status(status)
                .body(new ErrorResponse(false, message));
    }
}