package com.survivorserver.globalMarket.lib;

import java.util.List;

import com.survivorserver.globalMarket.Listing;

public class SearchResult {

    private int totalFound;
    private List<Listing> currentPage;

    public SearchResult(int totalFound, List<Listing> currentPage) {
        this.totalFound = totalFound;
        this.currentPage = currentPage;
    }

    public int getTotalFound() {
        return totalFound;
    }

    public List<Listing> getPage() {
        return currentPage;
    }
}
