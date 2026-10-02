package com.example.demo.repository;

/** Projection for {@link AccountRepository#countGroupedByStateAndPlace}. */
public interface StatePlaceCount {
    String getState();
    String getPlace();
    Long getCount();
}
