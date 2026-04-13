# Twitch Song Overlay Bot

This application listens for Twitch Channel Point redemptions and plays a random song from a database on a web overlay.

## Features

- **Twitch Integration**: Automatically detects when a specific reward is redeemed.
- **Web Overlay**: A simple HTML/JS overlay to be used in OBS or other streaming software.
- **Database Backed**: Songs are stored in a MariaDB database.
- **Caching**: Uses Redis for state management.

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
   This will start MariaDB, Redis, and the Twitch Bot application.

4. **Add songs to the database:**
   The application uses a `songs` table. You can add songs by connecting to the MariaDB container:
   ```bash
   docker exec -it twitchbot-db mariadb -u twitchuser -ptwitchpass twitchdb
   ```
   Then run an insert command:
   ```sql
   INSERT INTO songs (name, artist, url) VALUES ('Song Name', 'Artist Name', 'https://example.com/song.mp3');
   ```

## Usage

### OBS Overlay
In OBS, add a **Browser Source** with the following URL:
`http://localhost:8080`

When the reward is redeemed on Twitch, the overlay will pick a random song from the database and play it.

### Testing
You can manually trigger a song play (for testing the overlay) by visiting:
`http://localhost:8080/api/test/play`
(Note: This now requires the `X-API-Key` header).

Example using `curl`:
```bash
curl -H "X-API-Key: your_api_key" http://localhost:8080/api/test/play
```

### REST API
You can manage the song database using the following REST API endpoints. All requests to `/api/**` require an `X-API-Key` header with your set API key.

- **GET `/api/songs`**: List all songs.
- **GET `/api/songs/{id}`**: Get a specific song by ID.
- **POST `/api/songs`**: Add a new song.
  - Body: `{"name": "Song Name", "artist": "Artist Name", "url": "https://example.com/song.mp3"}`
- **PUT `/api/songs/{id}`**: Update an existing song.
  - Body: `{"name": "New Name", "artist": "New Artist", "url": "https://example.com/new.mp3"}`
- **DELETE `/api/songs/{id}`**: Remove a song from the database.

## Development

If you want to run the application locally without Docker for development:
- Ensure MariaDB and Redis are running on `localhost`.
- Update `src/main/resources/application.yml` or set environment variables.
- Run with Maven:
  ```bash
  mvn spring-boot:run
  ```
