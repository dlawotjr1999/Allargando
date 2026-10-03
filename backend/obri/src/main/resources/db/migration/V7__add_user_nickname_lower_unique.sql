-- 닉네임 중복을 대소문자 구분 없이 DB 레벨에서 막는다(D3). 닉네임이 신고·차단·프로필의 식별자인데 UNIQUE가 없어
-- 같은 닉네임 유저가 둘 생기면 단건 조회가 예외(500)를 던졌다. 표시는 입력한 대로 두고 판정만 lower() 기준으로 한다.
-- 함수 인덱스라 JPA(@Column(unique))로 표현할 수 없어 엔티티에는 주석으로 이 파일을 가리킨다.
-- 적용 전에 lower(nickname) 중복이 있으면 실패하므로 운영 DB에서는 docs/review/user.md "USER-T1 결과"의 조회를 먼저 돌린다.

create unique index UK_user_nickname_lower on "user" (lower(nickname));
