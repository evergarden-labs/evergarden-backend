package com.evergarden.evergardenbackend.garden.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.garden.dto.Garden;
import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.GardenObjectType;
import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import com.evergarden.evergardenbackend.garden.repository.GardenObjectRepository;
import com.evergarden.evergardenbackend.garden.repository.UserGardenObjectRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 정원 조회(GARDEN-01) — 해금한 것만 담고, totalCount는 도감 전체를 센다. */
class GardenServiceTest {

    private static final Long USER_ID = 1L;

    private final UserGardenObjectRepository userGardenObjectRepository = mock(UserGardenObjectRepository.class);
    private final GardenObjectRepository gardenObjectRepository = mock(GardenObjectRepository.class);
    private final GardenService service = new GardenService(userGardenObjectRepository, gardenObjectRepository);

    private User user(Long id) {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserGardenObject unlocked(Long id, short stage) {
        GardenObject gardenObject = GardenObject.builder()
                .name("서울 나무").type(GardenObjectType.PLANT).maxStage((short) 3).build();
        ReflectionTestUtils.setField(gardenObject, "id", 100L);
        UserGardenObject userGardenObject = UserGardenObject.builder()
                .user(user(USER_ID)).gardenObject(gardenObject).unlockedAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(userGardenObject, "id", id);
        ReflectionTestUtils.setField(userGardenObject, "stage", stage);
        return userGardenObject;
    }

    @Test
    @DisplayName("해금한 게 없으면 objects는 비고 unlockedCount는 0, totalCount는 도감 전체 개수다")
    void 해금없음() {
        given(userGardenObjectRepository.findByUser_Id(USER_ID)).willReturn(List.of());
        given(gardenObjectRepository.count()).willReturn(5L);

        Garden result = service.getMyGarden(USER_ID);

        assertThat(result.objects()).isEmpty();
        assertThat(result.unlockedCount()).isZero();
        assertThat(result.totalCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("해금한 것만 담기고, unlockedCount는 그 개수와 같다")
    void 해금된것만_담김() {
        given(userGardenObjectRepository.findByUser_Id(USER_ID))
                .willReturn(List.of(unlocked(1L, (short) 1), unlocked(2L, (short) 2)));
        given(gardenObjectRepository.count()).willReturn(10L);

        Garden result = service.getMyGarden(USER_ID);

        assertThat(result.objects()).hasSize(2);
        assertThat(result.unlockedCount()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(10);
        assertThat(result.objects().get(0).stage()).isEqualTo(1);
        assertThat(result.objects().get(0).gardenObject().gardenObjectId()).isEqualTo(100L);
    }
}
