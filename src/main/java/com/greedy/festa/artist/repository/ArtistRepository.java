package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByNameIn(Collection<String> names);

    @Query(value = """
            SELECT a AS artist, COUNT(l) AS appearanceCount
            FROM Artist a
            LEFT JOIN Lineup l ON l.artist = a
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

    @Query("SELECT COUNT(l) FROM Lineup l WHERE l.artist.id = :artistId")
    long countAppearancesByArtistId(@Param("artistId") Long artistId);
}
