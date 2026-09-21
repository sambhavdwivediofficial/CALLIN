-- Adds Google Sign-In and a two-step profile-completion flow.
--
-- A user created via Google Sign-In starts with profile_completed =
-- false and no username — the app sends them to a "complete your
-- profile" screen to set one (plus first/last name) before they can
-- do anything else. Classic username/password accounts are created
-- with profile_completed = true immediately, since they set a
-- username at registration.

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL,
    ALTER COLUMN username DROP NOT NULL,
    ALTER COLUMN display_name DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN google_id TEXT UNIQUE,
    ADD COLUMN first_name VARCHAR(64),
    ADD COLUMN last_name VARCHAR(64),
    ADD COLUMN profile_completed BOOLEAN NOT NULL DEFAULT false;

-- Backfill: any row that already existed before this migration was
-- created through classic register, so it already has a username
-- and display_name — mark it complete.
UPDATE users SET profile_completed = true WHERE username IS NOT NULL;
