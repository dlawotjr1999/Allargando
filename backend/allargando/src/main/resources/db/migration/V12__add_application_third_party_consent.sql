-- 지원 시 받는 개인정보 제3자 제공 동의(이름·휴대전화번호를 수락된 모집자에게 공개)의 기록을 application에 추가한다.
--   third_party_consented_at  동의 일시. 서버가 지원을 처리한 시각을 기록한다.
--   consent_version           동의 당시 보여 준 문안의 버전(예: 2026-10-15).
-- 두 컬럼 모두 nullable로 추가한다 — 이 기능 이전의 지원은 동의 기록이 없고, 옛 이미지로 되돌려도 INSERT가 깨지지 않는다.
-- 기록이 없는 지원은 수락돼도 모집자 응답에서 연락처를 마스킹한다(Application.exposesApplicantContact).

alter table "application" add column third_party_consented_at timestamp(6);
alter table "application" add column consent_version varchar(20);
