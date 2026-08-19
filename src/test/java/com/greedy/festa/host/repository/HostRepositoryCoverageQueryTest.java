package com.greedy.festa.host.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HostRepositoryCoverageQueryTest {

    @Test
    void coverage_query는_연도와_오늘을_필터링하고_대표_festival을_정렬한다() throws Exception {
        Method method = HostRepository.class.getMethod(
                "findCoverageRows", LocalDate.class, LocalDate.class, LocalDate.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query)
                .contains("f.start_date >= :yearStart")
                .contains("f.start_date < :nextYearStart")
                .contains("f.published_at IS NULL")
                .contains("f.end_date >= :today")
                .contains("ORDER BY f.start_date ASC, f.id ASC")
                .contains("LEFT JOIN LATERAL");
    }
}
