# Twitch Song Overlay Bot

This application listens for Twitch Channel Point redemptions and plays a random song from a database on a web overlay.

## Features

- **Twitch Integration**: Automatically detects when a specific reward is redeemed.
- **Web Overlay**: A simple HTML/JS overlay to be used in OBS or other streaming software.
- **Database Backed**: Songs are stored in a MariaDB database.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)
- A Twitch Developer account and a registered application to get your Client ID and Secret.
- A Twitch Access Token (User Access Token with `channel:read:redemptions` scope).

## Setup and Configuration

The application is configured using environment variables. You can create a `.env` file in the root directory or set them in your shell.

### Required Environment Variables

| Variable | Description |
| --- | --- |
| `TWITCH_CLIENT_ID` | Your Twitch Application Client ID |
| `TWITCH_CLIENT_SECRET` | Your Twitch Application Client Secret |
| `TWITCH_ACCESS_TOKEN` | A User Access Token for your channel |
| `TWITCH_CHANNEL_NAME` | Your Twitch channel name |
| `TWITCH_REDEEM_TITLE` | The exact title of the Channel Point reward (Default: `Play Random Song`) |
| `API_KEY` | Secret key for REST API Authorization (Default: `default_secret_key`) |
| `TWITCH_LOCAL_CLI_URL` | URL of the local Twitch CLI mock server (Default: `http://localhost:8080`) |
| `SONG_DELAY_SECONDS` | Delay between songs in seconds (Default: `5`) |
| `DB_HOST` | MariaDB host (Default: `localhost`) |
| `DB_PORT` | MariaDB port (Default: `3306`) |
| `DB_NAME` | MariaDB database name (Default: `mariadb`) |
| `DB_USER` | MariaDB username (Default: `mariadb`) |
| `DB_PASSWORD` | MariaDB password (Default: `mariadb`) |

## Running with Docker Compose

1. **Clone the repository.**
2. **Create a `.env` file** in the project root with your Twitch credentials:
   ```env
   TWITCH_CLIENT_ID=your_client_id
   TWITCH_CLIENT_SECRET=your_client_secret
   TWITCH_ACCESS_TOKEN=your_access_token
   TWITCH_CHANNEL_NAME=your_channel_name
   TWITCH_REDEEM_TITLE=Play Random Song
   API_KEY=your_api_key
   ```
3. **Start the application:**
   ```bash
   docker-compose up -d
   ```
   This will start MariaDB and the Twitch Bot application.

4. **Add songs to the database:**
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

You will need to provide your `API_KEY` in the UI to log in and perform actions.

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
(Note: This requires the `X-API-Key` header).

Example using `curl`:
```bash
curl -X POST -H "X-API-Key: your_api_key" http://localhost:8080/api/songs/1/play
```

### REST API
You can manage the application using the following REST API endpoints. All requests to `/api/**` require an `X-API-Key` header with your set API key.
Interactive documentation is available at `/swagger-ui.html`.

#### Song Management (`/api/songs`)
- **GET `/api/songs`**: List all songs.
- **GET `/api/songs/{id}`**: Get a specific song by ID.
- **POST `/api/songs`**: Add a new song.
  - Body: `{"name": "Song Name", "artist": "Artist Name", "url": "https://example.com/song.mp3", "redeems": [{"id": 1}]}`
- **PUT `/api/songs/{id}`**: Update an existing song.
- **DELETE `/api/songs/{id}`**: Remove a song from the database.
- **POST `/api/songs/{id}/play`**: Manually queue a song for playback.
- **GET `/api/songs/queue-size`**: Get the current number of songs in the queue.
- **GET `/api/songs/current`**: Get the currently playing song.

#### Redeem Management (`/api/redeems`)
- **GET `/api/redeems`**: List all defined Twitch channel point redeems.
- **POST `/api/redeems`**: Add a new redeem title.
- **DELETE `/api/redeems/{id}`**: Remove a redeem.

#### Twitch Configuration (`/api/twitch-config`)
- **GET `/api/twitch-config`**: Get current Twitch credentials and settings.
- **PUT `/api/twitch-config`**: Update Twitch configuration.
- **GET `/api/twitch-config/status`**: Check if the stream is currently online.
- **GET `/api/twitch-config/connection`**: Check Twitch IRC connection status.
- **GET `/api/twitch-config/redeems`**: Get a log of recent channel point redemption events.

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
