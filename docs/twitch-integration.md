# Twitch Integration

This application integrates with Twitch using the Helix API and EventSub.

## Key Features

- **EventSub Subscriptions**: Automatically detects and synchronizes subscriptions on startup.
- **App Access Tokens**: Uses App Access Tokens for API interactions and webhook subscriptions.
- **Unified Status Dashboard**: Real-time status of all EventSub subscriptions is visible in the Admin and Streamer dashboards.
- **Event Logging**: All Twitch events (Redemptions, Cheers, Subscriptions, Chat) are logged with an `[EVENT]` prefix.

## Scopes and Permissions

The system requires several scopes for full functionality.

### Streamer User Access Token Scopes:
- `channel:read:redemptions`
- `channel:read:subscriptions`
- `moderator:read:followers`
- `bits:read`
- `chat:read`
- `chat:edit`
- `channel:bot`

### Bot Account Scopes (Optional):
- `user:bot`
- `user:read:chat`
- `user:write:chat`

## Configuration

Twitch credentials (Client ID, Secret, and Access Tokens) are managed via the Admin UI at `/admin.html`.

## Forbidden Practices

**IMPORTANT**:
- The use of Twitch IRC is strictly forbidden. All chat functionality must use the Twitch Helix API and EventSub. The `TwitchClientBuilder.withEnableChat(true)` method must NOT be used.
- The use of WebSockets for Twitch EventSub is strictly forbidden. All EventSub subscriptions MUST use the **Webhook** transport method.
