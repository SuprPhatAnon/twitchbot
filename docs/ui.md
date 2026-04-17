# User Interfaces and Overlay

The application provides several web-based interfaces for different user roles and the stream overlay.

## Stream Overlay

The main player overlay is located at the root or `/overlay.html`.

- **Usage**: Add as a **Browser Source** in OBS with the URL `http://localhost:8080`.
- **Behavior**:
  - Plays a song when a redemption is detected.
  - Displays "Now Playing" information (title, artist, cover art).
  - Automatically clears the display when the song finishes.
  - Clears immediately if the queue is cleared.

## Dashboards

### Admin UI (`/admin.html`)
Full management dashboard for:
- Twitch Configuration (Client ID, Secret, Tokens).
- Song management.
- Redemption management.
- User management.

### Streamer UI (`/streamer.html`)
Operational dashboard for:
- Monitoring status.
- Viewing logs.
- Triggering manual song playback.

### Song Management (`/song-management.html`)
Dedicated interface for managing song files and cleaning up orphan records (Admin only).

## Other Interfaces

- **Upload UI (`/upload.html`)**: For uploading MP3 files with automatic metadata extraction.
- **Public Player (`/player.html`)**: Allows users to manually play songs in their browser.
- **Statistics (`/statistics.html`)**: Dashboard for viewing song play trends.
- **Login (`/login.html`)**: Standard form-based login.

## Authentication Roles

- `ROLE_UPLOAD`: Can upload songs.
- `ROLE_STREAMER`: Access to Streamer UI and manual playback.
- `ROLE_ADMIN`: Full access to all features.
