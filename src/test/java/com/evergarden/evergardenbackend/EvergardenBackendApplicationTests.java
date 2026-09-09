package com.evergarden.evergardenbackend;

import com.evergarden.evergardenbackend.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 애플리케이션이 실제 PostgreSQL·Redis에 붙어 뜨는지 확인한다.
 *
 * <p>컨텍스트가 뜬다는 것은 Flyway 마이그레이션이 정상 실행됐고,
 * {@code ddl-auto=validate}가 엔티티 25개와 스키마를 대조해 통과했다는 뜻이다.
 * 컬럼 타입 하나만 어긋나도 여기서 깨진다.
 */
class EvergardenBackendApplicationTests extends IntegrationTest {

    @Test
    @DisplayName("애플리케이션이 뜨고 엔티티가 스키마와 일치한다")
    void 컨텍스트가_뜬다() {
    }
}
