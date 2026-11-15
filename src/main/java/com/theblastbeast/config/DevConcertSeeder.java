package com.theblastbeast.config;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Concert;
import com.theblastbeast.domain.entity.ConcertArtist;
import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.domain.entity.Venue;
import com.theblastbeast.repository.BandRepository;
import com.theblastbeast.repository.ConcertRepository;
import com.theblastbeast.repository.VenueRepository;
import com.theblastbeast.service.ConcertGenre;
import com.theblastbeast.service.SlugService;
import com.theblastbeast.util.MadridTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Siembra una agenda de ejemplo en desarrollo cuando Ticketmaster está
 * desactivado. Permite probar filtros, calendario y detalle sin clave de API.
 */
@Slf4j
@Component
@Profile({"dev", "local"})
@ConditionalOnProperty(
        prefix = "blastbeast.ticketmaster",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
@RequiredArgsConstructor
public class DevConcertSeeder implements ApplicationRunner {

    private static final String SOURCE = "DEMO";

    private final ConcertRepository concertRepository;
    private final VenueRepository venueRepository;
    private final BandRepository bandRepository;
    private final SlugService slugService;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        if (concertRepository.count() > 0) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<DemoConcert> concerts = List.of(
                new DemoConcert("demo-metallica-madrid", "Metallica: M72 World Tour",
                        "Metallica", "metallica", "Movistar Arena", "Madrid", "Madrid",
                        "Calle de Raimundo Fernández Villaverde, 61", 18, ConcertGenre.METAL,
                        65.0, 145.0, true),
                new DemoConcert("demo-ghost-barcelona", "Ghost: Skeletá Tour",
                        "Ghost", "ghost", "Palau Sant Jordi", "Barcelona", "Cataluña",
                        "Passeig Olímpic, 5-7", 32, ConcertGenre.METAL,
                        55.0, 110.0, true),
                new DemoConcert("demo-gojira-bilbao", "Gojira en directo",
                        "Gojira", "gojira", "Bilbao Arena", "Bilbao", "País Vasco",
                        "Askatasuna zumarkalea, 13", 47, ConcertGenre.METAL,
                        45.0, 75.0, false),
                new DemoConcert("demo-opeth-valencia", "Opeth: The Last Will and Testament",
                        "Opeth", "opeth", "Roig Arena", "Valencia", "Comunidad Valenciana",
                        "Avinguda de les Corts Valencianes, 60", 61, ConcertGenre.METAL,
                        50.0, 90.0, false),
                new DemoConcert("demo-judas-priest-sevilla", "Judas Priest: Shield of Pain",
                        "Judas Priest", "judas-priest", "Auditorio Rocío Jurado", "Sevilla",
                        "Andalucía", "Isla de la Cartuja", 76, ConcertGenre.METAL,
                        60.0, 120.0, false),
                new DemoConcert("demo-sabaton-a-coruna", "Sabaton: Legends of the Great War",
                        "Sabaton", "sabaton", "Coliseum da Coruña", "A Coruña", "Galicia",
                        "Rúa de Manuel Murguía, 3", 92, ConcertGenre.METAL,
                        48.0, 85.0, false));

        for (DemoConcert demo : concerts) {
            seed(demo, now);
        }
        log.info("Sembrados {} conciertos de demostración", concerts.size());
    }

    private void seed(DemoConcert demo, OffsetDateTime now) {
        String citySlug = slugService.slugify(demo.city());
        String venueExternalId = "demo-venue-" + citySlug + "-"
                + slugService.slugify(demo.venueName());
        Venue venue = venueRepository.findBySourceAndExternalId(SOURCE, venueExternalId)
                .orElseGet(() -> {
                    Venue created = new Venue();
                    created.setSource(SOURCE);
                    created.setExternalId(venueExternalId);
                    created.setName(demo.venueName());
                    created.setCity(demo.city());
                    created.setCitySlug(citySlug);
                    created.setRegion(demo.region());
                    created.setCountryCode("ES");
                    created.setAddress(demo.address());
                    created.setTimezone("Europe/Madrid");
                    return venueRepository.save(created);
                });

        OffsetDateTime startAt = MadridTime.startOfDay(
                        MadridTime.today(clock).plusDays(demo.daysFromNow()))
                .plusHours(21);
        String slug = slugService.unique(demo.externalId(), concertRepository::existsBySlug);

        Concert concert = new Concert();
        concert.setSource(SOURCE);
        concert.setExternalId(demo.externalId());
        concert.setSlug(slug);
        concert.setTitle(demo.title());
        concert.setDescription("Concierto de demostración de la agenda de The Blast Beast.");
        concert.setStartAt(startAt);
        concert.setEndAt(startAt.plusHours(3));
        concert.setTimeTba(false);
        concert.setStatus(ConcertStatus.ON_SALE);
        concert.setGenre(demo.genre().label());
        concert.setGenreSlug(demo.genre().slug());
        concert.setSubgenre(demo.genre().label());
        concert.setTicketUrl("https://www.ticketmaster.es/");
        concert.setSourceUrl("https://www.ticketmaster.es/");
        concert.setPriceMin(BigDecimal.valueOf(demo.priceMin()));
        concert.setPriceMax(BigDecimal.valueOf(demo.priceMax()));
        concert.setCurrency("EUR");
        concert.setVenue(venue);
        concert.setFeatured(demo.featured());
        concert.setHidden(false);
        concert.setLastSeenAt(now);

        ConcertArtist artist = new ConcertArtist();
        artist.setExternalId("demo-artist-" + demo.bandSlug());
        artist.setName(demo.artistName());
        artist.setSlug(demo.bandSlug());
        artist.setHeadliner(true);
        artist.setSortOrder(0);
        artist.setBand(bandRepository.findBySlug(demo.bandSlug()).orElse(null));
        concert.addArtist(artist);

        concertRepository.save(concert);
    }

    private record DemoConcert(
            String externalId,
            String title,
            String artistName,
            String bandSlug,
            String venueName,
            String city,
            String region,
            String address,
            int daysFromNow,
            ConcertGenre genre,
            double priceMin,
            double priceMax,
            boolean featured) {

        private DemoConcert {
            city = city.trim();
            region = region.trim();
            address = address.trim();
            bandSlug = bandSlug.toLowerCase(Locale.ROOT);
        }
    }
}
