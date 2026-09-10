package com.omar.vendora.common.dto;

import java.util.List;

public record ErrorResponse(
    ErrorBody error
) {
    public static ErrorResponse of(String code, String message, List<ErrorDetail> details, String correlationId) {
        return new ErrorResponse(new ErrorBody(code, message, details != null ? details : List.of(), correlationId));
    }

    public record ErrorBody(
        String code,
        String message,
        List<ErrorDetail> details,
        String correlationId
    ) {}

    public record ErrorDetail(
        String field,
        String issue
    ) {}
}
