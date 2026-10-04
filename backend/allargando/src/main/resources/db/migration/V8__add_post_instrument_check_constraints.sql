-- post_instrument 정원 불변식을 DB 레벨에서도 지킨다(GLB-12·POST-13). 애플리케이션 검증(Post/PostInstrument)을 놓치는
-- 경로가 생겨도 people < confirmed 같은 상태가 저장되지 않게 하는 마지막 방어선이다.
--   people >= 1               모집 인원은 1명 이상
--   confirmed >= 0            확정 인원은 음수 불가
--   confirmed <= people       확정 인원이 모집 인원을 넘을 수 없음
-- 위반하는 기존 행이 있으면 추가가 실패하므로 운영 DB에서는 먼저 아래 조회가 0행인지 확인한다:
--   select id, post_id, instrument, people, confirmed from post_instrument
--    where people < 1 or confirmed < 0 or confirmed > people;
-- Hibernate의 ddl-auto=validate는 CHECK를 검증하지 않으므로 엔티티 변경은 필요 없다.

alter table "post_instrument" add constraint CK_post_instrument_people_positive check (people >= 1);

alter table "post_instrument" add constraint CK_post_instrument_confirmed_non_negative check (confirmed >= 0);

alter table "post_instrument" add constraint CK_post_instrument_confirmed_within_people check (confirmed <= people);
