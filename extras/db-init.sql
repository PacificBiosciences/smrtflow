-- This should be run to initialize the Test database
CREATE USER smrtlink_user WITH PASSWORD 'password';

CREATE DATABASE smrtlink WITH OWNER smrtlink_user;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO smrtlink_user;

