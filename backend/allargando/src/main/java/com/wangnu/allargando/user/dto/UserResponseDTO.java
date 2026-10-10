package com.wangnu.allargando.user.dto;

import com.wangnu.allargando.user.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/*
 * 내 정보 응답 DTO — 본인 조회용이라 phoneNumber 등 비공개 필드까지 포함
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private Long id;
    private String phoneNumber;
    private String name;
    private String nickname;
    private String instrument;
    private LocalDateTime createdAt;
    private List<CareerDTO> careers;

    // User 엔티티 → 내 정보 응답 DTO 변환 (경력 포함)
    public static UserResponseDTO from(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .phoneNumber(user.getPhoneNumber())
                .name(user.getName())
                .nickname(user.getNickname())
                .instrument(user.getInstrument())
                .createdAt(user.getCreatedAt())
                .careers(user.getCareers().stream()
                        .map(CareerDTO::from)
                        .collect(Collectors.toList()))
                .build();
        }
}
