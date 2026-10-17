-- 기존 BE translacat에 카쿠요무 공개 플랫폼/URL/장르만 추가한다.
-- 코어 schema와 소설 embedded/target/shared-cache SQL 다음에 실행한다.
-- ID를 지정하거나 기존 작품/회차/번역/플랫폼 설정을 덮어쓰지 않는다. 반복 실행 가능.
START TRANSACTION;

INSERT INTO platform(code, name, name_ja, name_ko, created_at, updated_at, created_by, updated_by)
SELECT 'KAKUYOMU', 'カクヨム', 'カクヨム', '카쿠요무', NOW(6), NOW(6), 'SYSTEM', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM platform WHERE code='KAKUYOMU');

INSERT INTO platform_url_template(platform_id, url_type, url_pattern, created_at, updated_at, created_by, updated_by)
SELECT p.id, seed.kind, seed.pattern, NOW(6), NOW(6), 'SYSTEM', 'SYSTEM'
FROM platform p
JOIN (
  SELECT 'DOMAIN' AS kind, 'https://kakuyomu.jp' AS pattern
  UNION ALL SELECT 'RANKING', 'https://kakuyomu.jp/rankings/%2$s/%1$s?work_variation=all&page=%3$d'
  UNION ALL SELECT 'SEARCH', 'https://kakuyomu.jp/search?q=%s&page=%d'
  UNION ALL SELECT 'NOVEL', 'https://kakuyomu.jp/works/%s'
  UNION ALL SELECT 'EPISODE', 'https://kakuyomu.jp/works/%s/episodes/%s'
) seed
WHERE p.code='KAKUYOMU' AND NOT EXISTS (
  SELECT 1 FROM platform_url_template old WHERE old.platform_id=p.id AND old.url_type=seed.kind
);

INSERT INTO genre(platform_id, identifier, name, name_ja, name_ko, created_at, updated_at, created_by, updated_by)
SELECT p.id, seed.identifier, seed.ja, seed.ja, seed.ko, NOW(6), NOW(6), 'SYSTEM', 'SYSTEM'
FROM platform p
JOIN (
  SELECT 'all' AS identifier, '総合' AS ja, '종합' AS ko
  UNION ALL SELECT 'fantasy', '異世界ファンタジー', '이세계 판타지'
  UNION ALL SELECT 'action', '現代ファンタジー', '현대 판타지'
  UNION ALL SELECT 'sf', 'SF', 'SF'
  UNION ALL SELECT 'love_story', '恋愛', '연애'
  UNION ALL SELECT 'romance', 'ラブコメ', '러브 코미디'
  UNION ALL SELECT 'drama', '現代ドラマ', '현대 드라마'
  UNION ALL SELECT 'horror', 'ホラー', '호러'
  UNION ALL SELECT 'mystery', 'ミステリー', '미스터리'
  UNION ALL SELECT 'nonfiction', 'エッセイ・ノンフィクション', '에세이·논픽션'
  UNION ALL SELECT 'history', '歴史・時代・伝奇', '역사·시대·전기'
  UNION ALL SELECT 'criticism', '創作論・評論', '창작론·평론'
  UNION ALL SELECT 'others', '詩・童話・その他', '시·동화·기타'
) seed
WHERE p.code='KAKUYOMU' AND NOT EXISTS (
  SELECT 1 FROM genre old WHERE old.platform_id=p.id AND old.identifier=seed.identifier
);

COMMIT;
