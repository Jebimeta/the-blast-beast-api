package com.theblastbeast.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "bands")
@Getter
@Setter
@NoArgsConstructor
@BatchSize(size = 32)
public class Band {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 80)
    private String country;

    @Column(name = "formed_year")
    private Integer formedYear;

    @Column(length = 60)
    private String genre;

    @Column(length = 300)
    private String website;

    @Column(name = "bandcamp_url", length = 300)
    private String bandcampUrl;

    @Column(name = "spotify_url", length = 300)
    private String spotifyUrl;
}
