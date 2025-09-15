package com.yhou.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Response DTO for standardized error information and validation messages.
 */
@Getter
@Builder
public class ErrorResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant timestamp;

    private final String path;
    private final String errorCode;
    private final String message;
    private final boolean retryable;
}
