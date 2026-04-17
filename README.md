# Twitch Song Overlay Bot

This application listens for Twitch Channel Point redemptions and plays songs from a database on a web overlay.

## Features

- **Twitch Integration**: Automated detection of rewards, chat, cheers, and subs via EventSub.
- **Web Overlay**: Real-time overlay for OBS using WebSockets.
- **Admin & Streamer UIs**: Dashboards for management and monitoring.
- **REST API**: Fully documented API for all system functions.
- **M3U Playlist**: Auto-generated playlist for external players.

## Quick Start

1. **Prerequisites**: Docker, Twitch Developer Account.
2. **Setup**: Create a `.env` file with `DB_PASSWORD` and `API_KEY`.
3. **Run**:
   ```bash
   docker-compose up -d
   ```
4. **Configure**: Visit `http://localhost:8080/admin.html` to set up Twitch credentials.

## Documentation

For detailed information on specific subsystems, please refer to the following guides:

- [**Twitch Integration**](docs/twitch-integration.md): Scopes, EventSub, and configuration.
- [**REST API**](docs/api.md): Endpoint details, authentication, and Swagger UI.
- [**User Interfaces & Overlay**](docs/ui.md): Admin dashboard, Streamer UI, and OBS overlay usage.
- [**Deployment Guide**](docs/deployment.md): Docker, environment variables, and Kubernetes.
- [**Development & Contribution**](docs/development.md): Local setup, testing, and scripts.

## Support

- **OpenAPI Documentation**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Kubernetes**: See `k8s/README.md` for cluster-specific instructions.

---
*For AI agents working on this codebase, please refer to [AGENTS.md](AGENTS.md).*
