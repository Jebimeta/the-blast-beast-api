package com.theblastbeast.repository;

import java.time.LocalDate;

public record ConcertFilter(
        String genre,
        String city,
        String band,
        LocalDate from,
        LocalDate to) {
}
