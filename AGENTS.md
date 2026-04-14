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
- `dev.phatanon.service.TwitchBotService`: Main service handling Twitch events, song queue management, and redemption processing.
- `dev.phatanon.controller.WebSocketController`: Handles WebSocket communication with the overlay.

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

### Authentication
- Some API endpoints (`/api/**`) require an `X-API-Key` header.
- **Read-only requests (GET)** are public and do not require an API key.
- **Write requests (POST, PUT, DELETE)** require an `X-API-Key` header with your set API key.
- The default key is `default_secret_key` if not configured via environment variables.

### Database Schema
The database is managed by Hibernate/JPA. Key tables:
- `songs`: `id`, `name`, `artist`, `url`.
- `song_plays`: `id`, `song_id`, `played_at`.
- `twitch_configs`: `id`, `client_id`, `client_secret`, `access_token`, etc.
- `redeems`: `id`, `title`.

## Common Development Tasks

### Adding a New Endpoint
1. Create or update a controller in `src/main/java/dev/phatanon/controller/`.
2. Ensure it has the `@SecurityRequirement(name = "X-API-Key")` annotation if it needs protection.
3. Add the endpoint to `README.md`.

### Changing Twitch Integration Logic
- Modify `TwitchBotService`. It uses `twitch4j` to interact with Twitch.
- If adding new event listeners, look at how `registerEventListeners()` is implemented.

### Modifying the Overlay
- Edit `src/main/resources/static/index.html`.
- It uses basic CSS for styling and SockJS/STOMP for communication.

## Testing Procedures

### Automated Tests
- Run tests using Maven: `mvn test`
- Tests are located in `src/test/java/dev/phatanon/`.

## Environment Setup for Agents

- Use the `CODE` mode for most tasks.
- When investigating issues, check `src/main/resources/application.yml` for default configurations.
- K8s configurations are available in the `k8s/` directory for deployment-related tasks.
