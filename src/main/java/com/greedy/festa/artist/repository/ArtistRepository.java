package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
    List<Artist> findAllByNameIn(Collection<String> names);
}
