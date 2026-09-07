package com.evergarden.evergardenbackend.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성·수정 시각을 자동으로 채운다.
 *
 * <p>DB에도 {@code DEFAULT now()}가 걸려 있어 마이그레이션이나 관리 SQL로 직접 넣은 행도
 * 값이 비지 않는다. 애플리케이션이 쓰는 경로에서는 이 클래스가 채운다.
 *
 * <p>{@code post_likes}·{@code trip_regions}처럼 수정될 일이 없는 연결 테이블은
 * 이 클래스를 상속하지 않고 {@code createdAt}만 직접 둔다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
