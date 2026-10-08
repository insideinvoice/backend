package com.insideinvoice.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Shared paging bounds. Controllers accept client-supplied page/size with no bean
 * validation, so {@code PageRequest.of(-1, 0)} or {@code size=100000} used to surface
 * as IllegalArgumentException → 500 or an unbounded load. UI max is 1000
 * (Dashboard requests size=1000), so anything beyond that is clamped.
 */
public final class PageParams {

    private PageParams() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), clampSize(size), sort);
    }

    public static int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 1000);
    }
}
