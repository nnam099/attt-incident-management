package com.attt.incident.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable API contract that does not expose Spring Data's internal PageImpl shape. */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int size,
        int number
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getSize(), page.getNumber());
    }
}
