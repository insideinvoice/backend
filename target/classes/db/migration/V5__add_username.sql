ALTER TABLE users ADD COLUMN username VARCHAR(100) UNIQUE;

UPDATE users SET username = email WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
