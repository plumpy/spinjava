DROP DATABASE IF EXISTS orca;
SET tx_isolation = 'READ-COMMITTED';

CREATE DATABASE orca;
CREATE USER orca_migrate;
CREATE USER orca_service;

GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW VIEW ON `orca`.* TO 'orca_service'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER, LOCK TABLES, EXECUTE, SHOW VIEW ON `orca`.* TO 'orca_migrate'@'%';
