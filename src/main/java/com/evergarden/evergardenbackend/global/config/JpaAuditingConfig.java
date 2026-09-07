package com.evergarden.evergardenbackend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@link com.evergarden.evergardenbackend.global.entity.BaseTimeEntity}의
 * 생성·수정 시각을 채우기 위한 설정.
 *
 * <p>메인 클래스가 아니라 별도 설정으로 둔 이유는, 테스트에서 감사 기능만 빼고
 * 컨텍스트를 띄우고 싶을 때 이 설정만 제외하면 되기 때문이다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
