CREATE TABLE `tickets` (
    `ticket_id` INT(128) AUTO_INCREMENT PRIMARY KEY,
    `channel_id` BIGINT(128) DEFAULT NULL,
    `message_id` BIGINT(128) DEFAULT NULL,
    `ticket_owner` BIGINT(128) NOT NULL,
    `ticket_type` VARCHAR(128) NOT NULL,
    `creation_date` DATETIME NOT NULL,
    `locked` INT(1) NOT NULL
);

CREATE TABLE `stickies` (
    `channel_id` BIGINT(128) PRIMARY KEY,
    `message` TEXT(1024) NOT NULL
);

CREATE TABLE `points` (
    `discord_id` BIGINT(128) PRIMARY KEY,
    `lastKnownName` VARCHAR(128) NOT NULL,
    `lastKnownAvatar` VARCHAR(255) NOT NULL,
    `points` INT(128) NOT NULL
);

CREATE TABLE `punishments` (
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
    `punished_by_lastKnownAvatar` VARCHAR(255) NOT NULL
);

CREATE TABLE `ranking` (
    `discord_id` BIGINT(128) PRIMARY KEY,
    `lastKnownName` VARCHAR(128) NOT NULL,
    `lastKnownAvatar` VARCHAR(255) NOT NULL,
    `exp` INT(128) NOT NULL
);

CREATE TABLE `blacklists` (
    `blacklist_id` INT(128) AUTO_INCREMENT PRIMARY KEY,
    `discord_id` BIGINT(128) UNIQUE KEY
);

CREATE TABLE `roles` (
    `blacklist_id` INT(128),
    `role_id` BIGINT(128)
);