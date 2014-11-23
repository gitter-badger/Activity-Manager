-- Suppression et cr�ation de la base
drop database if exists taskmgr_db;
create database taskmgr_db;

-- Suppression et cr�ation de l'utilisateur
delete from mysql.user where user='taskmgr_user';
grant all privileges on taskmgr_db.* to taskmgr_user@'%'
identified by 'taskmgr_password';
flush privileges;
