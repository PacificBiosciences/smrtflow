-- This should be run to initialize the Test database

CREATE ROLE smrtlink_test_user WITH PASSWORD 'password';

-- Is this the same as create ROLE ?
CREATE USER smrtlink_test_user WITH PASSWORD 'password';

CREATE DATABASE smrtlink_test WITH OWNER smrtlink_test_user;

GRANT SELECT, UPDATE, INSERT, DELETE ON ALL TABLES IN SCHEMA smrtlink_test.public TO smrtlink_test_user;
