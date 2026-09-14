package com.greedy.festa.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI festaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("festa API")
                        .description("축제 정보 서비스 festa의 API 문서. "
                                + "/api/admin 으로 시작하는 경로는 관리자 로그인으로 받은 토큰이 필요하다. "
                                + "enum 쿼리 파라미터(genre·sort·status·type·onConflict)는 정식 값이 대문자이고, "
                                + "입력의 대소문자와 앞뒤 공백은 무시한다. 빈 값은 값을 주지 않은 것과 같게 다룬다. "
                                + "page·size는 숫자가 아닌 값을 400 INVALID_PAGE·INVALID_PAGE_SIZE로 돌려준다. "
                                + "음수 page와 범위 밖 size도 같은 코드다.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerAuth()));
    }

    private SecurityScheme bearerAuth() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }
}
