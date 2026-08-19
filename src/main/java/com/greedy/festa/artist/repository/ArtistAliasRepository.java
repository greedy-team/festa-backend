package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.ArtistAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArtistAliasRepository extends JpaRepository<ArtistAlias, Long> {
    @Query("SELECT aa FROM ArtistAlias aa JOIN FETCH aa.artist WHERE aa.name IN :names")
    List<ArtistAlias> findAllWithArtistByNameIn(@Param("names") Collection<String> names);

    @Query("SELECT aa FROM ArtistAlias aa WHERE aa.artist.id IN :artistIds")
    List<ArtistAlias> findAllByArtistIdIn(@Param("artistIds") Collection<Long> artistIds);
}
