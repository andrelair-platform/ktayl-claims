-- Debezium CDC user (Slice C). The MySQL binlog connector needs a user with replication + read grants.
-- Password is injected at container init from the MYSQL env (see docker-compose); kept simple for the sim.
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY 'debezium';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';
FLUSH PRIVILEGES;
