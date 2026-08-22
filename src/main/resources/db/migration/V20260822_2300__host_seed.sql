-- 주최 시드 — 크롤링 대상 대학 29곳
--
-- 원본: festa-crawler `universities-2026.csv` (develop 기준)
-- name·region은 그 파일의 `university`·`region` 컬럼을 문자 그대로 옮긴 값이다.
-- 크롤러가 CSV `host_name`에 내보내는 값이 바로 `university`이므로(crawl.py L122·L170)
-- 한 글자라도 다르면 임포트가 HOST_NOT_FOUND 블로커로 전부 막힌다. 임의로 고치지 않는다.
--
-- short_name은 통용 약칭이다. `X대학교`→`X대`, `X여자대학교`→`X여대`를 규칙으로 쓰고
-- 규칙이 어색해지는 둘만 예외로 뒀다 — 한국외국어대학교→한국외대, 서울과학기술대학교→서울과기대.
--
-- homepage_url·instagram_url은 시드에 없는 값이라 사람이 조사해 채웠다.
-- instagram_url은 학교 공식 계정이며 총학생회·학과·입학처 계정이 아니다.
-- 축제 계정은 festival.instagram_url에 따로 들어간다.
--
-- logo_url·banner_url은 비운다 — 관리자 몫이라서가 아니라 값이 아직 없다.
-- 로고·배너는 호스팅 주체가 미결이다. 정해지면 관리자 API로 채운다.
--
-- ON CONFLICT DO NOTHING: 이 파일은 첫 실행 시점의 출발 상태만 정하고
-- 이미 있는 행은 건드리지 않는다. 이후 값의 원본은 DB이며 이 파일이 아니다.
-- created_at·updated_at은 컬럼 기본값이 없어(NOT NULL만) 직접 채운다.

INSERT INTO host (name, short_name, region, homepage_url, instagram_url, created_at, updated_at) VALUES
    ('서울대학교', '서울대', '서울 관악구', 'https://www.snu.ac.kr', 'https://www.instagram.com/snu.official/', now(), now()),
    ('연세대학교', '연세대', '서울 서대문구', 'https://www.yonsei.ac.kr', 'https://www.instagram.com/yonsei_official/', now(), now()),
    ('고려대학교', '고려대', '서울 성북구', 'https://www.korea.ac.kr', 'https://www.instagram.com/koreauniv.official/', now(), now()),
    ('한양대학교', '한양대', '서울 성동구', 'https://www.hanyang.ac.kr', 'https://www.instagram.com/hanyang_univ/', now(), now()),
    ('건국대학교', '건국대', '서울 광진구', 'https://www.konkuk.ac.kr', 'https://www.instagram.com/konkuk_official/', now(), now()),
    ('중앙대학교', '중앙대', '서울 동작구', 'https://www.cau.ac.kr', 'https://www.instagram.com/chunganguniv/', now(), now()),
    ('홍익대학교', '홍익대', '서울 마포구', 'https://www.hongik.ac.kr', 'https://www.instagram.com/hongik_university/', now(), now()),
    ('경희대학교', '경희대', '서울 동대문구', 'https://www.khu.ac.kr', 'https://www.instagram.com/kyunghee_university/', now(), now()),
    ('세종대학교', '세종대', '서울 광진구', 'https://www.sejong.ac.kr', 'https://www.instagram.com/sejong_univ/', now(), now()),
    ('성균관대학교', '성균관대', '서울 종로구', 'https://www.skku.edu', 'https://www.instagram.com/skku.official/', now(), now()),
    ('서강대학교', '서강대', '서울 마포구', 'https://www.sogang.ac.kr', 'https://www.instagram.com/sogang_university/', now(), now()),
    ('한국외국어대학교', '한국외대', '서울 동대문구', 'https://www.hufs.ac.kr', 'https://www.instagram.com/hufs.official/', now(), now()),
    ('서울시립대학교', '서울시립대', '서울 동대문구', 'https://www.uos.ac.kr', 'https://www.instagram.com/uos.official/', now(), now()),
    ('동국대학교', '동국대', '서울 중구', 'https://www.dongguk.edu', 'https://www.instagram.com/dongguk_university/', now(), now()),
    ('숭실대학교', '숭실대', '서울 동작구', 'https://ssu.ac.kr', 'https://www.instagram.com/soongsil1897/', now(), now()),
    ('국민대학교', '국민대', '서울 성북구', 'https://www.kookmin.ac.kr', 'https://www.instagram.com/kookmin.univ/', now(), now()),
    ('광운대학교', '광운대', '서울 노원구', 'https://www.kw.ac.kr', 'https://www.instagram.com/kwangwoon_univ_official/', now(), now()),
    ('서울과학기술대학교', '서울과기대', '서울 노원구', 'https://www.seoultech.ac.kr', 'https://www.instagram.com/seoultech.official/', now(), now()),
    ('명지대학교', '명지대', '서울 서대문구', 'https://www.mju.ac.kr', 'https://www.instagram.com/myongji_univ/', now(), now()),
    ('상명대학교', '상명대', '서울 종로구', 'https://www.smu.ac.kr', 'https://www.instagram.com/sangmyung_univ/', now(), now()),
    ('이화여자대학교', '이화여대', '서울 서대문구', 'https://www.ewha.ac.kr', 'https://www.instagram.com/ewha.w.univ/', now(), now()),
    ('숙명여자대학교', '숙명여대', '서울 용산구', 'https://www.sookmyung.ac.kr', 'https://www.instagram.com/sookmyung_womens_univ/', now(), now()),
    ('성신여자대학교', '성신여대', '서울 성북구', 'https://www.sungshin.ac.kr', 'https://www.instagram.com/sungshin.official/', now(), now()),
    ('서울여자대학교', '서울여대', '서울 노원구', 'https://www.swu.ac.kr', 'https://www.instagram.com/swu.official/', now(), now()),
    ('동덕여자대학교', '동덕여대', '서울 성북구', 'https://www.dongduk.ac.kr', 'https://www.instagram.com/dongduk_w_univ/', now(), now()),
    ('덕성여자대학교', '덕성여대', '서울 도봉구', 'https://www.duksung.ac.kr', 'https://www.instagram.com/duksung_official/', now(), now()),
    ('한성대학교', '한성대', '서울 성북구', 'https://www.hansung.ac.kr', 'https://www.instagram.com/hansung_univ_official/', now(), now()),
    ('서경대학교', '서경대', '서울 성북구', 'https://www.skuniv.ac.kr', 'https://www.instagram.com/sku_skon/', now(), now()),
    ('삼육대학교', '삼육대', '서울 노원구', 'https://www.syu.ac.kr', 'https://www.instagram.com/sahmyook_university/', now(), now())
ON CONFLICT (name) DO NOTHING;
