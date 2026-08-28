package com.greedy.festa.host.controller;

import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.host.service.HostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@WebMvcTest(controllers = HostController.class,
        excludeAutoConfiguration = OAuth2ClientWebSecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("NonAsciiCharacters")
class HostControllerTest {

    private static final String 숫자가_아닌_경로 = "/api/hosts/abc";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private HostService hostService;

    @Test
    void 숫자가_아닌_id는_400과_INVALID_PATH_VARIABLE을_반환한다() {
        // when
        MvcTestResult 결과 = mvc.get().uri(숫자가_아닌_경로).exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().convertTo(ErrorResponse.class)
                .isEqualTo(new ErrorResponse(
                        "INVALID_PATH_VARIABLE",
                        "경로 변수의 형식이 올바르지 않습니다",
                        400,
                        숫자가_아닌_경로));
    }

    @Test
    void 숫자가_아닌_id는_서비스에_닿기_전에_걸러진다() {
        // when
        mvc.get().uri(숫자가_아닌_경로).exchange();

        // then
        then(hostService).shouldHaveNoInteractions();
    }
}
