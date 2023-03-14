CREATE TABLE `tickets` (
    `ticket_id` INT(128) AUTO_INCREMENT PRIMARY KEY,
    `ticket_owner` BIGINT(128),
    `creation_date` DATETIME,
    `closed` INT(1)
);

