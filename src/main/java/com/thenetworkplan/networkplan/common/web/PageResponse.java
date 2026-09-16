package com.thenetworkplan.networkplan.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable page envelope. Spring's own {@code PageImpl} is not a documented wire
 * format, so the API exposes this record instead.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    public static <T> PageResponse<T> of(List<T> content) {
        return new PageResponse<>(content, 0, content.size(), content.size(), 1, true);
    }
}
