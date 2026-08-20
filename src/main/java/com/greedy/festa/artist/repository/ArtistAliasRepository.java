package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.ArtistAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistAliasRepository extends JpaRepository<ArtistAlias, Long> {

    List<ArtistAlias> findByArtistId(Long artistId);

    List<ArtistAlias> findByArtistIdIn(List<Long> artistIds);

    boolean existsByName(String name);

    boolean existsByNameAndArtistIdNot(String name, Long artistId);

    void deleteByArtistId(Long artistId);

    void deleteByArtistIdIn(List<Long> artistIds);

    void deleteByArtistIdAndName(Long artistId, String name);
}
