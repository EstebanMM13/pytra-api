-- Preset avatar key chosen by the user (see AvatarPolicy). Nullable: no avatar shows the username initial.
ALTER TABLE users ADD COLUMN avatar VARCHAR(32);
