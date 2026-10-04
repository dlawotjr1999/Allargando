-- 연습일지(practice_log) 백엔드 구현(BACKLOG.md, 연습일지 CRUD) — 프론트 요구사항에 맞춰 title 컬럼 추가,
-- log_date·duration을 NOT NULL로, content 길이를 varchar(255)에서 varchar(4000)으로 확장(Post.description과 동일 방식).
--
-- PostgreSQL 이관 — MySQL의 "modify column"은 Postgres에 없어 alter column 절로 분리했다.
-- V1 직후 빈 테이블 상태에서만 실행되므로(baseline-on-migrate) not null 추가에 기존 행 위반 걱정은 없다.

alter table "practice_log" add column title varchar(255) not null;

alter table "practice_log" alter column log_date set not null;

alter table "practice_log" alter column duration set not null;

alter table "practice_log" alter column content type varchar(4000);
