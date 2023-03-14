CREATE TABLE `tickets` (
    `ticket_id` INT(128) AUTO_INCREMENT PRIMARY KEY,
    `channel_id` BIGINT(128) DEFAULT NULL,
    `message_id` BIGINT(128) DEFAULT NULL,
    `ticket_owner` BIGINT(128) NOT NULL,
    `creation_date` DATETIME NOT NULL,
    `locked` INT(1) NOT NULL
);

