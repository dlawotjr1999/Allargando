package com.wangnu.allargando.user.dto;

import com.wangnu.allargando.user.entity.Career;
import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 경력은 단체명·설명이 모두 비어도 허용하되(혼자 연주한 경우), 아무 내용 없는 행은 저장하지 않는다
class CareerDTOTest {

    private final User user = User.builder().id(1L).build();

    private CareerDTO dto(String organization, String contexts) {
        return CareerDTO.builder().organization(organization).contexts(contexts).build();
    }

    @Test
    void toEntities_keepsRowWithOnlyContexts() {
        List<Career> result = CareerDTO.toEntities(user, List.of(dto("", "혼자 5년 연주")));

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.getOrganization()).isEmpty();
            assertThat(c.getContexts()).isEqualTo("혼자 5년 연주");
        });
    }

    @Test
    void toEntities_keepsRowWithOnlyOrganization() {
        List<Career> result = CareerDTO.toEntities(user, List.of(dto("동아리 오케스트라", null)));

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.getOrganization()).isEqualTo("동아리 오케스트라");
            assertThat(c.getContexts()).isEmpty(); // null이 아니라 빈 문자열로 저장
        });
    }

    @Test
    void toEntities_dropsRowsWithNoContent() {
        List<Career> result = CareerDTO.toEntities(user,
                Arrays.asList(dto("", ""), dto("  ", null), dto(null, "   "), dto("밴드", "베이스")));

        assertThat(result).singleElement().extracting(Career::getOrganization).isEqualTo("밴드");
    }

    @Test
    void toEntities_stripsSurroundingWhitespace() {
        List<Career> result = CareerDTO.toEntities(user, List.of(dto("  밴드  ", "\t베이스\n")));

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.getOrganization()).isEqualTo("밴드");
            assertThat(c.getContexts()).isEqualTo("베이스");
        });
    }

    @Test
    void toEntities_returnsEmptyListWhenNull() {
        assertThat(CareerDTO.toEntities(user, null)).isEmpty();
    }
}
