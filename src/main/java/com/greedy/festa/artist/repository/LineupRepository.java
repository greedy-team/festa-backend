package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.Lineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface LineupRepository extends JpaRepository<Lineup, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Lineup l SET l.artist = :target " +
            "WHERE l.artist.id in :sourceIds")
    int reassignArtist(@Param("target") Artist target, @Param("sourceIds") Collection<Long> sourceIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Lineup l " +
            "WHERE l.artist = :target " +
            "AND EXISTS (SELECT 1 FROM Lineup e " +
            "WHERE e.artist = :target AND e.festival = l.festival AND e.day = l.day AND e.displayOrder < l.displayOrder)")
    int removeDuplicates(@Param("target") Artist target);
}
