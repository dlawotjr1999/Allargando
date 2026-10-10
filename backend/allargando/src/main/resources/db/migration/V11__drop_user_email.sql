-- 서버는 이메일을 받지도 저장하지도 않는다. 로그인 아이디는 Firebase에서 내부 이메일 형식으로 바꿔 쓰고,
-- 계정 고유성은 전화번호(UNIQUE)가 맡는다(CLAUDE.md §3.1). UNIQUE 제약은 컬럼과 함께 사라진다.
-- 첫 배포 전이라 쓰는 곳이 없다(어떤 응답에도 나가지 않고 중복 검사에만 쓰였다).

alter table "user" drop column email;
