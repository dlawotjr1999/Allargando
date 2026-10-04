-- 음대생 전용 프로필 필드 제거. 대상이 악기 취미생 전반으로 넓어지면서 학교 재학·졸업 정보는
-- 더 이상 도메인에 필요하지 않다. 학교 이메일 인증 기능 자체도 함께 제거.

alter table "user" drop constraint if exists "UK24kx9bfqv9yg16btcdo76hp9r";
alter table "user" drop column school_email;
alter table "user" drop column school_email_verified;
alter table "user" drop column school;
alter table "user" drop column is_graduate;
