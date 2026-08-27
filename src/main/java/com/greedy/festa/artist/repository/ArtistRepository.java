package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.LocalDate;
import java.util.List;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    List<Artist> findAllByIdNot(Long id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByNameIn(Collection<String> names);

    List<Artist> findAllByNameIn(Collection<String> names);

    @Query(value = """
            SELECT a AS artist, COUNT(f) AS appearanceCount
            FROM Artist a
            LEFT JOIN Lineup l ON l.artist = a
            LEFT JOIN Festival f ON f = l.festival
                 AND f.publishedAt IS NOT NULL
                 AND f.endDate < CURRENT_DATE
            WHERE (:needsReview IS NULL OR a.needsReview = :needsReview)
              AND (:genre IS NULL OR a.genre = :genre)
              AND (:q IS NULL
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR EXISTS (SELECT 1 FROM ArtistAlias al
                              WHERE al.artist = a
                                AND LOWER(al.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))))
            GROUP BY a
            """,
            countQuery = """
            SELECT COUNT(a)
            FROM Artist a
            WHERE (:needsReview IS NULL OR a.needsReview = :needsReview)
              AND (:genre IS NULL OR a.genre = :genre)
              AND (:q IS NULL
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR EXISTS (SELECT 1 FROM ArtistAlias al
                              WHERE al.artist = a
                                AND LOWER(al.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))))
            """)
    Page<ArtistWithAppearanceCount> findAllWithAppearanceCount(
            @Param("needsReview") Boolean needsReview,
            @Param("genre") ArtistGenre genre,
            @Param("q") String q,
            Pageable pageable
    );

    @Query(value = """
            SELECT a AS artist, COUNT(f) AS appearanceCount
            FROM Artist a
            LEFT JOIN Lineup l ON l.artist = a
            LEFT JOIN Festival f ON f = l.festival
                 AND f.publishedAt IS NOT NULL
                 AND f.endDate < :today
            WHERE (:genre IS NULL OR a.genre = :genre)
              AND (:q IS NULL
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR EXISTS (SELECT 1 FROM ArtistAlias al
                              WHERE al.artist = a
                                AND LOWER(al.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))))
            GROUP BY a
            ORDER BY COUNT(f) DESC, a.id ASC
            """,
            countQuery = """
            SELECT COUNT(a)
            FROM Artist a
            WHERE (:genre IS NULL OR a.genre = :genre)
              AND (:q IS NULL
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR EXISTS (SELECT 1 FROM ArtistAlias al
                              WHERE al.artist = a
                                AND LOWER(al.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))))
            """)
    Page<ArtistWithAppearanceCount> findPublicByAppearances(
            @Param("genre") ArtistGenre genre,
            @Param("q") String q,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @Query(value = """
            SELECT a AS artist, COUNT(f) AS appearanceCount
            FROM Artist a
            LEFT JOIN Lineup l ON l.artist = a
            LEFT JOIN Festival f ON f = l.festival
                 AND f.publishedAt IS NOT NULL
                 AND f.endDate < :today
            WHERE (:genre IS NULL OR a.genre = :genre)
              AND (:q IS NULL
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR EXISTS (SELECT 1 FROM ArtistAlias al
                              WHERE al.artist = a
                                AND LOWER(al.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))))
            GROUP BY a
            ORDER BY a.name ASC, a.id ASC
            """,
            countQuery = """
            SELECT COUNT(a)
            FROM Artist a
            WHERE (:genre IS NULL OR a.genre = :genre)
              AND (:q IS NULL
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR EXISTS (SELECT 1 FROM ArtistAlias al
                              WHERE al.artist = a
                                AND LOWER(al.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))))
            """)
    Page<ArtistWithAppearanceCount> findPublicByName(
            @Param("genre") ArtistGenre genre,
            @Param("q") String q,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @Query("""
            SELECT l.artist.id AS artistId,
                   f.id AS festivalId,
                   f.name AS festivalName,
                   h.shortName AS hostShortName,
                   f.endDate AS endDate
            FROM Lineup l
            JOIN l.festival f
            JOIN f.host h
            WHERE l.artist.id IN :artistIds
              AND f.publishedAt IS NOT NULL
              AND f.endDate < :today
            GROUP BY l.artist.id, f.id, f.name, h.shortName, f.endDate
            ORDER BY l.artist.id ASC, f.endDate DESC, f.id DESC
            """)
    List<ArtistRecentFestivalRow> findRecentFestivals(
            @Param("artistIds") Collection<Long> artistIds,
            @Param("today") LocalDate today
    );

    @Query("""
            SELECT COUNT(l) FROM Lineup l
            WHERE l.artist.id = :artistId
              AND l.festival.publishedAt IS NOT NULL
              AND l.festival.endDate < CURRENT_DATE
            """)
    long countAppearancesByArtistId(@Param("artistId") Long artistId);

    @Query("SELECT COUNT(l) FROM Lineup l WHERE l.artist.id = :artistId")
    long countLineupsByArtistId(@Param("artistId") Long artistId);

    // ORDER BY는 잠그는 순서를 고정해 서로 겹치는 병합 요청끼리 교착에 빠지지 않게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Artist a WHERE a.id IN :ids ORDER BY a.id")
    List<Artist> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT l.artist.id AS artistId, COUNT(l) AS appearanceCount
            FROM Lineup l
            WHERE l.festival.publishedAt IS NOT NULL
              AND l.festival.endDate < CURRENT_DATE
            GROUP BY l.artist.id
            """)
    List<ArtistAppearanceCount> countAllAppearances();

    @Query("""
            SELECT DISTINCT other.artist.id
            FROM Lineup mine
            JOIN Lineup other ON other.festival = mine.festival
            WHERE mine.artist.id = :artistId
              AND other.artist.id <> :artistId
            """)
    List<Long> findSameFestivalArtistIds(@Param("artistId") Long artistId);
}
