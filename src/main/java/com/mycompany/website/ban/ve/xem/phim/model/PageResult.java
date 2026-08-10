package com.mycompany.website.ban.ve.xem.phim.model;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalItems) {
    public PageResult {
        items = List.copyOf(items);
        page = Math.max(1, page);
        size = Math.max(1, size);
        totalItems = Math.max(0, totalItems);
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public long getTotalPages() {
        return Math.max(1, (totalItems + size - 1) / size);
    }

    public boolean isHasPrevious() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < getTotalPages();
    }
}
