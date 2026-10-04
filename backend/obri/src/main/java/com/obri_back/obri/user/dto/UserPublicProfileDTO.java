package com.obri_back.obri.user.dto;

import com.obri_back.obri.user.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/*
 * 타인 공개 프로필 DTO — 닉네임·악기·활동 이력만 담는다. email·phoneNumber 같은 연락처와 가입일은 제외
 * (연락처는 지원 후 모집자에게만 공개되는 별도 정책이고, 가입일은 공개할 실익이 적다)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicProfileDTO {
    private String nickname;
    private String instrument;
    private List<CareerDTO> careers;

    // User 엔티티 → 공개 프로필 DTO 변환 (연락처 제외)
    public static UserPublicProfileDTO from(User user) {
        return UserPublicProfileDTO.builder()
                .nickname(user.getNickname())
                .instrument(user.getInstrument())
                .careers(user.getCareers().stream()
                        .map(CareerDTO::from)
                        .collect(Collectors.toList()))
                .build();
    }
}
