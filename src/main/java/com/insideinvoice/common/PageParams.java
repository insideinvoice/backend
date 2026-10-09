package com.insideinvoice.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

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

    /**
     * Builds a {@link Sort} from client input, but only for fields the caller
     * whitelists. An unknown {@code sortBy} (e.g. {@code ?sortBy=password}) previously
     * reached {@code Sort.by(...)} and surfaced as a PropertyReferenceException → 500;
     * it now falls back to {@code defaultField} (indexed {@code createdAt} by default),
     * which also keeps ORDER BY on an index instead of a full filesort.
     */
    public static Sort safeSort(String sortBy, String sortDir, Set<String> allowed, String defaultField) {
        String field = (sortBy != null && allowed.contains(sortBy)) ? sortBy : defaultField;
        boolean asc = Sort.Direction.ASC.name().equalsIgnoreCase(sortDir);
        return asc ? Sort.by(field).ascending() : Sort.by(field).descending();
    }
}
