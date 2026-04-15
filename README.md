# Twitch Song Overlay Bot

This application listens for Twitch Channel Point redemptions and plays a random song from a database on a web overlay.

## Features

- **Twitch Integration**: Automatically detects when a specific reward is redeemed via Twitch EventSub.
- **Web Overlay**: A real-time HTML/JS/CSS overlay for OBS or other streaming software using WebSockets.
- **Admin & Streamer UIs**: Built-in dashboards to manage songs, rewards, and monitor status.
- **Database Driven**: All songs, rewards, and statistics are stored in a MariaDB database.
- **API First**: Fully documented REST API with OpenAPI/Swagger support.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)
- A Twitch Developer account and a registered application to get your Client ID and Secret.
- A Twitch Access Token (Streamer User Access Token with `channel:read:redemptions`, `channel:read:subscriptions`, `moderator:read:followers`, `bits:read`, `chat:read`, `chat:edit`, and `channel:bot` scopes).
- (Optional) A separate Twitch Access Token for a bot account with `user:bot`, `user:read:chat`, and `user:write:chat` scopes.

## Setup and Configuration

 The application is configured using environment variables for the system and the database. Twitch credentials and settings are managed directly through the database via the Admin UI.

### Required Environment Variables

| Variable | Description |
| --- | --- |
| `API_KEY` | Secret key for REST API Authorization (Default: `default_secret_key`) |
| `DB_HOST` | MariaDB host (Default: `localhost`) |
| `DB_USER` | MariaDB username (Default: `mariadb`) |
| `DB_PASSWORD` | MariaDB password (Default: `mariadb`) |

### Optional Environment Variables

| Variable | Description |
| --- | --- |
| `TWITCH_REDIRECT_URI_HOST` | Host part of the OAuth redirect URI (e.g., `https://mybot.com`). Default: `https://stream.phat.wtf` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profiles (e.g. `prod`, `test`) |

## Running with Docker Compose

1. **Clone the repository.**
2. **Create a `.env` file** in the project root with your database and API credentials:
   ```env
   DB_HOST=mariadb
   DB_USER=mariadb
   DB_PASSWORD=mariadb
   API_KEY=your_api_key
   ```
3. **Start the application:**
   ```bash
   docker-compose up -d
   ```
   This will start MariaDB and the Twitch Bot application.

## Running on Kubernetes

For general instructions on how to deploy the application to a Kubernetes cluster, see the [Kubernetes Deployment Guide](k8s/README.md).

## Running on Minikube

For instructions on how to deploy the application to a local Minikube cluster using specific overlays, see the [Minikube Deployment Guide](k8s/MINIKUBE.md).

## Running on k3s

For instructions on how to deploy the application to a k3s cluster using specific overlays and configure an Apache2 proxy to access multiple clusters, see the [k3s Deployment Guide](k8s/K3S.md).

## Configuration and Usage

1. **Configure Twitch credentials:**
   Access the Admin UI at `http://localhost:8080/admin.html` and use the "Twitch Configuration" section to provide your Client ID, Secret, and Access Tokens.

2. **Add songs to the database:**
   The application uses a `songs` table. You can add songs by connecting to the MariaDB container:
   ```bash
   docker exec -it twitchbot-db mariadb -u mariadb -pmariadb mariadb
   ```
   Then run an insert command:
   ```sql
   INSERT INTO songs (name, artist, url) VALUES ('Song Name', 'Artist Name', 'https://example.com/song.mp3');
   ```

## Admin UI

The application includes a built-in Admin UI for full management and a Streamer UI for read-only access with operational capabilities.

- **Admin UI**: `http://localhost:8080/admin.html`
  - Full access to manage songs, redeems, and Twitch configuration.
- **Streamer UI**: `http://localhost:8080/streamer.html`
  - Read-only access to view configuration, songs, and logs.
  - Allows triggering song playback ("Play" button).
- **Public Player**: `http://localhost:8080/player.html`
  - Simple UI for regular people to play songs directly in their browser.

You will need to provide your `API_KEY` in the Admin and Streamer UIs to log in and perform actions. The Public Player and Statistics pages do not require an API key.

## OpenAPI Documentation

Interactive API documentation is available via Swagger UI:
`http://localhost:8080/swagger-ui.html`

The raw OpenAPI spec can be found at:
`http://localhost:8080/api-docs`

## Usage

### OBS Overlay
In OBS, add a **Browser Source** with the following URL:
`http://localhost:8080`

When the reward is redeemed on Twitch, the overlay will pick a random song from the database and play it.

### Testing
You can manually trigger a song play (for testing the overlay) by visiting:
`http://localhost:8080/api/songs/{id}/play`

Example using `curl`:
```bash
curl -X POST http://localhost:8080/api/songs/1/play -H "X-API-Key: your_api_key"
```
*(Note: As this is a POST request, it requires the `X-API-Key` header.)*

### REST API
You can manage the application using the following REST API endpoints. All write requests (**POST, PUT, DELETE**) to `/api/**` require an `X-API-Key` header with your set API key. Read-only requests (**GET**) are public.
Interactive documentation is available at `/swagger-ui.html`.

#### Song Management (`/api/songs`)
- **GET `/api/songs`**: List all songs.
- **GET `/api/songs/{id}`**: Get a specific song by ID.
- **POST `/api/songs`**: Add a new song.
  - Body: `{"name": "Song Name", "artist": "Artist Name", "url": "https://example.com/song.mp3", "redeems": [{"id": 1}]}`
- **PUT `/api/songs/{id}`**: Update an existing song.
- **DELETE `/api/songs/{id}`**: Remove a song from the database.
- **POST `/api/songs/{id}/play`**: Manually queue a song for playback.
  - Query parameter: `incrementStats` (boolean, default: `false`).
- **POST `/api/songs/clear`**: Clear the song queue and stop playback.
- **GET `/api/songs/queue-size`**: Get the current number of songs in the queue.
- **GET `/api/songs/current`**: Get the currently playing song.
- **GET `/api/songs/plays/recent`**: Get the most recent song plays.
  - Query parameter: `limit` (int, default: `5`).
- **GET `/api/songs/statistics`**: Get song play statistics.
  - Query parameters: `range` (e.g., `daily`, `weekly`, `monthly`, `yearly`, `alltime`), `groupBy` (e.g., `song`, `artist`).

#### Redeem Management (`/api/redeems`)
- **GET `/api/redeems`**: List all defined Twitch channel point redeems.
- **POST `/api/redeems`**: Add a new redeem title.
- **DELETE `/api/redeems/{id}`**: Remove a redeem.

#### Twitch Configuration (`/api/twitch-config`)
- **GET `/api/twitch-config`**: Get current Twitch credentials and settings.
- **PUT `/api/twitch-config`**: Update Twitch configuration.
- **GET `/api/twitch-config/status`**: Check if the stream is currently online.
- **GET `/api/twitch-config/connection`**: Check Twitch EventSub connection status.
- **GET `/api/twitch-config/redeems`**: Get a log of recent channel point redemption events.
- **GET `/api/twitch-config/profiles`**: Get active Spring profiles.
- **GET `/api/twitch-config/redirect-uri-host`**: Get the configured redirect URI host.

#### Test Endpoints (`/api/test`)
- **GET `/api/test/play`**: Trigger a random song to play (for testing the overlay).
- **GET `/api/test/finish`**: Simulate a song finished event.

## Development

If you want to run the application locally without Docker for development:
- Ensure MariaDB is running on `localhost`.
- Update `src/main/resources/application.yml` or set environment variables.
- Run with Maven:
  ```bash
  mvn spring-boot:run
  ```
