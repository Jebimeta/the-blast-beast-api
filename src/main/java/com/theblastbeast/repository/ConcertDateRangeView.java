package com.theblastbeast.repository;

import java.time.OffsetDateTime;

public interface ConcertDateRangeView {

    OffsetDateTime getEarliest();

    OffsetDateTime getLatest();
}
