package com.evergarden.evergardenbackend.user.service;

import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.dto.PublicProfile;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 프로필 조회·검색(COMM-20 · ARCH-10). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    /**
     * 다른 사용자의 공개 프로필(COMM-20). 탈퇴·차단 회원은 존재 여부를 알리지 않고
     * 그냥 {@code USER_NOT_FOUND}다.
     */
    public PublicProfile getProfile(Long userId) {
        return toProfile(findVisibleUser(userId));
    }

    /**
     * 닉네임으로 정확히 일치하는 사용자를 찾는다(ARCH-10, ADR-024). 닉네임이 유일하므로
     * (ADR-025) 결과는 0개 아니면 1개다. 탈퇴·차단 회원은 결과에서 뺀다.
     */
    public List<PublicProfile> search(String nickname) {
        return userRepository.findByNickname(nickname)
                .filter(User::isActive)
                .map(user -> List.of(toProfile(user)))
                .orElse(List.of());
    }

    private PublicProfile toProfile(User user) {
        int postCount = (int) postRepository.countByAuthor_IdAndStatus(user.getId(), PostStatus.ACTIVE);
        return new PublicProfile(user.getId(), user.getNickname(), user.getProfileImageUrl(), postCount);
    }

    private User findVisibleUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
