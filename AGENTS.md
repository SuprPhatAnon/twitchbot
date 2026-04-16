# AI Agent Guide for Twitch Song Overlay Bot

This document provides essential information for AI agents working on this codebase.

## Project Overview

This is a Spring Boot application that integrates with Twitch to play songs on a web overlay when specific Channel Point rewards are redeemed.

### Architecture

- **Backend**: Spring Boot (Java 21+)
- **Database**: MariaDB
- **Messaging**: WebSocket (STOMP) for real-time updates to the overlay.
- **Frontend**: Simple HTML/JS/CSS served as static resources.
- **External Integrations**: Twitch API (Helix) and Twitch EventSub (via `twitch4j`).

**IMPORTANT**: The use of Twitch IRC is strictly forbidden and has been completely removed from the codebase. All chat-related functionality must use the Twitch Helix API (for sending messages) and Twitch EventSub (for receiving messages).

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
- **Song Uploads**: Managed by `SongUploadController`. Files are stored in the directory specified by `SONG_UPLOAD_PATH` (default: `/uploads/songs`).
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

## Common Development Tasks

### General Requirements
- **Always keep documentation updated**: If you change functionality, update `README.md`, `AGENTS.md`, and any relevant K8s/Docker documentation (including `k8s/K3S.md` and `k8s/MINIKUBE.md`).
- **Keep AGENTS.md current**: When adding new core services, entities, or significant architectural changes, ensure they are documented here for future AI agents.
- **Maintain Kubernetes Configurations**: Keep all Kubernetes deployment files in the `k8s/` directory (including `base` and `overlays` for production, minikube, and k3s) up-to-date with any changes in the application's infrastructure or configuration requirements.
- **Maintain OpenAPI documentation**: Ensure SpringDoc/OpenAPI annotations in controllers are accurate and up-to-date.
- **Update Javadocs**: Provide or update Javadocs for new or modified public classes and methods.
- **Maintain Security Directives**: Ensure Content Security Policy (CSP), HSTS, and other security-related headers are up-to-date in `SecurityConfig.java` when adding new external dependencies (CDNs, fonts, etc.).

### Adding a New Endpoint
1. Create or update a controller in `src/main/java/dev/phatanon/controller/`.
2. Use Spring Security annotations like `@PreAuthorize` to protect endpoints.
3. Add the endpoint to `README.md`.

### Changing Twitch Integration Logic
- Modify `TwitchBotService`. It uses `twitch4j` to interact with Twitch.
- If adding new event listeners, look at how `registerEventListeners()` is implemented.
- **Chat Command Handling**: To add or modify chat commands (e.g., `!music`), implement or modify `ChatMessageHandler` in `dev.phatanon.service.chat`. These are automatically picked up by `ChatMessageService`.
- **IMPORTANT: The use of Twitch IRC is strictly forbidden.** All chat-related functionality must use the Twitch Helix API (for sending messages) and Twitch EventSub (for receiving messages and other events). IRC-related code and configurations have been permanently removed. Specifically, the use of `.withEnableChat(true)` in `TwitchClientBuilder` is strictly forbidden as it enables the legacy IRC interface.

### Modifying the Overlay
- The main player overlay is `src/main/resources/static/overlay.html`.
- **Overlay Behavior Directives**:
  - The overlay MUST play a song when it receives a message on `/topic/play`.
  - It MUST display "Now Playing" information (title, artist, and cover art if available) while the song is playing.
  - It MUST stop the song and REMOVE the "Now Playing" message immediately when the song finishes.
  - It MUST stop the song and REMOVE the "Now Playing" message immediately when a "clear queue" instruction is received (via a `null` or empty message on `/topic/current-song`).
- Other UIs:
  - `admin.html`: Full management dashboard.
  - `song-management.html`: Dedicated song file management (Admin only).
  - `streamer.html`: Read-only/operational dashboard.
  - `upload.html`: Song upload interface.
  - `user-management.html`: User management interface (Admin only).
  - `login.html`: Login page.
  - `player.html`: Public player for manual playback.
  - `statistics.html`: Song play statistics dashboard.
- UIs use basic CSS, SockJS/STOMP, and use session-based authentication.

## Testing Procedures

### Automated Tests
- Run tests using Maven: `mvn test`
- Tests are located in `src/test/java/dev/phatanon/`.
- **Maintain a minimum test coverage of 70%** for all new and modified code.

### Headless Browser Testing
The project supports headless browser testing using Selenium and Chrome. This is particularly useful for testing JavaScript functionality on the web pages.
- **Local Execution**: Ensure you have Chrome installed and use `WebDriverManager` (included in `pom.xml`) to manage drivers.
- **Docker Environment**: A dedicated Docker environment is provided for running these tests in a consistent, headless environment.
  - Run tests with: `docker-compose -f docker-compose.test.yml up --build --exit-code-from test-runner`
  - This setup uses a MariaDB container for the database and a Maven container with Chrome installed for running the tests.

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
