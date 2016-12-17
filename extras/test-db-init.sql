-- This should be run to initialize the Test database

CREATE ROLE smrtlink_test_user WITH PASSWORD 'password';
-- Note, "CREATE USER" is an alias for "CREATE ROLE"
-- CREATE USER smrtlink_test_user WITH PASSWORD 'password';

ALTER ROLE smrtlink_test_user WITH LOGIN;

CREATE DATABASE smrtlink_test WITH OWNER smrtlink_test_user;

GRANT SELECT, UPDATE, INSERT, DELETE ON ALL TABLES IN SCHEMA smrtlink_test.public TO smrtlink_test_user;
