# REST API Documentation

The application provides a comprehensive REST API for managing songs, users, redemptions, and configuration.

## Authentication

All write requests (POST, PUT, DELETE) and protected GET requests require authentication.

### API Key Authentication

Include your API key in the `X-API-KEY` header:
```bash
curl -H "X-API-KEY: your_api_key" http://localhost:8080/api/songs
```
API keys can be managed and rotated in the User Management UI.

## Interactive Documentation

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI Spec**: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

## API Endpoints

### Song Management (`/api/songs`)
- **GET `/api/songs`**: List all songs.
- **GET `/api/songs/{id}`**: Get a specific song.
- **POST `/api/songs`**: Add a new song.
- **POST `/api/songs/upload`**: Upload an MP3 file.
- **POST `/api/songs/upload/metadata`**: Extract metadata from an MP3 without saving.
- **PUT `/api/songs/{id}`**: Update a song.
- **DELETE `/api/songs/{id}`**: Remove a song from the DB.
- **POST `/api/songs/{id}/play`**: Queue a song for playback.
- **POST `/api/songs/clear`**: Clear the queue and stop playback.
- **GET `/api/songs/queue`**: Get the current queue.
- **GET `/api/songs/statistics`**: Get song play statistics.

### User Management (`/api/users`) - Admin Only
- **GET `/api/users`**: List all users.
- **POST `/api/users`**: Create a new user.
- **PUT `/api/users/{id}`**: Update a user.
- **DELETE `/api/users/{id}`**: Delete a user.

### Redeem Management (`/api/redeems`)
- **GET `/api/redeems`**: List Twitch channel point redeems.
- **POST `/api/redeems`**: Add a new redeem title.
- **DELETE `/api/redeems/{id}`**: Remove a redeem.

### Twitch Configuration (`/api/twitch-config`)
- **GET `/api/twitch-config`**: Get Twitch credentials and settings.
- **PUT `/api/twitch-config`**: Update Twitch configuration.
- **GET `/api/twitch-config/status`**: Consolidated connection status.
