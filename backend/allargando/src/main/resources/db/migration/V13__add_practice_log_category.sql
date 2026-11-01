-- 연습일지 분류(기본기·곡 연습·레슨·기타). 선택 입력이라 비워 둘 수 있고, 기존 일지는 값 없이 그대로 둔다.
ALTER TABLE practice_log ADD COLUMN category VARCHAR(255);
