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

## Key Components

### Core Logic
- `dev.phatanon.service.TwitchBotService`: Main service handling Twitch events (EventSub, IRC), song queue management, and redemption processing.
- `dev.phatanon.controller.WebSocketController`: Handles incoming WebSocket messages from the overlay (e.g., song completion).
- `dev.phatanon.ConnectionStartupLogger`: Helper for logging startup status.

### Controllers
- `SongController`: Manages song database, statistics, and manual playback.
- `RedeemController`: Manages Twitch redemption titles that trigger the bot.
- `TwitchConfigController`: Manages Twitch credentials and connection status.

### Entities
- `Song`: Represents a song in the database.
- `SongPlay`: Records an instance of a song being played (for statistics).
- `TwitchConfig`: Stores Twitch API credentials and channel information.
- `Redeem`: Represents a Twitch redemption title.

## Technical Details for Agents

### WebSocket Flow
1. The overlay (`index.html`) connects to `/ws` using SockJS and STOMP.
2. It subscribes to `/topic/play` to receive song play events.
3. When a song finishes, the overlay sends a message to `/app/song-finished`.
4. It also subscribes to:
   - `/topic/queue-size`: Receives the current number of songs in the queue.
   - `/topic/current-song`: Receives the currently playing song details.
   - `/topic/songs`: Receives "refresh" messages when the song list changes.
   - `/topic/redeems-list`: Receives "refresh" messages when the redeems list changes.

### Authentication
- Some API endpoints (`/api/**`) require an `X-API-Key` header.
- **Read-only requests (GET)** are public and do not require an API key.
- **Write requests (POST, PUT, DELETE)** require an `X-API-Key` header with your set API key.
- The default key is `default_secret_key` if not configured via environment variables.

### Database Schema
The database is managed by Hibernate/JPA. Key tables:
- `songs`: `id`, `name`, `artist`, `url`, `sort_name`, `enabled`, `play_count`.
- `song_plays`: `id`, `song_id`, `timestamp`, `source`.
- `twitch_config`: `id`, `client_id`, `client_secret`, `access_token`, `refresh_token`, `bot_access_token`, `bot_refresh_token`, `channel_name`, `redeem_title`, `song_delay_seconds`.
- `redeems`: `id`, `title`.
- `song_redeem_link`: (Join table for `songs` and `redeems`).

## Common Development Tasks

### General Requirements
- **Always keep documentation updated**: If you change functionality, update `README.md`, `AGENTS.md`, and any relevant K8s/Docker documentation (including `k8s/K3S.md` and `k8s/MINIKUBE.md`).
- **Maintain Kubernetes Configurations**: Keep all Kubernetes deployment files in the `k8s/` directory (including `base` and `overlays` for production, minikube, and k3s) up-to-date with any changes in the application's infrastructure or configuration requirements.
- **Maintain OpenAPI documentation**: Ensure SpringDoc/OpenAPI annotations in controllers are accurate and up-to-date.
- **Update Javadocs**: Provide or update Javadocs for new or modified public classes and methods.

### Adding a New Endpoint
1. Create or update a controller in `src/main/java/dev/phatanon/controller/`.
2. Ensure it has the `@SecurityRequirement(name = "X-API-Key")` annotation if it needs protection.
3. Add the endpoint to `README.md`.

### Changing Twitch Integration Logic
- Modify `TwitchBotService`. It uses `twitch4j` to interact with Twitch.
- If adding new event listeners, look at how `registerEventListeners()` is implemented.

### Modifying the Overlay
- The main player overlay is `src/main/resources/static/index.html`.
- Other UIs:
  - `admin.html`: Full management dashboard.
  - `streamer.html`: Read-only/operational dashboard.
  - `player.html`: Public player for manual playback.
  - `statistics.html`: Song play statistics dashboard.
- UIs use basic CSS, SockJS/STOMP, and often include an API key for write operations.

## Testing Procedures

### Automated Tests
- Run tests using Maven: `mvn test`
- Tests are located in `src/test/java/dev/phatanon/`.

## Environment Setup for Agents

- Use the `CODE` mode for most tasks.
- When investigating issues, check `src/main/resources/application.yml` for default configurations.
- K8s configurations are available in the `k8s/` directory. You MUST keep these (including `base` and `overlays` for production, minikube, and k3s) updated with any infrastructure changes.
- For detailed deployment instructions on specific Kubernetes distributions, refer to:
  - `k8s/K3S.md`: Guide for deploying to a k3s cluster.
  - `k8s/MINIKUBE.md`: Guide for local deployment using Minikube.
