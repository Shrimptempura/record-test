package com.mytest.oplib.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "도서/좌석 정보 OpenAPI",
                version = "v1",
                description = "주간 인기 대출 도서 & 실시간 좌석 조회 API 문서"
        )
)
public class OpenApiConfig {

}
