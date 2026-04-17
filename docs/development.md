# Development and Contribution

## Local Setup

1. Ensure MariaDB is running locally.
2. Configure `src/main/resources/application.yml` or set environment variables.
3. Run with Maven:
   ```bash
   mvn spring-boot:run
   ```

## Testing

### Automated Tests
Run unit and integration tests:
```bash
mvn test
```

### Headless Browser Testing
Run tests that require a browser (Selenium) using Docker:
```bash
docker-compose -f docker-compose.test.yml up --build --exit-code-from test-runner
```
Refer to `AGENTS.md` for more details on Selenium test requirements and database resetting.

## Automation Scripts

Located in the `scripts/` directory:

- **`upload_songs.py`**: Bulk upload MP3 files from a directory.
  - Requires `requests` library.
  - Usage: `python3 scripts/upload_songs.py /path/to/mp3s --api-key YOUR_KEY`
- **`backup.sh`**: Backs up the database and uploaded songs.

## Logging

- Uses **Log4j2**.
- Configuration: `src/main/resources/log4j2-spring.xml`.
- Logs are output to console and `logs/twitchbot.log`.

## Coding Standards

- Follow the existing code style.
- Maintain a minimum test coverage of 70%.
- Keep documentation (including `AGENTS.md` and OpenAPI) up-to-date.
