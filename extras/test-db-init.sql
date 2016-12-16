-- This should be run to initialize the Test database
CREATE USER smrtlink_test_user WITH PASSWORD 'password';

CREATE DATABASE smrtlink_test WITH OWNER smrtlink_user;

-- Is this correct?
GRANT SELECT ON ALL TABLES IN SCHEMA public TO smrtlink_test_user;
