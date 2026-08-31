package com.greedy.festa.host.repository;

import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class HostRepositoryFindAllOrderTest extends PostgresTestSupport {

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 주최_목록은_등록_역순으로_내려간다() {
        host("먼저 등록");
        host("나중 등록");
        host("가장 나중 등록");
        em.flush();

        List<String> 이름들 = hostRepository.findAllWithFestivalCount(PageRequest.of(0, 3))
                .map(row -> row.getHost().getName())
                .getContent();

        assertThat(이름들).containsExactly("가장 나중 등록", "나중 등록", "먼저 등록");
    }

    @Test
    void 페이지를_넘겨도_같은_주최가_겹치지_않는다() {
        host("첫째");
        host("둘째");
        host("셋째");
        host("넷째");
        em.flush();

        List<String> 첫_페이지 = 이름들(0);
        List<String> 둘째_페이지 = 이름들(1);

        assertThat(첫_페이지).containsExactly("넷째", "셋째");
        assertThat(둘째_페이지).containsExactly("둘째", "첫째");
    }

    private List<String> 이름들(int page) {
        return hostRepository.findAllWithFestivalCount(PageRequest.of(page, 2))
                .map(row -> row.getHost().getName())
                .getContent();
    }

    private void host(String name) {
        em.persist(Host.builder().name(name).region("서울").build());
    }
}
