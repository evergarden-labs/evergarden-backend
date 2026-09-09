package com.evergarden.evergardenbackend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트가 쓸 PostgreSQL·Redis 컨테이너.
 *
 * <p>{@code @ServiceConnection}이 붙어 있어 접속 정보를 따로 적을 필요가 없다.
 * Spring이 컨테이너가 뜬 포트를 읽어 데이터소스와 Redis 설정을 채운다.
 *
 * <p>컨테이너는 스프링 컨텍스트 캐시를 따라간다. 같은 설정을 쓰는 테스트끼리는
 * 컨테이너를 다시 띄우지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainerConfig {

    /** 운영과 같은 메이저 버전을 쓴다. 마이너 차이로 문법이 갈리는 일을 막는다. */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
