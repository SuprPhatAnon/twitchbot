# Requirements Document - Twitch Song Overlay Bot

## 1. Introduction
The Twitch Song Overlay Bot is a Spring Boot application designed to enhance Twitch streams by playing AI-generated songs (or any uploaded audio) triggered by Twitch events like Channel Point redemptions. It provides a web-based overlay for OBS, an administrative dashboard, and a streamer monitoring interface.

## 2. Functional Requirements

### 2.1 Twitch Integration
- **EventSub Support**: The system must listen for and respond to various Twitch events:
    - Channel Point Redemptions (triggering song playback).
    - Chat messages (commands like `!music`).
    - Cheers/Bits events.
    - Subscription events.
- **Webhook Handling**: Securely receive and verify Twitch EventSub notifications via HMAC SHA256 signatures.
- **OAuth 2.0 Flow**: Support Twitch authentication for both "Streamer" and "Bot" accounts to obtain access and refresh tokens.
- **Bot Chat**: Capability to send automated messages to Twitch chat.

### 2.2 Music Management
- **Song Repository**: Store and manage a library of songs (audio files and cover art).
- **Song Playback**: 
    - Trigger song playback based on specific redemption titles.
    - Support random song playback from the library.
- **Queue Management**:
    - Queue songs when multiple redemptions occur.
    - Provide ability to view, reorder (via API), and remove items from the queue.
    - Manual "Play Now" functionality for administrators.
- **Statistics**: Track play counts and timestamps for songs.

### 2.3 User Interfaces
- **Admin Dashboard**: 
    - Configure Twitch credentials (Client ID, Secret, Redirect URIs).
    - Manage users and API keys.
    - View system status and logs.
- **Streamer Interface**: 
    - Real-time monitor for the currently playing song and queue.
    - Controls for the bot (skip, clear queue, test redemptions).
- **OBS Overlay**: A dedicated web page (`player.html`) that plays audio and displays cover art/song info for stream integration.

### 2.4 API & Security
- **REST API**: Comprehensive API for all management functions (songs, users, config).
- **Authentication**: 
    - Role-based access control (ADMIN, USER).
    - API Key authentication for programmatic access and UI communication.
- **WebSockets**: Real-time updates for the overlay and streamer UI using STOMP protocol.

## 3. Non-Functional Requirements

### 3.1 Reliability
- The bot must handle token expiration by automatically refreshing Twitch OAuth tokens.
- The system should survive Twitch API outages and reconnect automatically.

### 3.2 Security
- Webhook endpoints must verify signatures to prevent spoofing.
- **Clacks Overhead**: All HTTP responses must include the header `X-Clacks-Overhead: "GNU Terry Pratchett"`.
- API keys must be stored securely (hashed).
- Sensitive Twitch credentials must be stored in a database and configurable via environment variables.

### 3.3 Scalability & Deployment
- **Containerization**: Must be deployable via Docker and Docker Compose.
- **Database**: Use MariaDB/MySQL for persistent storage.
- **Kubernetes**: Provide configuration for deployment in K8s clusters.

## 4. Technical Stack
- **Backend**: Java 21, Spring Boot 3.x.
- **Twitch Client**: Twitch4J library.
- **Database**: MariaDB with Flyway for migrations.
- **Frontend**: HTML5, CSS3, JavaScript (Vanilla/jQuery/Bootstrap).
- **Real-time**: Spring WebSocket with SockJS and STOMP.
- **Documentation**: OpenAPI (Swagger).
