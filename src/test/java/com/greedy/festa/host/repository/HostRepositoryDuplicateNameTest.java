package com.greedy.festa.host.repository;

import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.HostFixture;
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
class HostRepositoryDuplicateNameTest extends PostgresTestSupport {

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 자기_이름을_자기_id로_조회하면_걸리지_않는다() {
        Host 가상대 = 주최를_넣는다("가상대학교");
        반영한다();

        assertThat(hostRepository.existsByNameAndIdNot("가상대학교", 가상대.getId())).isFalse();
    }

    @Test
    void 다른_주최가_쓰는_이름을_내_id로_조회하면_걸린다() {
        주최를_넣는다("허구대학교");
        Host 가상대 = 주최를_넣는다("가상대학교");
        반영한다();

        assertThat(hostRepository.existsByNameAndIdNot("허구대학교", 가상대.getId())).isTrue();
    }

    @Test
    void 아무도_쓰지_않는_이름은_걸리지_않는다() {
        Host 가상대 = 주최를_넣는다("가상대학교");
        반영한다();

        assertThat(hostRepository.existsByNameAndIdNot("아무도 안 쓰는 대학교", 가상대.getId())).isFalse();
    }

    private Host 주최를_넣는다(String 이름) {
        Host 주최 = HostFixture.host(이름).build();
        em.persist(주최);
        return 주최;
    }

    private void 반영한다() {
        em.flush();
        em.clear();
    }
}
