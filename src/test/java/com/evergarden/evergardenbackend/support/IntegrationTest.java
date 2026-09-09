package com.evergarden.evergardenbackend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 PostgreSQL·Redis를 띄우고 애플리케이션 전체를 올리는 테스트의 밑판.
 *
 * <p>이 클래스를 상속하면 Flyway 마이그레이션이 실행되고
 * {@code ddl-auto=validate}가 엔티티와 스키마를 대조한다.
 * 둘이 어긋나면 컨텍스트가 뜨지 않아 테스트가 깨진다.
 *
 * <p><b>도커가 떠 있어야 한다.</b> 없으면 컨테이너를 만들지 못해 실패한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(ContainerConfig.class)
public abstract class IntegrationTest {
}
