package com.evergarden.evergardenbackend.archive.entity;

/**
 * 가져온 아카이브(ARCH-16)의 빈 자리 안내 하나. {@link ArchiveItem}을 만들지 않고
 * {@code archives.layout_template}에 배열로 저장한다(ADR-057) — 사진 없이 배치·순서만
 * 남겨야 하는데 {@code ArchiveItem.media}는 {@code NOT NULL}이라 항목으로 못 만든다.
 */
public record ArchiveLayoutSlot(short sortOrder, ArchiveLayout layout) {
}
