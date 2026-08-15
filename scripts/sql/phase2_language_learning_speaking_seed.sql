-- Phase 2 Language Learning Speaking initial data
-- MySQL idempotent seed: 10 topic categories + canonical system keywords + locale display names.

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'DAILY', 'DAILY', 'Daily Conversation', 'Everyday conversation practice',
       'B1', 'AI_FIRST', 1, 10, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'DAILY' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'TRAVEL', 'TRAVEL', 'Travel', 'Airport, hotel, transport and sightseeing conversation',
       'B1', 'AI_FIRST', 1, 20, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'TRAVEL' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'FOOD', 'FOOD', 'Food', 'Restaurant, cafe, cooking and food ordering conversation',
       'A2', 'AI_FIRST', 1, 30, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'FOOD' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'SHOPPING', 'SHOPPING', 'Shopping', 'Price, payment, exchange and delivery conversation',
       'A2', 'USER_FIRST', 1, 40, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'SHOPPING' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'BUSINESS', 'BUSINESS', 'Business', 'Meeting, schedule, customer service and presentation conversation',
       'B1', 'AI_FIRST', 1, 50, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'BUSINESS' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'IT', 'IT', 'IT', 'Development, API, database, deployment and incident conversation',
       'B1', 'USER_FIRST', 1, 60, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'IT' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'HOBBY', 'HOBBY', 'Hobby', 'Movie, music, reading and exercise conversation',
       'A2', 'AI_FIRST', 1, 70, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'HOBBY' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'GAME', 'GAME', 'Game', 'Cooperation, strategy, character and online game conversation',
       'A2', 'AI_FIRST', 1, 80, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'GAME' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'CULTURE', 'CULTURE', 'Culture', 'Festival, etiquette, local culture and language difference conversation',
       'B1', 'AI_FIRST', 1, 90, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'CULTURE' AND version = 1
);

INSERT INTO language_learning_speaking_topic
(topic_code, category, title, description, recommended_level,
 recommended_start_mode, active, sort_order, version, created_at, updated_at)
SELECT 'FREE_TALK', 'FREE_TALK', 'Free Talk', 'Open conversation without a fixed topic',
       'A2', 'USER_FIRST', 1, 100, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_speaking_topic
    WHERE topic_code = 'FREE_TALK' AND version = 1
);

CREATE TABLE IF NOT EXISTS language_learning_system_keyword_locale (
    id BIGINT NOT NULL AUTO_INCREMENT,
    system_keyword_id BIGINT NOT NULL,
    locale VARCHAR(20) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_system_keyword_locale UNIQUE (system_keyword_id, locale),
    CONSTRAINT fk_ll_system_keyword_locale_keyword
        FOREIGN KEY (system_keyword_id)
        REFERENCES language_learning_system_keyword(id)
);

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'family', 'family', 'VOCABULARY', 'DAILY_FAMILY', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'DAILY_FAMILY'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '가족', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'DAILY_FAMILY'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '家族', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'DAILY_FAMILY'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'friend', 'friend', 'VOCABULARY', 'DAILY_FRIEND', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'DAILY_FRIEND'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '친구', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'DAILY_FRIEND'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '友達', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'DAILY_FRIEND'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'weather', 'weather', 'VOCABULARY', 'DAILY_WEATHER', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'DAILY_WEATHER'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '날씨', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'DAILY_WEATHER'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '天気', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'DAILY_WEATHER'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'housework', 'housework', 'VOCABULARY', 'DAILY_HOUSEWORK', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'DAILY_HOUSEWORK'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '집안일', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'DAILY_HOUSEWORK'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '家事', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'DAILY_HOUSEWORK'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'airport', 'airport', 'VOCABULARY', 'TRAVEL_AIRPORT', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'TRAVEL_AIRPORT'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '공항', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_AIRPORT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '空港', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_AIRPORT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'hotel', 'hotel', 'VOCABULARY', 'TRAVEL_HOTEL', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'TRAVEL_HOTEL'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '호텔', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_HOTEL'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'ホテル', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_HOTEL'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'directions', 'directions', 'VOCABULARY', 'TRAVEL_DIRECTIONS', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'TRAVEL_DIRECTIONS'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '길찾기', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_DIRECTIONS'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '道案内', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_DIRECTIONS'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'transportation', 'transportation', 'VOCABULARY', 'TRAVEL_TRANSPORT', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'TRAVEL_TRANSPORT'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '교통', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_TRANSPORT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '交通', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_TRANSPORT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'sightseeing', 'sightseeing', 'VOCABULARY', 'TRAVEL_SIGHTSEEING', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'TRAVEL_SIGHTSEEING'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '관광', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_SIGHTSEEING'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '観光', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'TRAVEL_SIGHTSEEING'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'restaurant', 'restaurant', 'VOCABULARY', 'FOOD_RESTAURANT', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'FOOD_RESTAURANT'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '레스토랑', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'FOOD_RESTAURANT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'レストラン', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'FOOD_RESTAURANT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'cafe', 'cafe', 'VOCABULARY', 'FOOD_CAFE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'FOOD_CAFE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '카페', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'FOOD_CAFE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'カフェ', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'FOOD_CAFE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'cooking', 'cooking', 'VOCABULARY', 'FOOD_COOKING', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'FOOD_COOKING'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '요리', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'FOOD_COOKING'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '料理', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'FOOD_COOKING'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'food order', 'food order', 'VOCABULARY', 'FOOD_FOOD_ORDER', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'FOOD_FOOD_ORDER'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '음식 주문', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'FOOD_FOOD_ORDER'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '料理の注文', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'FOOD_FOOD_ORDER'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'price', 'price', 'VOCABULARY', 'SHOPPING_PRICE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'SHOPPING_PRICE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '가격', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'SHOPPING_PRICE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '価格', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'SHOPPING_PRICE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'payment', 'payment', 'VOCABULARY', 'SHOPPING_PAYMENT', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'SHOPPING_PAYMENT'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '결제', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'SHOPPING_PAYMENT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '支払い', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'SHOPPING_PAYMENT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'exchange', 'exchange', 'VOCABULARY', 'SHOPPING_EXCHANGE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'SHOPPING_EXCHANGE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '교환', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'SHOPPING_EXCHANGE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '交換', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'SHOPPING_EXCHANGE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'delivery', 'delivery', 'VOCABULARY', 'SHOPPING_DELIVERY', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'SHOPPING_DELIVERY'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '배송', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'SHOPPING_DELIVERY'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '配送', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'SHOPPING_DELIVERY'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'meeting', 'meeting', 'VOCABULARY', 'BUSINESS_MEETING', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'BUSINESS_MEETING'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '회의', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'BUSINESS_MEETING'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '会議', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'BUSINESS_MEETING'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'schedule', 'schedule', 'VOCABULARY', 'BUSINESS_SCHEDULE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'BUSINESS_SCHEDULE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '일정', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'BUSINESS_SCHEDULE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '日程', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'BUSINESS_SCHEDULE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'customer service', 'customer service', 'VOCABULARY', 'BUSINESS_CUSTOMER_SERVICE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'BUSINESS_CUSTOMER_SERVICE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '고객 응대', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'BUSINESS_CUSTOMER_SERVICE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '顧客対応', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'BUSINESS_CUSTOMER_SERVICE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'presentation', 'presentation', 'VOCABULARY', 'BUSINESS_PRESENTATION', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'BUSINESS_PRESENTATION'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '발표', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'BUSINESS_PRESENTATION'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'プレゼンテーション', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'BUSINESS_PRESENTATION'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'development', 'development', 'VOCABULARY', 'IT_DEVELOPMENT', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'IT_DEVELOPMENT'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '개발', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_DEVELOPMENT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '開発', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_DEVELOPMENT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'API', 'api', 'VOCABULARY', 'IT_API', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'IT_API'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', 'API', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_API'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'API', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_API'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'database', 'database', 'VOCABULARY', 'IT_DATABASE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'IT_DATABASE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '데이터베이스', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_DATABASE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'データベース', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_DATABASE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'deployment', 'deployment', 'VOCABULARY', 'IT_DEPLOYMENT', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'IT_DEPLOYMENT'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '배포', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_DEPLOYMENT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'デプロイ', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_DEPLOYMENT'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'incident response', 'incident response', 'VOCABULARY', 'IT_INCIDENT_RESPONSE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'IT_INCIDENT_RESPONSE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '장애 대응', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_INCIDENT_RESPONSE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '障害対応', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'IT_INCIDENT_RESPONSE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'movie', 'movie', 'VOCABULARY', 'HOBBY_MOVIE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'HOBBY_MOVIE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '영화', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'HOBBY_MOVIE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '映画', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'HOBBY_MOVIE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'music', 'music', 'VOCABULARY', 'HOBBY_MUSIC', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'HOBBY_MUSIC'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '음악', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'HOBBY_MUSIC'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '音楽', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'HOBBY_MUSIC'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'reading', 'reading', 'VOCABULARY', 'HOBBY_READING', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'HOBBY_READING'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '독서', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'HOBBY_READING'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '読書', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'HOBBY_READING'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'exercise', 'exercise', 'VOCABULARY', 'HOBBY_EXERCISE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'HOBBY_EXERCISE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '운동', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'HOBBY_EXERCISE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '運動', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'HOBBY_EXERCISE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'cooperation', 'cooperation', 'VOCABULARY', 'GAME_COOPERATION', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'GAME_COOPERATION'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '협동', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'GAME_COOPERATION'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '協力', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'GAME_COOPERATION'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'strategy', 'strategy', 'VOCABULARY', 'GAME_STRATEGY', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'GAME_STRATEGY'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '전략', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'GAME_STRATEGY'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '戦略', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'GAME_STRATEGY'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'character', 'character', 'VOCABULARY', 'GAME_CHARACTER', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'GAME_CHARACTER'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '캐릭터', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'GAME_CHARACTER'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'キャラクター', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'GAME_CHARACTER'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'online game', 'online game', 'VOCABULARY', 'GAME_ONLINE_GAME', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'GAME_ONLINE_GAME'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '온라인 게임', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'GAME_ONLINE_GAME'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'オンラインゲーム', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'GAME_ONLINE_GAME'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'festival', 'festival', 'VOCABULARY', 'CULTURE_FESTIVAL', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'CULTURE_FESTIVAL'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '축제', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'CULTURE_FESTIVAL'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '祭り', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'CULTURE_FESTIVAL'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'etiquette', 'etiquette', 'VOCABULARY', 'CULTURE_ETIQUETTE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'CULTURE_ETIQUETTE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '예절', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'CULTURE_ETIQUETTE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', 'マナー', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'CULTURE_ETIQUETTE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'local culture', 'local culture', 'VOCABULARY', 'CULTURE_LOCAL_CULTURE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'CULTURE_LOCAL_CULTURE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '지역 문화', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'CULTURE_LOCAL_CULTURE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '地域文化', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'CULTURE_LOCAL_CULTURE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );

INSERT INTO language_learning_system_keyword
(text, normalized_text, keyword_type, canonical_key, active, created_at, updated_at)
SELECT 'language difference', 'language difference', 'VOCABULARY', 'CULTURE_LANGUAGE_DIFFERENCE', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_system_keyword
    WHERE canonical_key = 'CULTURE_LANGUAGE_DIFFERENCE'
);

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ko-KR', '언어 차이', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'CULTURE_LANGUAGE_DIFFERENCE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ko-KR'
  );

INSERT INTO language_learning_system_keyword_locale
(system_keyword_id, locale, display_name, created_at, updated_at)
SELECT id, 'ja-JP', '言語の違い', NOW(), NOW()
FROM language_learning_system_keyword
WHERE canonical_key = 'CULTURE_LANGUAGE_DIFFERENCE'
  AND NOT EXISTS (
      SELECT 1 FROM language_learning_system_keyword_locale locale_row
      WHERE locale_row.system_keyword_id = language_learning_system_keyword.id
        AND locale_row.locale = 'ja-JP'
  );
