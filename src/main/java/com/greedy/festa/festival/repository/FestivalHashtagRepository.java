package com.greedy.festa.festival.repository;

import com.greedy.festa.festival.entity.FestivalHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalHashtagRepository extends JpaRepository<FestivalHashtag, Long> {
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM FestivalHashtag hashtag WHERE hashtag.festival.id = :festivalId")
    void deleteAllByFestivalId(@Param("festivalId") Long festivalId);
}
