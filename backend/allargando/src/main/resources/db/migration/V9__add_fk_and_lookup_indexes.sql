-- 조회·탈퇴 정리 쿼리가 쓰는 컬럼에 인덱스를 추가한다(RPT-T6·BLK-T1·POST-T11). PostgreSQL은 FK 컬럼에 인덱스를 자동으로
-- 만들지 않고, 기존 UNIQUE 제약은 맨 앞 컬럼 조회만 돕는다. 인덱스만 추가하므로 데이터·제약은 바뀌지 않는다.
--   report(target_type, target_id)   탈퇴 때 "탈퇴자가 쓴 글을 대상으로 한 신고" 삭제 — UNIQUE는 reporter_id가 맨 앞이라 못 씀
--   user_block(blocked_id)           탈퇴 때 "나를 차단한 기록" 삭제 — UNIQUE(blocker_id, blocked_id)는 blocker_id가 맨 앞
--   post_instrument(post_id)         글 상세·목록의 악기 조회와 EXISTS 악기 필터(측정 46.1ms → 15.1ms)
--   post(user_id)                    내 모집글 목록(/api/posts/me)·탈퇴 정리(측정 7.3ms → 0.05ms)
--   application(user_id)             내 지원 목록(/api/applications/me)·탈퇴 정리 — UNIQUE(post_id, user_id)는 post_id가 맨 앞
-- 이미 같은 이름의 인덱스가 있는 DB(수동 생성 등)에서도 실패하지 않도록 IF NOT EXISTS를 쓴다.

create index if not exists idx_report_target on "report" (target_type, target_id);

create index if not exists idx_user_block_blocked on "user_block" (blocked_id);

create index if not exists idx_post_instrument_post on "post_instrument" (post_id);

create index if not exists idx_post_user on "post" (user_id);

create index if not exists idx_application_user on "application" (user_id);
