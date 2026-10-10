-- 가입 때 받는 이름과 약관·개인정보 동의 시각을 user에 추가한다.
--   name             모집자에게만 보이는 실명(지원자 응답). 닉네임과 달리 UNIQUE가 아니다(동명이인).
--   terms_agreed_at  이용약관·개인정보 수집에 동의한 시각. 서버가 가입 요청을 처리한 시각을 기록한다.
-- 개발·운영 DB 모두 비어 있는 상태에서 적용하는 것을 전제로 NOT NULL로 추가한다(기본값 없음).
-- 행이 남은 DB에서는 이 마이그레이션이 실패한다 — 그때는 행을 정리하거나 기본값을 넣은 새 마이그레이션을 쓴다.

alter table "user" add column name varchar(50) not null;
alter table "user" add column terms_agreed_at timestamp(6) not null;
