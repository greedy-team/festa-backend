package com.greedy.festa.host.repository;

import com.greedy.festa.host.entity.Host;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
            "GROUP BY h",
            countQuery = "SELECT COUNT(h) FROM Host h")
    Page<HostWithFestivalCount> findAllWithFestivalCount(Pageable pageable);

    @Query("SELECT count(f) FROM Festival f WHERE f.host.id = :hostId")
    long countFestivalsByHostId(@Param("hostId") Long hostId);
}
