-- Remove redundant play_count column from songs table as song_plays is now the single source of truth for statistics
ALTER TABLE songs DROP COLUMN play_count;
