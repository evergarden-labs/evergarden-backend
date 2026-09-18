package com.evergarden.evergardenbackend.place.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * 장소 데이터를 가져오는 외부 API 두 곳의 클라이언트 설정({@code docs/place-data-sync.md}).
 *
 * <p>{@code RestClient.Builder}를 주입받지 않고 직접 만든다 — Boot 4는 자동설정이
 * 스타터별로 잘게 나뉘어 있어({@code build.gradle}의 flyway 주석 참고), 지금 의존성
 * 구성에는 그 빈을 만들어주는 자동설정이 없다.
 */
@Configuration
@EnableConfigurationProperties({TourApiProperties.class, KakaoLocalProperties.class})
@RequiredArgsConstructor
public class ExternalApiConfig {

    private final TourApiProperties tourApiProperties;
    private final KakaoLocalProperties kakaoLocalProperties;

    /**
     * TourAPI는 결과가 없으면 {@code items}가 객체가 아니라 빈 문자열({@code ""})로 온다 —
     * 실전에서 {@code areaBasedList2}로 결과 없는 지역·타입 조합을 부를 때
     * {@code InvalidFormatException}으로 확인된 문제다. 이 코덱에서만 "빈 문자열이면
     * null로 봐준다"는 관용도를 켜서, 우리 응답 모양(record)은 그대로 두고 여기서 흡수한다.
     */
    @Bean
    public RestClient tourApiRestClient() {
        JsonMapper tourApiJsonMapper = JsonMapper.builder()
                .withCoercionConfig(LogicalType.POJO,
                        cfg -> cfg.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull))
                .build();
        return RestClient.builder()
                .baseUrl(tourApiProperties.baseUrl())
                .configureMessageConverters(converters ->
                        converters.withJsonConverter(new JacksonJsonHttpMessageConverter(tourApiJsonMapper)))
                .build();
    }

    @Bean
    public RestClient kakaoLocalRestClient() {
        return RestClient.builder()
                .baseUrl(kakaoLocalProperties.baseUrl())
                .defaultHeader("Authorization", "KakaoAK " + kakaoLocalProperties.restApiKey())
                .build();
    }
}
