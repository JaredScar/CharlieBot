-- CharlieBot database setup for MariaDB.
--
-- Run on the dedicated server as an administrator:
--   sudo mariadb < install.sql
--
-- Then set these keys in config.yml to the account created below:
--   Database.DB:       charliebot
--   Database.Username: charliebot
--   Database.Password: the password you set in this file
--
-- Replace 'change-me' before running. The account is created with
-- mysql_native_password so the bot's MySQL driver can log in. MariaDB's
-- auth_gssapi_client plugin is what made `java -jar CharlieBot.jar` fail.

CREATE DATABASE IF NOT EXISTS `charliebot`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Dedicated bot account. Safe to re-run: an existing account is left as-is.
CREATE USER IF NOT EXISTS 'charliebot'@'localhost'
    IDENTIFIED VIA mysql_native_password USING PASSWORD('change-me');

GRANT ALL PRIVILEGES ON `charliebot`.* TO 'charliebot'@'localhost';
FLUSH PRIVILEGES;

-- To keep logging in as root instead, comment out the CREATE USER / GRANT above
-- and run this once, with the same password as Database.Password:
-- ALTER USER 'root'@'localhost' IDENTIFIED VIA mysql_native_password USING PASSWORD('change-me');
-- FLUSH PRIVILEGES;

USE `charliebot`;

CREATE TABLE IF NOT EXISTS `tickets` (
    `ticket_id` INT(128) AUTO_INCREMENT PRIMARY KEY,
    `channel_id` BIGINT(128) DEFAULT NULL,
    `message_id` BIGINT(128) DEFAULT NULL,
    `ticket_owner` BIGINT(128) NOT NULL,
    `ticket_type` VARCHAR(128) NOT NULL,
    `creation_date` DATETIME NOT NULL,
    `locked` INT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `stickies` (
    `channel_id` BIGINT(128) PRIMARY KEY,
    `message` TEXT(1024) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `points` (
    `discord_id` BIGINT(128) PRIMARY KEY,
    `lastKnownName` VARCHAR(128) NOT NULL,
    `lastKnownAvatar` VARCHAR(255) NOT NULL,
    `points` INT(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `punishments` (
    `pid` INT(128) AUTO_INCREMENT PRIMARY KEY,
    `discord_id` BIGINT(128),
    `datetime` DATETIME,
    `ruleIds_broken` VARCHAR(255),
    `lastKnownName` VARCHAR(128) NOT NULL,
    `lastKnownAvatar` VARCHAR(255) NOT NULL,
    `punishment_type` VARCHAR(128),
    `punishment_length` VARCHAR(128) DEFAULT '0',
    `reason` TEXT(1024),
    `punished_by` BIGINT(128),
    `punished_by_lastKnownName` VARCHAR(128) NOT NULL,
    `punished_by_lastKnownAvatar` VARCHAR(255) NOT NULL,
    `deleted` BIT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ranking` (
    `discord_id` BIGINT(128) PRIMARY KEY,
    `lastKnownName` VARCHAR(128) NOT NULL,
    `lastKnownAvatar` VARCHAR(255) NOT NULL,
    `exp` INT(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `blacklists` (
    `blacklist_id` INT(128) AUTO_INCREMENT PRIMARY KEY,
    `discord_id` BIGINT(128) UNIQUE KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `roles` (
    `blacklist_id` INT(128),
    `role_id` BIGINT(128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Active mutes. expires_at is a Unix timestamp in milliseconds, NULL = permanent.
-- (The bot also creates this table itself on startup if it is missing.)
CREATE TABLE IF NOT EXISTS `mutes` (
    `discord_id` BIGINT PRIMARY KEY,
    `expires_at` BIGINT DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `lockdown_roles` (
    `channel_id` BIGINT(128),
    `role_id` BIGINT(128),
    `permission` VARCHAR(128),
    `default` BIT(1) DEFAULT NULL,
    PRIMARY KEY (`channel_id`, `role_id`, `permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
