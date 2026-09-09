package com.evergarden.evergardenbackend.archive.entity;

/**
 * 캔버스 위의 배치. 좌표와 크기는 캔버스 폭을 1.0으로 본 비율이다.
 *
 * <p>JSONB 컬럼에 통째로 저장한다. 항목마다 컬럼 다섯 개를 두는 대신
 * 한 덩어리로 다루는 이유는, 실시간 편집에서 이 값만 주고받기 때문이다(ADR-051).
 *
 * @param rotation 시계 방향 각도(도). 기본 0
 */
public record ArchiveLayout(
        double x,
        double y,
        double width,
        double height,
        double rotation) {

    public static ArchiveLayout of(double x, double y, double width, double height) {
        return new ArchiveLayout(x, y, width, height, 0);
    }
}
