package com.theblastbeast.repository;

import java.time.LocalDate;

public interface ConcertDayCountView {

    LocalDate getDay();

    long getTotal();
}
