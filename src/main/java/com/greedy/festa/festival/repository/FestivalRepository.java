package com.greedy.festa.festival.repository;

import com.greedy.festa.festival.entity.Festival;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FestivalRepository extends JpaRepository<Festival, Long> {
    List<Festival> findAllByImportKeyIn(Collection<String> importKeys);
}
