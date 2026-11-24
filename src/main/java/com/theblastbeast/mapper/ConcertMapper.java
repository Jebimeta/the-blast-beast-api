package com.theblastbeast.mapper;

import com.theblastbeast.domain.entity.Concert;
import com.theblastbeast.domain.entity.Venue;
import com.theblastbeast.domain.vo.ConcertDetail;
import com.theblastbeast.domain.vo.ConcertSummary;
import com.theblastbeast.domain.vo.VenueDetail;
import com.theblastbeast.domain.vo.VenueSummary;
import com.theblastbeast.service.ConcertGenre;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConcertMapper {

    public ConcertSummary toSummary(Concert concert) {
        ConcertSummary summary = new ConcertSummary();
        fillSummary(summary, concert);
        return summary;
    }

    public ConcertDetail toDetail(Concert concert) {
        ConcertDetail detail = new ConcertDetail();
        fillSummary(detail, concert);
        detail.setDescription(concert.getDescription());
        detail.setImageUrl(concert.getImageUrl());
        detail.setImageAttribution(concert.getImageAttribution());
        detail.setSource(sourceLabel(concert.getSource()));
        return detail;
    }

    public VenueSummary toVenueSummary(Venue venue) {
        VenueSummary summary = new VenueSummary();
        summary.setId(venue.getId());
        summary.setName(venue.getName());
        summary.setCity(venue.getCity());
        summary.setCitySlug(venue.getCitySlug());
        summary.setRegion(venue.getRegion());
        return summary;
    }

    public VenueDetail toVenueDetail(Venue venue) {
        VenueDetail detail = new VenueDetail();
        detail.setId(venue.getId());
        detail.setName(venue.getName());
        detail.setCity(venue.getCity());
        detail.setCitySlug(venue.getCitySlug());
        detail.setRegion(venue.getRegion());
        detail.setAddress(venue.getAddress());
        detail.setLatitude(toDouble(venue.getLatitude()));
        detail.setLongitude(toDouble(venue.getLongitude()));
        return detail;
    }

    private void fillSummary(ConcertSummary summary, Concert concert) {
        summary.setId(concert.getId());
        summary.setSlug(concert.getSlug());
        summary.setTitle(concert.getTitle());
        summary.setStartAt(concert.getStartAt());
        summary.setEndAt(concert.getEndAt());
        summary.setTimeTba(concert.isTimeTba());
        summary.setStatus(toStatus(concert));
        summary.setGenre(genreSlug(concert));
        summary.setGenreLabel(genreLabel(concert));
        summary.setVenue(toVenueSummary(concert.getVenue()));
        summary.setArtists(toArtists(concert));
        summary.setFeatured(concert.isFeatured());
    }

    private void fillSummary(ConcertDetail detail, Concert concert) {
        detail.setId(concert.getId());
        detail.setSlug(concert.getSlug());
        detail.setTitle(concert.getTitle());
        detail.setStartAt(concert.getStartAt());
        detail.setEndAt(concert.getEndAt());
        detail.setTimeTba(concert.isTimeTba());
        detail.setStatus(toStatus(concert));
        detail.setGenre(genreSlug(concert));
        detail.setGenreLabel(genreLabel(concert));
        detail.setVenue(toVenueDetail(concert.getVenue()));
        detail.setArtists(toArtists(concert));
        detail.setFeatured(concert.isFeatured());
    }

    private List<com.theblastbeast.domain.vo.ConcertArtist> toArtists(Concert concert) {
        return concert.getArtists().stream().map(this::toArtist).toList();
    }

    private com.theblastbeast.domain.vo.ConcertArtist toArtist(
            com.theblastbeast.domain.entity.ConcertArtist artist) {
        com.theblastbeast.domain.vo.ConcertArtist response =
                new com.theblastbeast.domain.vo.ConcertArtist();
        response.setSlug(artist.getSlug());
        response.setName(artist.getName());
        response.setBandSlug(artist.getBand() == null ? null : artist.getBand().getSlug());
        return response;
    }

    private com.theblastbeast.domain.vo.ConcertStatus toStatus(Concert concert) {
        if (concert.getStatus() == null
                || concert.getStatus() == com.theblastbeast.domain.entity.ConcertStatus.ARCHIVED) {
            return com.theblastbeast.domain.vo.ConcertStatus.OFF_SALE;
        }
        return com.theblastbeast.domain.vo.ConcertStatus.valueOf(concert.getStatus().name());
    }

    private String genreSlug(Concert concert) {
        return StringUtils.hasText(concert.getGenreSlug())
                ? concert.getGenreSlug()
                : ConcertGenre.OTHER.slug();
    }

    private String genreLabel(Concert concert) {
        if (StringUtils.hasText(concert.getGenre())) {
            return concert.getGenre();
        }
        return ConcertGenre.labelForSlug(genreSlug(concert));
    }

    private String sourceLabel(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "TICKETMASTER" -> "Ticketmaster";
            case "DEMO" -> "Demo";
            default -> source;
        };
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
