-- Add deleted column to users table for soft delete functionality
ALTER TABLE users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
