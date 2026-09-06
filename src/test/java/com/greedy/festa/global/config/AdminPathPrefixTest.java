package com.greedy.festa.global.config;

import com.greedy.festa.admin.repository.AdminUserRepository;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.repository.FestivalHashtagRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import com.greedy.festa.importer.repository.ImportCommitRowRepository;
import com.greedy.festa.lineup.repository.LineupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "app.jwt.secret=test-jwt-secret",
        "app.jwt.admin-secret=ZmVzdGEtYWRtaW4tand0LXRlc3Qtc2VjcmV0LWtleS0zMg==",
        "app.crypto.aes-key=test-aes-key"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("NonAsciiCharacters")
class AdminPathPrefixTest {

    private static final String 애플리케이션_패키지 = "com.greedy.festa";
    private static final String 관리자_접두어 = "/api/admin/";
    private static final String API_접두어 = "/api/";
    private static final String 관리자_컨트롤러_표식 = "Admin";

    @Autowired
    private List<RequestMappingHandlerMapping> 핸들러_매핑들;

    @MockitoBean
    private HostRepository hostRepository;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private ArtistRepository artistRepository;

    @MockitoBean
    private ArtistAliasRepository artistAliasRepository;

    @MockitoBean
    private LineupRepository lineupRepository;

    @MockitoBean
    private FestivalRepository festivalRepository;

    @MockitoBean
    private ImportBatchRepository importBatchRepository;

    @MockitoBean
    private ImportCommitRowRepository importCommitRowRepository;

    @MockitoBean
    private FestivalHashtagRepository festivalHashtagRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void 관리자_컨트롤러의_모든_경로는_api_admin_접두어_아래에_있다() {
        // given
        List<매핑> 전체 = 애플리케이션_매핑();

        // when
        List<String> 위반 = 전체.stream()
                .filter(매핑::관리자_컨트롤러다)
                .filter(하나 -> !하나.경로().startsWith(관리자_접두어))
                .map(매핑::표기)
                .sorted()
                .toList();

        // then
        assertThat(위반)
                .as("SecurityConfig의 인가 매처는 requestMatchers(\"/api/admin/**\").authenticated() 한 벌뿐이고 "
                        + "나머지는 anyRequest().permitAll()이다. 이 접두어 밖에 매핑된 관리자 경로는 "
                        + "401이 아니라 200으로 조용히 공개된다")
                .isEmpty();
    }

    @Test
    void api_admin_아래의_경로는_모두_관리자_컨트롤러가_들고_있다() {
        // given
        List<매핑> 전체 = 애플리케이션_매핑();

        // when
        List<String> 위반 = 전체.stream()
                .filter(하나 -> 하나.경로().startsWith(관리자_접두어))
                .filter(하나 -> !하나.관리자_컨트롤러다())
                .map(매핑::표기)
                .sorted()
                .toList();

        // then
        assertThat(위반)
                .as("위 검사는 클래스명에 \"" + 관리자_컨트롤러_표식 + "\"이 든 것만 관리자 컨트롤러로 본다. "
                        + "이름에 그 표식이 없는 관리자 경로가 생기면 위 검사가 그 컨트롤러를 놓친다")
                .isEmpty();
    }

    @Test
    void 모든_경로는_api_접두어_아래에_있다() {
        // given
        List<매핑> 전체 = 애플리케이션_매핑();

        // when
        List<String> 위반 = 전체.stream()
                .filter(하나 -> !하나.경로().startsWith(API_접두어))
                .map(매핑::표기)
                .sorted()
                .toList();

        // then
        assertThat(위반)
                .as("모든 API 경로는 /api 접두사를 갖는다 (#60). 문서·액추에이터 경로는 이 검사 대상이 아니다 — "
                        + "선언 클래스가 " + 애플리케이션_패키지 + " 패키지인 핸들러만 본다")
                .isEmpty();
    }

    private List<매핑> 애플리케이션_매핑() {
        List<매핑> 결과 = new ArrayList<>();
        for (RequestMappingHandlerMapping 매핑기 : 핸들러_매핑들) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> 항목 : 매핑기.getHandlerMethods().entrySet()) {
                Class<?> 컨트롤러 = 항목.getValue().getBeanType();
                if (!컨트롤러.getName().startsWith(애플리케이션_패키지)) {
                    continue;
                }
                for (String 경로 : 항목.getKey().getPatternValues()) {
                    결과.add(new 매핑(컨트롤러.getSimpleName(), http_메서드(항목.getKey()), 경로));
                }
            }
        }
        return 결과.stream().distinct().toList();
    }

    private String http_메서드(RequestMappingInfo 정보) {
        Set<RequestMethod> 메서드들 = 정보.getMethodsCondition().getMethods();
        if (메서드들.isEmpty()) {
            return "ANY";
        }
        return 메서드들.stream().map(RequestMethod::name).sorted().collect(Collectors.joining("|"));
    }

    private record 매핑(String 컨트롤러, String http메서드, String 경로) {

        private boolean 관리자_컨트롤러다() {
            return 컨트롤러.contains(관리자_컨트롤러_표식);
        }

        private String 표기() {
            return 컨트롤러 + " → " + http메서드 + " " + 경로;
        }
    }
}
