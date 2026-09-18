package com.evergarden.evergardenbackend.place.client.dto;

import java.math.BigDecimal;

/** WGS84 좌표. TourAPI({@code mapx}/{@code mapy})와 카카오({@code x}/{@code y}) 둘 다 이 좌표계라 변환이 필요 없다. */
public record Coordinate(BigDecimal lat, BigDecimal lng) {
}
