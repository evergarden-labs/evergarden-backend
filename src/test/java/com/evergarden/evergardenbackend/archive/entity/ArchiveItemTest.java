package com.evergarden.evergardenbackend.archive.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.media.entity.Media;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchiveItemTest {

    private ArchiveItem item() {
        return ArchiveItem.builder()
                .archive(mock(Archive.class))
                .media(mock(Media.class))
                .sortOrder((short) 0)
                .layout(ArchiveLayout.of(0, 0, 0.5, 0.5))
                .caption("원래 캡션")
                .build();
    }

    @Test
    @DisplayName("update — 보낸 필드만 바뀐다")
    void update_보낸_필드만() {
        ArchiveItem item = item();
        ArchiveLayout newLayout = ArchiveLayout.of(0.1, 0.1, 0.4, 0.4);

        item.update(null, newLayout, null);

        assertThat(item.getSortOrder()).isZero();
        assertThat(item.getLayout()).isEqualTo(newLayout);
        assertThat(item.getCaption()).isEqualTo("원래 캡션");
    }

    @Test
    @DisplayName("update — sortOrder·caption도 각각 부분 수정된다")
    void update_sortOrder와_caption() {
        ArchiveItem item = item();

        item.update((short) 3, null, "새 캡션");

        assertThat(item.getSortOrder()).isEqualTo((short) 3);
        assertThat(item.getCaption()).isEqualTo("새 캡션");
    }

    @Test
    @DisplayName("update — 전부 null이면 아무것도 안 바뀐다")
    void update_전부_null() {
        ArchiveItem item = item();
        ArchiveLayout original = item.getLayout();

        item.update(null, null, null);

        assertThat(item.getSortOrder()).isZero();
        assertThat(item.getLayout()).isEqualTo(original);
        assertThat(item.getCaption()).isEqualTo("원래 캡션");
    }

    @Test
    @DisplayName("reorder — sortOrder·layout을 한꺼번에 덮어쓴다 (null 무시 없음)")
    void reorder_통째로_덮어씀() {
        ArchiveItem item = item();
        ArchiveLayout newLayout = ArchiveLayout.of(0.2, 0.2, 0.3, 0.3);

        item.reorder((short) 5, newLayout);

        assertThat(item.getSortOrder()).isEqualTo((short) 5);
        assertThat(item.getLayout()).isEqualTo(newLayout);
    }
}
