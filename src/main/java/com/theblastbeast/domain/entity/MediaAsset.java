package com.theblastbeast.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
@BatchSize(size = 32)
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private MediaAsset parent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaKind kind;

    @Column(name = "storage_key", nullable = false, unique = true, length = 300)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    private Integer width;

    private Integer height;

    @Column(nullable = false)
    private long bytes;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(length = 200)
    private String credit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
