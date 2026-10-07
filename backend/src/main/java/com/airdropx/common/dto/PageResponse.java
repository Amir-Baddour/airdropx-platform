package com.airdropx.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Wraps Spring Data's Page&lt;T&gt; into a stable, minimal JSON shape instead of leaking Spring internals. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
