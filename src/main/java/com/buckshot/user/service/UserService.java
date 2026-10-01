package com.buckshot.user.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PlayerProfileResponse getProfile(long userId) {
        return PlayerProfileResponse.from(findUser(userId));
    }

    @Transactional
    public PlayerProfileResponse changeNickname(long userId, String nickname) {
        User user = findUser(userId);
        if (nickname.equals(user.getNickname())) {
            return PlayerProfileResponse.from(user);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        user.changeNickname(nickname);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicated) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        return PlayerProfileResponse.from(user);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
