package com.evergarden.evergardenbackend.trip.dto;

/** 지도를 이 범위에 맞추면 동선 전체가 들어온다. */
public record BoundingBox(double minLat, double minLng, double maxLat, double maxLng) {
}
