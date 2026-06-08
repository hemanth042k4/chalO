-- ChalO Database Schema
-- MySQL 8.x
-- Run once to initialise the database.

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS chat_participants;
DROP TABLE IF EXISTS chats;
DROP TABLE IF EXISTS join_requests;
DROP TABLE IF EXISTS adventure_photos;
DROP TABLE IF EXISTS adventure_tags;
DROP TABLE IF EXISTS user_interests;
DROP TABLE IF EXISTS adventures;
DROP TABLE IF EXISTS tags;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ─────────────────────────────────────────────
--  users
-- ─────────────────────────────────────────────
CREATE TABLE users (
    id          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    email       VARCHAR(255)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    avatar_url  VARCHAR(500),
    bio         TEXT,
    phone       VARCHAR(20),
    age         INT,
    gender      ENUM('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'),
    city        VARCHAR(100),
    is_admin    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP
);

-- ─────────────────────────────────────────────
--  tags
-- ─────────────────────────────────────────────
CREATE TABLE tags (
    id    BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(50)  NOT NULL UNIQUE,
    slug  VARCHAR(50)  NOT NULL UNIQUE,
    icon  VARCHAR(100)
);

-- ─────────────────────────────────────────────
--  user_interests  (user ↔ tag, drives recommendations)
-- ─────────────────────────────────────────────
CREATE TABLE user_interests (
    user_id  BIGINT NOT NULL,
    tag_id   BIGINT NOT NULL,
    PRIMARY KEY (user_id, tag_id),
    CONSTRAINT fk_ui_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ui_tag  FOREIGN KEY (tag_id)  REFERENCES tags(id)  ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
--  adventures
-- ─────────────────────────────────────────────
CREATE TABLE adventures (
    id               BIGINT        AUTO_INCREMENT PRIMARY KEY,
    host_id          BIGINT        NOT NULL,
    title            VARCHAR(150)  NOT NULL,
    description      TEXT,
    adventure_date   DATE          NOT NULL,
    location_name    VARCHAR(255),
    location_lat     DECIMAL(9, 6),
    location_lng     DECIMAL(9, 6),
    max_participants INT           NOT NULL,
    cover_image_url  VARCHAR(500),
    status           ENUM('DRAFT', 'PUBLISHED', 'COMPLETED', 'CANCELLED')
                                   NOT NULL DEFAULT 'DRAFT',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_adventure_host FOREIGN KEY (host_id) REFERENCES users(id)
);

-- ─────────────────────────────────────────────
--  adventure_tags  (adventure ↔ tag)
-- ─────────────────────────────────────────────
CREATE TABLE adventure_tags (
    adventure_id  BIGINT NOT NULL,
    tag_id        BIGINT NOT NULL,
    PRIMARY KEY (adventure_id, tag_id),
    CONSTRAINT fk_at_adventure FOREIGN KEY (adventure_id)
        REFERENCES adventures(id) ON DELETE CASCADE,
    CONSTRAINT fk_at_tag FOREIGN KEY (tag_id)
        REFERENCES tags(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
--  adventure_photos
-- ─────────────────────────────────────────────
CREATE TABLE adventure_photos (
    id             BIGINT        AUTO_INCREMENT PRIMARY KEY,
    adventure_id   BIGINT        NOT NULL,
    url            VARCHAR(500)  NOT NULL,
    caption        VARCHAR(255),
    display_order  INT           NOT NULL DEFAULT 0,
    uploaded_by    BIGINT        NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_photo_adventure FOREIGN KEY (adventure_id)
        REFERENCES adventures(id) ON DELETE CASCADE,
    CONSTRAINT fk_photo_uploader FOREIGN KEY (uploaded_by)
        REFERENCES users(id)
);

-- ─────────────────────────────────────────────
--  join_requests
-- ─────────────────────────────────────────────
CREATE TABLE join_requests (
    id            BIGINT     AUTO_INCREMENT PRIMARY KEY,
    adventure_id  BIGINT     NOT NULL,
    requester_id  BIGINT     NOT NULL,
    message       TEXT,
    status        ENUM('PENDING', 'ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_adventure_requester UNIQUE (adventure_id, requester_id),
    CONSTRAINT fk_jr_adventure FOREIGN KEY (adventure_id)
        REFERENCES adventures(id) ON DELETE CASCADE,
    CONSTRAINT fk_jr_requester FOREIGN KEY (requester_id)
        REFERENCES users(id)
);

-- ─────────────────────────────────────────────
--  chats  (one per adventure, created on first join acceptance)
-- ─────────────────────────────────────────────
CREATE TABLE chats (
    id            BIGINT     AUTO_INCREMENT PRIMARY KEY,
    adventure_id  BIGINT     NOT NULL UNIQUE,
    created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_adventure FOREIGN KEY (adventure_id)
        REFERENCES adventures(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
--  chat_participants
-- ─────────────────────────────────────────────
CREATE TABLE chat_participants (
    chat_id    BIGINT     NOT NULL,
    user_id    BIGINT     NOT NULL,
    joined_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_id, user_id),
    CONSTRAINT fk_cp_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ─────────────────────────────────────────────
--  messages  (polled every 5 s via JS fetch)
-- ─────────────────────────────────────────────
CREATE TABLE messages (
    id         BIGINT     AUTO_INCREMENT PRIMARY KEY,
    chat_id    BIGINT     NOT NULL,
    sender_id  BIGINT     NOT NULL,
    content    TEXT       NOT NULL,
    created_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_chat   FOREIGN KEY (chat_id)   REFERENCES chats(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);

-- ─────────────────────────────────────────────
--  Seed data
-- ─────────────────────────────────────────────
INSERT INTO tags (name, slug, icon) VALUES
    ('Trekking',       'trekking',       '🏔'),
    ('Camping',        'camping',        '⛺'),
    ('Photography',    'photography',    '📸'),
    ('Beach',          'beach',          '🏖'),
    ('Road Trip',      'road-trip',      '🚗'),
    ('Sunrise',        'sunrise',        '🌅'),
    ('Waterfall',      'waterfall',      '💧'),
    ('Food Exploring', 'food-exploring', '🍜'),
    ('Scuba',          'scuba',          '🤿'),
    ('Cycling',        'cycling',        '🚴');
