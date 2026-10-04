package com.wangnu.allargando.post.dto;

import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.post.entity.PostInstrument;
import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// D11: manuallyClosed는 "모집 재개" 메뉴를 수동 마감 글에만 보이기 위한 값이라 작성자에게만 내려주고, 남에게는 null
class PostDetailResponseDTOTest {

    private Post manuallyClosedPost() {
        User owner = User.builder().id(1L).nickname("owner").instrument("바이올린").build();
        Post post = Post.create(owner, PostInfo.builder()
                .category("앙상블").title("t").location("서울").region("서울").timetable("13:00")
                .eventAt(LocalDateTime.now().plusDays(3)).build());
        post.addInstrument(PostInstrument.of(post, "바이올린", 2));
        post.close();
        return post;
    }

    @Test
    void from_exposesManuallyClosedToOwner() {
        PostDetailResponseDTO dto = PostDetailResponseDTO.from(manuallyClosedPost(), 0L, true, null);

        assertThat(dto.getManuallyClosed()).isTrue();
    }

    @Test
    void from_hidesManuallyClosedFromOthers() {
        PostDetailResponseDTO dto = PostDetailResponseDTO.from(manuallyClosedPost(), 0L, false, null);

        assertThat(dto.getManuallyClosed()).isNull();
    }

    // 자동 마감(정원 충족)은 수동 마감이 아니므로 작성자에게도 false — "모집 재개" 메뉴가 뜨지 않는다
    @Test
    void from_reportsFalseForOwnerWhenClosedByCapacityOnly() {
        User owner = User.builder().id(1L).nickname("owner").instrument("바이올린").build();
        Post post = Post.create(owner, PostInfo.builder()
                .category("앙상블").title("t").location("서울").region("서울").timetable("13:00")
                .eventAt(LocalDateTime.now().plusDays(3)).build());
        post.addInstrument(PostInstrument.of(post, "첼로", 1));
        post.confirmInstrument("첼로");

        PostDetailResponseDTO dto = PostDetailResponseDTO.from(post, 1L, true, null);

        assertThat(dto.getManuallyClosed()).isFalse();
    }
}
