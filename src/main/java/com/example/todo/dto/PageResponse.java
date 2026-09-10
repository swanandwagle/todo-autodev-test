package com.example.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Paginated response wrapper")
public class PageResponse<T> {

    @Schema(description = "Items on this page")
    private List<T> items;

    @Schema(description = "Current page number (0-based)")
    private int page;

    @Schema(description = "Page size")
    private int size;

    @Schema(description = "Total number of items matching the filters")
    private long totalItems;

    @Schema(description = "Total number of pages")
    private int totalPages;

    public static <E, D> PageResponse<D> from(Page<E> springPage, Function<E, D> mapper) {
        PageResponse<D> r = new PageResponse<>();
        r.items = springPage.getContent().stream().map(mapper).toList();
        r.page = springPage.getNumber();
        r.size = springPage.getSize();
        r.totalItems = springPage.getTotalElements();
        r.totalPages = springPage.getTotalPages();
        return r;
    }

    public List<T> getItems() { return items; }
    public void setItems(List<T> items) { this.items = items; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getTotalItems() { return totalItems; }
    public void setTotalItems(long totalItems) { this.totalItems = totalItems; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
