package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.Lineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineupRepository extends JpaRepository<Lineup, Long> {
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Lineup lineup WHERE lineup.festival.id = :festivalId")
    void deleteAllByFestivalId(@Param("festivalId") Long festivalId);
}
