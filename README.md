# Twitch Song Overlay Bot

This application listens for Twitch Channel Point redemptions and plays a random song from a database on a web overlay.

## Features

- **Twitch Integration**: Automatically detects when a specific reward is redeemed via Twitch EventSub.
- **Web Overlay**: A real-time HTML/JS/CSS overlay for OBS or other streaming software using WebSockets.
- **Admin & Streamer UIs**: Built-in dashboards to manage songs, rewards, and monitor status.
- **Database Driven**: All songs, rewards, and statistics are stored in a MariaDB database.
- **API First**: Fully documented REST API with OpenAPI/Swagger support.
- **M3U Playlist**: Automatically generates a `playlist.m3u` file containing all enabled songs in the upload directory.

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
| `SONG_UPLOAD_PATH` | Path where uploaded songs are stored (Default: `/uploads/songs`) |
| `DB_HOST` | MariaDB host (Default: `localhost`) |
| `DB_USER` | MariaDB username (Default: `mariadb`) |
| `DB_PASSWORD` | MariaDB password (Default: `mariadb`) |

### Optional Environment Variables

| Variable | Description |
| --- | --- |
| `TWITCH_REDIRECT_URI_HOST` | Host part of the OAuth redirect URI (e.g., `https://mybot.com`). Default: `https://music.phat.wtf` |
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
- **File Management UI**: `http://localhost:8080/song-management.html`
  - Dedicated interface for managing uploaded song files, including deleting orphan files (Admin only).
- **Streamer UI**: `http://localhost:8080/streamer.html`
  - Read-only access to view configuration, songs, and logs.
  - Allows triggering song playback ("Play" button).
- **Upload UI**: `http://localhost:8080/upload.html`
  - Interface to upload new song files (MP3) directly to the server.
- **Public Player**: `http://localhost:8080/player.html`
  - Simple UI for regular people to play songs directly in their browser.

## Authentication

The application uses a user-based authentication system with three groups:
- **upload**: Allowed to upload songs.
- **streamer**: Allowed access to Streamer UI and upload.
- **admin**: Allowed everything, including user management and Twitch configuration.

API keys are stored securely using hashing. When creating a user or rotating an API key, the raw key is shown only once. Make sure to copy it and store it in a secure location.

**Default Credentials:**
- **Username**: `admin`
- **Password**: `admin`

Access the Admin UI to manage users and their roles.

### API Key Authentication

In addition to username/password authentication, the application supports **API Key authentication**. This is useful for scripts and automated tools.

1.  Log in to the Admin or Streamer UI.
2.  Go to the **Account** page.
3.  You can view (if just created/rotated) or rotate your **API Key**.
4.  To use the API key, include it in the `X-API-KEY` header of your requests:
    ```bash
    curl -H "X-API-KEY: your_api_key" http://localhost:8080/api/songs
    ```

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

Example using `curl` (requires session cookie or basic auth if configured, but here we assume session):
```bash
curl -X POST http://localhost:8080/api/songs/1/play
```

### REST API
You can manage the application using the following REST API endpoints. Write requests (**POST, PUT, DELETE**) and protected **GET** requests require authentication. The API supports **API Key** (`X-API-KEY` header) authentication.
Interactive documentation is available at `/swagger-ui.html`.

#### Song Management (`/api/songs`)
- **GET `/api/songs`**: List all songs.
- **GET `/api/songs/{id}`**: Get a specific song by ID.
- **POST `/api/songs`**: Add a new song.
  - Body: `{"name": "Song Name", "artist": "Artist Name", "url": "https://example.com/song.mp3", "redeems": [{"id": 1}]}`
- **POST `/api/songs/upload`**: Upload a new song file.
  - Multipart form-data: `file` (MP3 file), `name` (string), `artist` (string).
- **PUT `/api/songs/{id}`**: Update an existing song.
- **DELETE `/api/songs/{id}`**: Remove a song from the database.
- **POST `/api/songs/{id}/play`**: Manually queue a song for playback.
  - Query parameter: `incrementStats` (boolean, default: `false`).
- **POST `/api/songs/clear`**: Clear the song queue and stop playback.
- **GET `/api/songs/queue-size`**: Get the current number of songs in the queue.
- **GET `/api/songs/current`**: Get the currently playing song.
- **GET `/api/songs/queue`**: Get the current song queue.
- **DELETE `/api/songs/queue/{index}`**: Remove a song from the queue.
- **GET `/api/songs/ghost-records`**: List DB records with missing files.
- **GET `/api/songs/files`**: List all files in the upload directory.
- **DELETE `/api/songs/files/{filename}`**: Delete a file from the upload directory.
- **DELETE `/api/songs/{id}/permanent`**: Delete a song record AND its associated file.
- **GET `/api/songs/plays/recent`**: Get the most recent song plays.
  - Query parameter: `limit` (int, default: `5`).
- **GET `/api/songs/statistics`**: Get song play statistics.
  - Query parameters: `range` (e.g., `daily`, `weekly`, `monthly`, `yearly`, `alltime`), `groupBy` (e.g., `song`, `artist`).

#### User Management (`/api/users`) - Admin Only
- **GET `/api/users`**: List all users.
- **POST `/api/users`**: Create a new user.
- **PUT `/api/users/{id}`**: Update a user.
- **DELETE `/api/users/{id}`**: Delete a user.

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

### Bulk Upload / Single Song Upload Script

A Python script `scripts/upload_songs.py` is provided to upload MP3 files from a directory or a single MP3 file. It automatically generates song titles from filenames by replacing separators (`_`, `-`) with spaces and applying proper capitalization.

**Prerequisites:**
- Python 3.x
- `requests` library: `pip install requests`

**Usage:**

**Upload a directory of songs:**
```bash
python3 scripts/upload_songs.py /path/to/mp3/directory --artist "Default Artist" --url http://localhost:8080/api/songs/upload --api-key your_api_key
```

**Upload a single song:**
```bash
python3 scripts/upload_songs.py /path/to/song.mp3 --artist "Artist Name" --url http://localhost:8080/api/songs/upload --api-key your_api_key
```

**Arguments:**
- `path`: Path to an MP3 file or a directory containing MP3 files.
- `--artist`: Default artist for the songs (default: `Unknown Artist`).
- `--url`: The API upload endpoint URL (default: `http://localhost:8080/api/songs/upload`).
- `--api-key`: API Key for authentication (Required).

### Backup Script

A script `scripts/backup.sh` is provided to back up the database and the song upload directory.

**Usage:**
```bash
./scripts/backup.sh
```
The script will create a timestamped backup in the `backups/` directory and keep the last 7 days of backups.

## Development

If you want to run the application locally without Docker for development:
- Ensure MariaDB is running on `localhost`.
- Update `src/main/resources/application.yml` or set environment variables.
- Run with Maven:
  ```bash
  mvn spring-boot:run
  ```
