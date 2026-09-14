package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.ArtistFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class ArtistRepositoryDuplicateNameTest extends PostgresTestSupport {

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ArtistAliasRepository artistAliasRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 자기_이름을_자기_id로_조회하면_걸리지_않는다() {
        Artist 잔나비 = 아티스트를_넣는다("잔나비");
        반영한다();

        assertThat(artistRepository.existsByNameAndIdNot("잔나비", 잔나비.getId())).isFalse();
    }

    @Test
    void 다른_아티스트가_쓰는_이름을_내_id로_조회하면_걸린다() {
        아티스트를_넣는다("아이유");
        Artist 잔나비 = 아티스트를_넣는다("잔나비");
        반영한다();

        assertThat(artistRepository.existsByNameAndIdNot("아이유", 잔나비.getId())).isTrue();
    }

    @Test
    void 아무도_쓰지_않는_이름은_걸리지_않는다() {
        Artist 잔나비 = 아티스트를_넣는다("잔나비");
        반영한다();

        assertThat(artistRepository.existsByNameAndIdNot("없는 이름", 잔나비.getId())).isFalse();
    }

    @Test
    void 같은_아티스트의_다른_별칭은_걸리지_않는다() {
        Artist 다듀 = 아티스트를_넣는다("다이나믹 듀오");
        별칭을_넣는다(다듀, "다듀");
        별칭을_넣는다(다듀, "다이나믹");
        반영한다();

        assertThat(artistAliasRepository.existsByNameAndArtistIdNot("다듀", 다듀.getId())).isFalse();
        assertThat(artistAliasRepository.existsByNameAndArtistIdNot("다이나믹", 다듀.getId())).isFalse();
    }

    @Test
    void 다른_아티스트의_별칭을_내_id로_조회하면_걸린다() {
        Artist 다듀 = 아티스트를_넣는다("다이나믹 듀오");
        별칭을_넣는다(다듀, "다듀");
        Artist 잔나비 = 아티스트를_넣는다("잔나비");
        반영한다();

        assertThat(artistAliasRepository.existsByNameAndArtistIdNot("다듀", 잔나비.getId())).isTrue();
    }

    @Test
    void 아무_별칭도_쓰지_않는_이름은_걸리지_않는다() {
        Artist 다듀 = 아티스트를_넣는다("다이나믹 듀오");
        별칭을_넣는다(다듀, "다듀");
        반영한다();

        assertThat(artistAliasRepository.existsByNameAndArtistIdNot("없는 별칭", 다듀.getId())).isFalse();
    }

    private Artist 아티스트를_넣는다(String 이름) {
        Artist 아티스트 = ArtistFixture.artist(이름).build();
        em.persist(아티스트);
        return 아티스트;
    }

    private void 별칭을_넣는다(Artist 아티스트, String 이름) {
        em.persist(ArtistFixture.alias(아티스트, 이름).build());
    }

    private void 반영한다() {
        em.flush();
        em.clear();
    }
}
