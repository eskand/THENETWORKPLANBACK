-- ============================================================
--  networkplan — database bootstrap
--  Run in pgAdmin 4: right-click "postgres" database -> Query Tool
--  (CREATE DATABASE cannot run inside a transaction block, so run
--   the statements one at a time if pgAdmin wraps them.)
-- ============================================================

-- 1) The database
CREATE DATABASE networkplan
    WITH OWNER = postgres
         ENCODING = 'UTF8'
         LC_COLLATE = 'en_US.UTF-8'
         LC_CTYPE = 'en_US.UTF-8'
         TEMPLATE = template0
         CONNECTION LIMIT = -1;

COMMENT ON DATABASE networkplan IS 'The Network Plan - application database';

-- ------------------------------------------------------------
-- 2) OPTIONAL: a dedicated application user instead of `postgres`.
--    If you use this, set DB_USERNAME=networkplan_app and
--    DB_PASSWORD=<the password below> for the Spring Boot app.
-- ------------------------------------------------------------
-- CREATE ROLE networkplan_app WITH LOGIN PASSWORD 'change_me_please';
-- GRANT ALL PRIVILEGES ON DATABASE networkplan TO networkplan_app;

-- ------------------------------------------------------------
-- 3) Now RECONNECT to the `networkplan` database and run the rest.
--    (In pgAdmin: expand Databases -> networkplan -> Query Tool)
-- ------------------------------------------------------------

-- Useful extensions for a JPA app
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";   -- uuid_generate_v4()
-- CREATE EXTENSION IF NOT EXISTS "pgcrypto";    -- gen_random_uuid(), digest()

-- Grant the app user rights on the public schema (PostgreSQL 15+ locks this down)
-- GRANT USAGE, CREATE ON SCHEMA public TO networkplan_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public
--     GRANT ALL ON TABLES TO networkplan_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public
--     GRANT ALL ON SEQUENCES TO networkplan_app;

-- Sanity check
SELECT current_database(), current_user, version();
