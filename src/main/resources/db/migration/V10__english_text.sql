-- 방문자 화면의 영어 지원. 운영진이 입력하는 내용에 영어 칸을 붙입니다.
--
-- 전부 NULL 허용입니다. 영어가 비어 있으면 프런트가 한국어 값을 대신 보여주므로
-- 서버에서 대체하지 않습니다. 길이는 프런트 입력칸과 같은 값으로 맞췄습니다.
-- 영어는 한국어보다 글자 수가 많아 한국어 칸보다 넉넉합니다.
--
-- 한 문장에 컬럼 하나씩 둡니다. 운영은 MySQL이지만 테스트는 H2로 돌아가는데,
-- H2는 ALTER TABLE 하나에 ADD COLUMN을 여러 개 적는 문법을 받지 않습니다.

ALTER TABLE booth ADD COLUMN name_en VARCHAR(40) NULL;
ALTER TABLE booth ADD COLUMN place_en VARCHAR(60) NULL;
ALTER TABLE booth ADD COLUMN description_en VARCHAR(1000) NULL;

ALTER TABLE notice ADD COLUMN title_en VARCHAR(60) NULL;
ALTER TABLE notice ADD COLUMN content_en VARCHAR(2000) NULL;

ALTER TABLE performance ADD COLUMN title_en VARCHAR(40) NULL;
ALTER TABLE performance ADD COLUMN description_en VARCHAR(1000) NULL;

ALTER TABLE facility ADD COLUMN name_en VARCHAR(40) NULL;
ALTER TABLE facility ADD COLUMN place_en VARCHAR(60) NULL;

ALTER TABLE stage ADD COLUMN name_en VARCHAR(40) NULL;

ALTER TABLE festival ADD COLUMN name_en VARCHAR(40) NULL;

-- 영어 도면. 한국어 도면과 같은 크기·같은 배치로 만들기 때문에 핀 좌표(퍼센트)는 공유합니다.
ALTER TABLE map_floorplan ADD COLUMN image_url_en VARCHAR(500) NULL;
