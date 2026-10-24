-- Instagram-style uniqueness: "John" and "john" are the same username, and
-- A@b.com must not register twice as a@b.com. The exact-case UNIQUE
-- constraints from V1 (email) and V5 (username) stay; these indexes close
-- the case-variant hole. Verified duplicate-free on local + prod before adding.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username_lower ON users (LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (LOWER(email));
