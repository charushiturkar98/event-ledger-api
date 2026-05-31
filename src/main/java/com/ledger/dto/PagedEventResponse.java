package com.ledger.dto;

import java.util.List;

public class PagedEventResponse {

    private List<EventResponse> events;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public PagedEventResponse(List<EventResponse> events, int page, int size,
                               long totalElements, int totalPages) {
        this.events        = events;
        this.page          = page;
        this.size          = size;
        this.totalElements = totalElements;
        this.totalPages    = totalPages;
    }

    public List<EventResponse> getEvents()  { return events; }
    public int getPage()                    { return page; }
    public int getSize()                    { return size; }
    public long getTotalElements()          { return totalElements; }
    public int getTotalPages()              { return totalPages; }
}
