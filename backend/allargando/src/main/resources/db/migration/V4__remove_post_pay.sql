-- 유료 구인구직처럼 보이지 않도록 출연료(pay) 개념을 제거. 무보수 취미 모집으로 전환.

alter table "post" drop column pay;
