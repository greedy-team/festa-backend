package com.greedy.festa.festival.repository;

import com.greedy.festa.festival.entity.Festival;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface FestivalRepository extends JpaRepository<Festival, Long> {

    @Query(value = """
            SELECT f AS festival, h AS host, COUNT(l) AS lineupCount
            FROM Festival f
            LEFT JOIN f.host h
            LEFT JOIN Lineup l ON l.festival = f
            WHERE (:published IS NULL
                   OR (:published = TRUE  AND f.publishedAt IS NOT NULL)
                   OR (:published = FALSE AND f.publishedAt IS NULL))
              AND (:hostId IS NULL OR h.id = :hostId)
              AND (:yearStart IS NULL
                   OR (f.startDate >= :yearStart AND f.startDate < :nextYearStart))
              AND (:q IS NULL
                   OR LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')))
              AND (:discovery IS NULL OR f.discovery = :discovery)
            GROUP BY f, h
            """,
            countQuery = """
            SELECT COUNT(f)
            FROM Festival f
            LEFT JOIN f.host h
            WHERE (:published IS NULL
                   OR (:published = TRUE  AND f.publishedAt IS NOT NULL)
                   OR (:published = FALSE AND f.publishedAt IS NULL))
              AND (:hostId IS NULL OR h.id = :hostId)
              AND (:yearStart IS NULL
                   OR (f.startDate >= :yearStart AND f.startDate < :nextYearStart))
              AND (:q IS NULL
                   OR LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')))
              AND (:discovery IS NULL OR f.discovery = :discovery)
            """)
    Page<FestivalWithLineupCount> findReviewRows(
            @Param("published") Boolean published,
            @Param("hostId") Long hostId,
            @Param("yearStart") LocalDate yearStart,
            @Param("nextYearStart") LocalDate nextYearStart,
            @Param("q") String q,
            @Param("discovery") String discovery,
            Pageable pageable
    );

    @Query("""
            SELECT f AS festival, h AS host, COUNT(l) AS lineupCount
            FROM Festival f
            LEFT JOIN f.host h
            LEFT JOIN Lineup l ON l.festival = f
            WHERE f.id IN :festivalIds
            GROUP BY f, h
            """)
    List<FestivalWithLineupCount> findPublishTargets(@Param("festivalIds") Collection<Long> festivalIds);

    @Query("SELECT COUNT(l) FROM Lineup l WHERE l.festival.id = :festivalId")
    long countLineupsByFestivalId(@Param("festivalId") Long festivalId);

    List<Festival> findAllByImportKeyIn(Collection<String> importKeys);

    @Query("""
        SELECT f
        FROM Festival f
        JOIN FETCH f.host
        WHERE f.publishedAt IS NOT NULL
          AND f.endDate >= :today
        ORDER BY f.startDate ASC, f.id ASC
        """)
    List<Festival> findPublishedNotEnded(@Param("today") LocalDate today, Limit limit);

    @Query("""
        SELECT f
        FROM Festival f
        JOIN FETCH f.host
        WHERE f.publishedAt IS NOT NULL
        ORDER BY f.publishedAt DESC, f.id DESC
        """)
    List<Festival> findRecentlyPublished(Limit limit);
}
