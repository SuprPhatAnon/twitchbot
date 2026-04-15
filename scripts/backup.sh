#!/bin/bash

# Simple backup script for Twitch Song Overlay Bot
# Backs up the MariaDB database and the song upload directory.

# Configuration
BACKUP_DIR="./backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DB_HOST=${DB_HOST:-localhost}
DB_USER=${DB_USER:-mariadb}
DB_PASSWORD=${DB_PASSWORD:-mariadb}
DB_NAME=${DB_NAME:-mariadb}
UPLOAD_PATH=${SONG_UPLOAD_PATH:-/uploads/songs}

mkdir -p "$BACKUP_DIR/$TIMESTAMP"

echo "Starting backup at $TIMESTAMP..."

# 1. Backup Database
echo "Backing up database..."
if command -v mysqldump &> /dev/null; then
    mysqldump -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" > "$BACKUP_DIR/$TIMESTAMP/db_backup.sql"
    echo "Database backup completed."
else
    echo "Warning: mysqldump not found. Database backup skipped."
fi

# 2. Backup Uploads
echo "Backing up upload directory..."
if [ -d "$UPLOAD_PATH" ]; then
    tar -czf "$BACKUP_DIR/$TIMESTAMP/uploads_backup.tar.gz" -C "$UPLOAD_PATH" .
    echo "Uploads backup completed."
else
    echo "Warning: Upload path $UPLOAD_PATH does not exist. Uploads backup skipped."
fi

# 3. Cleanup old backups (keep last 7 days)
echo "Cleaning up old backups..."
find "$BACKUP_DIR" -type d -mtime +7 -exec rm -rf {} +

echo "Backup process finished. Files located in $BACKUP_DIR/$TIMESTAMP"
