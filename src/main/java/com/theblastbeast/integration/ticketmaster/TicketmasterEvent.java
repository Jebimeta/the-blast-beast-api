package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterEvent(
        String id,
        String name,
        String url,
        String info,
        String pleaseNote,
        String locale,
        TicketmasterDates dates,
        List<TicketmasterClassification> classifications,
        List<TicketmasterPriceRange> priceRanges,
        List<TicketmasterImage> images,
        @JsonProperty("_embedded") TicketmasterEventEmbedded embedded) {
}
