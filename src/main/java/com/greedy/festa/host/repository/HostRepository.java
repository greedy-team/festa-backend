package com.greedy.festa.host.repository;

import com.greedy.festa.host.entity.Host;
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
public interface HostRepository extends JpaRepository<Host, Long> {

    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
    List<Host> findAllByNameIn(Collection<String> names);

    @Query(value = "SELECT h AS host, COUNT(f) AS festivalCount " +
            "FROM Host h " +
            "LEFT JOIN Festival f ON f.host = h " +
            "GROUP BY h " +
            "ORDER BY h.id DESC",
            countQuery = "SELECT COUNT(h) FROM Host h")
    Page<HostWithFestivalCount> findAllWithFestivalCount(Pageable pageable);

    @Query("SELECT count(f) FROM Festival f WHERE f.host.id = :hostId")
    long countFestivalsByHostId(@Param("hostId") Long hostId);

    @Query(value = """
            SELECT h.id AS "hostId",
                   h.name AS "hostName",
                   h.instagram_url AS "instagramUrl",
                   review_festival.id AS "festivalId",
                   review_festival.name AS "festivalName",
                   review_festival.start_date AS "startDate",
                   review_festival.end_date AS "endDate",
                   (review_festival.id IS NOT NULL) AS "hasUnpublishedFestival",
                   (current_festival.id IS NOT NULL) AS "hasCurrentFestival"
            FROM host h
            LEFT JOIN LATERAL (
                SELECT f.id, f.name, f.start_date, f.end_date
                FROM festival f
                WHERE f.host_id = h.id
                  AND f.start_date >= :yearStart
                  AND f.start_date < :nextYearStart
                  AND f.published_at IS NULL
                ORDER BY f.start_date ASC, f.id ASC
                LIMIT 1
            ) review_festival ON TRUE
            LEFT JOIN LATERAL (
                SELECT f.id
                FROM festival f
                WHERE f.host_id = h.id
                  AND f.start_date >= :yearStart
                  AND f.start_date < :nextYearStart
                  AND f.end_date >= :today
                  AND f.published_at IS NOT NULL
                ORDER BY f.start_date ASC, f.id ASC
                LIMIT 1
            ) current_festival ON TRUE
            """, nativeQuery = true)
    List<HostCoverageRow> findCoverageRows(
            @Param("yearStart") LocalDate yearStart,
            @Param("nextYearStart") LocalDate nextYearStart,
            @Param("today") LocalDate today
    );
}
