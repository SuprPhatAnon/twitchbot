# AI Agent Guide for Twitch Song Overlay Bot

This document provides essential information for AI agents working on this codebase.

## Project Overview

This is a Spring Boot application that integrates with Twitch to play songs on a web overlay when specific Channel Point rewards are redeemed.

### Architecture

- **Backend**: Spring Boot (Java 21+)
- **Database**: MariaDB
- **Messaging**: WebSocket (STOMP) for real-time updates to the overlay.
- **Frontend**: Simple HTML/JS/CSS served as static resources.
- **External Integrations**: Twitch API (Helix) and Twitch EventSub (via `twitch4j`). Uses App Access Tokens for API interactions and webhook subscriptions.

**IMPORTANT**: The use of Twitch IRC is strictly forbidden and has been completely removed from the codebase. All chat-related functionality must use the Twitch Helix API (for sending messages) and Twitch EventSub (for receiving messages). Furthermore, Twitch EventSub MUST use the **Webhook** transport method; the use of WebSockets for EventSub is strictly prohibited to ensure better scalability and avoid long-lived connection management issues.

## Key Components

### Core Logic
- `dev.phatanon.service.TwitchBotService`: Main service handling Twitch events (EventSub), song queue management, and redemption processing.
- `dev.phatanon.service.chat.ChatMessageService`: Extensible service for processing incoming Twitch chat messages using various `ChatMessageHandler` implementations.
- `dev.phatanon.controller.WebSocketController`: Handles incoming WebSocket messages from the overlay (e.g., song completion).
- `dev.phatanon.ConnectionStartupLogger`: Helper for logging startup status.

### Controllers
- `SongController`: Manages song database, statistics, and manual playback.
- `RedeemController`: Manages Twitch redemption titles that trigger the bot.
- `TwitchConfigController`: Manages Twitch credentials and connection status.
- `SongUploadController`: Handles song file uploads and management.
- `UserController`: Manages user accounts and API keys.

### Entities
- `Song`: Represents a song in the database.
- `SongPlay`: Records an instance of a song being played (for statistics).
- `TwitchConfig`: Stores Twitch API credentials and channel information.
- `Redeem`: Represents a Twitch redemption title.
- `User`: Represents a system user with roles and an API key.

## Technical Details for Agents

### Logging
- The application uses **Log4j2** for logging.
- Configuration is located in `src/main/resources/log4j2-spring.xml`.
- Log levels can be configured in the XML file or overridden via environment variables if configured.
- Logs are output to the console and to `logs/twitchbot.log` with a rolling policy (10MB per file, 7 files max).

### WebSocket Flow
1. The overlay (`overlay.html`) connects to `/ws` using SockJS and STOMP.
2. It subscribes to `/topic/play` to receive song play events.
3. When a song finishes, the overlay sends a message to `/app/song-finished`.
4. It also subscribes to:
   - `/topic/queue-size`: Receives the current number of songs in the queue.
   - `/topic/current-song`: Receives the currently playing song details.
   - `/topic/songs`: Receives "refresh" messages when the song list changes.
   - `/topic/redeems-list`: Receives "refresh" messages when the redeems list changes.

### File Storage & Playlists
- **Song Uploads**: Managed by `SongUploadController`. Only MP3 files are supported for upload. Files are stored in the directory specified by `SONG_UPLOAD_PATH` (default: `/uploads/songs`).
- **Metadata Extraction Endpoint**: `POST /api/songs/upload/metadata` allows extracting metadata from an MP3 file without saving it, useful for pre-filling UI forms.
- **M3U Playlist**: A `playlist.m3u` file is automatically generated and updated in the `SONG_UPLOAD_PATH` directory whenever songs are added, updated, or deleted. This file contains all enabled songs.
- **Metadata Extraction**: `SongService` automatically extracts metadata (artist, title, and cover art) from uploaded MP3 files using the `mp3agic` library. Cover art is stored as a Base64-encoded Data URI in the `Song` entity.

### Authentication
- The application uses Spring Security with form-based login for the Web UI.
- **API Access**: All API write requests (POST, PUT, DELETE) and protected GET requests must use **API Key authentication** via the `X-API-KEY` header. Username and password (Basic Auth) are NOT supported for the API. API keys are stored securely using hashing (BCrypt) and can be managed/rotated in the User Management UI.
- **Data Transfer Objects (DTOs)**: The application uses DTOs (e.g., `UserDTO`, `TwitchConfigDTO`) to expose only necessary data and mask sensitive information like passwords or API keys in list views.
- **Read-only requests (GET)**: Some are public, while others require authentication (API Key or Session).
- **Roles**:
  - `ROLE_UPLOAD`: Allowed to upload songs and manage files.
  - `ROLE_STREAMER`: Allowed access to Streamer UI and song playback controls.
  - `ROLE_ADMIN`: Full access (Users, Config, Songs, Redeems, System).
- Default admin credentials: `admin` / `admin`.

### Database Schema
The database is managed by Hibernate/JPA. Key tables:
- `songs`: `id`, `name`, `artist`, `url`, `sort_name`, `enabled`, `play_count`, `cover_art`.
- `song_plays`: `id`, `song_id`, `timestamp`, `source`. Tracks each play instance for statistics.
- `twitch_config`: `id`, `client_id`, `client_secret`, `access_token`, `refresh_token`, `bot_access_token`, `bot_refresh_token`, `channel_name`, `redeem_title`, `song_delay_seconds`.
- `redeems`: `id`, `title`.
- `song_redeem_link`: (Join table for `songs` and `redeems`).
- `users`: `id`, `username`, `password`, `api_key` (hashed), `deleted`.
- `user_roles`: (Join table for `users` and `Role` enum).

## Documentation for Agents

Agents should familiarize themselves with the project structure and subsystems by reading the comprehensive documentation in the `docs/` folder:

- [**Twitch Integration**](docs/twitch-integration.md): Details on EventSub, scopes, and the strictly forbidden IRC usage.
- [**REST API**](docs/api.md): Endpoint list, authentication (API Keys), and OpenAPI documentation.
- [**User Interfaces & Overlay**](docs/ui.md): Roles, dashboard functionality, and OBS overlay behavior directives.
- [**Deployment Guide**](docs/deployment.md): Docker, Kubernetes, and environment variable configuration.
- [**Development & Contribution**](docs/development.md): Setup, testing procedures (including Selenium state isolation), and scripts.

### General Requirements for Agents
- **Always keep documentation updated**: If you change functionality, update `README.md`, `AGENTS.md`, and the relevant files in `docs/`.
- **Critical Header Requirement**: All HTTP responses MUST include the header `X-Clacks-Overhead: "GNU Terry Pratchett"`.
- **Maintain Kubernetes Configurations**: Keep all files in `k8s/` updated.
- **Maintain OpenAPI documentation**: Ensure annotations in controllers are accurate.
- **Maintain Security Directives**: Update CSP and other headers in `SecurityConfig.java` when needed.
- **State Isolation in Tests**: When adding JPA entities, you MUST update `BaseSeleniumTest.resetDatabase()` to include the new repository and ensure proper deletion order.

## Environment Setup for Agents

- Use the `CODE` mode for most tasks.
- When investigating issues, check `src/main/resources/application.yml` for default configurations.
- **Python Scripts**: Some automation scripts (e.g., for uploading songs) are available in the `scripts/` directory.
  - **Python Version**: Python 3.10+ is recommended.
  - **Dependencies**: Install required libraries using `pip install requests`.
- K8s configurations are available in the `k8s/` directory. You MUST keep these (including `base` and `overlays` for production, minikube, and k3s) updated with any infrastructure changes.
- For detailed deployment instructions on specific Kubernetes distributions, refer to:
  - `k8s/K3S.md`: Guide for deploying to a k3s cluster.
  - `k8s/MINIKUBE.md`: Guide for local deployment using Minikube.
