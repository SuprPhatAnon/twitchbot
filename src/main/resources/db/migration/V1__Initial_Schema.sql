-- Initial schema for Twitch Song Overlay Bot

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) UNIQUE
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(255) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE twitch_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(255),
    client_secret VARCHAR(255),
    access_token VARCHAR(255),
    refresh_token VARCHAR(255),
    bot_access_token VARCHAR(255),
    bot_refresh_token VARCHAR(255),
    channel_name VARCHAR(255),
    redeem_title VARCHAR(255),
    song_delay_seconds INT NOT NULL DEFAULT 0
);

CREATE TABLE redeems (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL UNIQUE,
    created_by VARCHAR(255),
    created_timestamp DATETIME,
    last_updated_by VARCHAR(255),
    last_updated_timestamp DATETIME
);

CREATE TABLE songs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    sort_name VARCHAR(255),
    artist VARCHAR(255),
    url VARCHAR(255),
    cover_art LONGTEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    play_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    created_timestamp DATETIME,
    last_updated_by VARCHAR(255),
    last_updated_timestamp DATETIME
);

CREATE TABLE song_redeem_link (
    song_id BIGINT NOT NULL,
    redeem_id BIGINT NOT NULL,
    PRIMARY KEY (song_id, redeem_id),
    FOREIGN KEY (song_id) REFERENCES songs(id),
    FOREIGN KEY (redeem_id) REFERENCES redeems(id)
);

CREATE TABLE song_plays (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    song_id BIGINT NOT NULL,
    timestamp DATETIME NOT NULL,
    source VARCHAR(255),
    FOREIGN KEY (song_id) REFERENCES songs(id)
);
