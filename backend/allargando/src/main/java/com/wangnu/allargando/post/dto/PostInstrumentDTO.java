package com.wangnu.allargando.post.dto;

import com.wangnu.allargando.post.entity.PostInstrument;
import lombok.Builder;
import lombok.Getter;

/*
 * 모집 악기 응답 DTO (악기명·모집/확정 인원·마감 여부)
 */
@Getter
@Builder
public class PostInstrumentDTO {

    private Long id;
    private String instrument;
    private Integer people;
    private Integer confirmed;
    private Boolean closed;

    // PostInstrument 엔티티 → DTO 변환
    public static PostInstrumentDTO from(PostInstrument postInstrument) {
        return PostInstrumentDTO.builder()
                .id(postInstrument.getId())
                .instrument(postInstrument.getInstrument())
                .people(postInstrument.getPeople())
                .confirmed(postInstrument.getConfirmed())
                .closed(postInstrument.getClosed())
                .build();
    }
}