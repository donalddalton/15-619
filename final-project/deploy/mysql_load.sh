#!/usr/bin/env bash

sudo mysql -e "drop database if exists twitter; create database twitter character set UTF8mb4 collate utf8mb4_bin;"
sudo rm -rf /home/ubuntu/frontend/data
mkdir /home/ubuntu/frontend/data


################# q4 #################
mkdir /home/ubuntu/frontend/data/q4
cd /home/ubuntu/frontend/data/q4

# table data 4
wget https://storage.googleapis.com/sm1-q1-final/q3opfinal/mergedq4.txt -P /home/ubuntu/frontend/data/q4;

sudo mysql -e "
            USE twitter;
            drop table if exists q4;
            create table q4 (
              tweetid BIGINT not null,
              timestamp LONGTEXT not null,
              uuid BIGINT not null,
              username LONGTEXT not null,
              text LONGTEXT default null,
              favorite_count BIGINT not null,
              retweet_count BIGINT not null,
              PRIMARY key (tweetid)
            );"

sudo mysql -e "set unique_checks=0; set foreign_key_checks=0; set sql_log_bin=0; USE twitter; LOAD DATA LOCAL INFILE 'mergedq4.txt' INTO TABLE q4 fields terminated by '\t' escaped by '' lines terminated by '\n'; set unique_checks=1; set foreign_key_checks=1; set sql_log_bin=1;"
echo "finished loading file mergedq4.txt"
sudo rm "mergedq4.txt"

# add index after data is loaded
sudo mysql -e "
            USE twitter;
            ALTER TABLE q4 ADD INDEX(uuid);"

echo "loaded data into table q4"
sudo rm -rf /home/ubuntu/frontend/data/q4/*


################# q3 #################
mkdir /home/ubuntu/frontend/data/q3
cd /home/ubuntu/frontend/data/q3

wget https://storage.googleapis.com/sm1-q1-final/q3opfinal/merged.txt -P /home/ubuntu/frontend/data/q3;

sudo mysql -e "
            USE twitter;
            drop table if exists q3;
            create table q3 (
              uuid BIGINT not null,
              attributes LONGTEXT default NULL,
              PRIMARY key (uuid)
            );"

sudo mysql -e "set unique_checks=0; set foreign_key_checks=0; set sql_log_bin=0; USE twitter; LOAD DATA LOCAL INFILE 'merged.txt' INTO TABLE q3 fields terminated by '\t' escaped by '' lines terminated by '\n'; set unique_checks=1; set foreign_key_checks=1; set sql_log_bin=1;"
echo "finished loading file merged.txt"
sudo rm -rf /home/ubuntu/frontend/data/q3/*


################# q2 #################

mkdir /home/ubuntu/frontend/data/q2
cd /home/ubuntu/frontend/data/q2

wget https://storage.googleapis.com/sm1-q1-final/q1op/part-00000 -P /home/ubuntu/frontend/data/q2;
wget https://storage.googleapis.com/sm1-q1-final/q1op/part-00001 -P /home/ubuntu/frontend/data/q2;
wget https://storage.googleapis.com/sm1-q1-final/q1op/part-00002 -P /home/ubuntu/frontend/data/q2;
wget https://storage.googleapis.com/sm1-q1-final/q1op/part-00003 -P /home/ubuntu/frontend/data/q2;
wget https://storage.googleapis.com/sm1-q1-final/q1op/part-00004 -P /home/ubuntu/frontend/data/q2;
wget https://storage.googleapis.com/sm1-q1-final/q1op/part-00005 -P /home/ubuntu/frontend/data/q2;
wget https://storage.googleapis.com/sm1-q1-final/q1op/part-00006 -P /home/ubuntu/frontend/data/q2;
wget https://storage.googleapis.com/sm1-q1-final/q1op/part-00007 -P /home/ubuntu/frontend/data/q2;


sudo mysql -e "
            USE twitter;
            drop table if exists q2;
            create table q2 (
                keyword VARCHAR(256) not null,
                counts LONGTEXT default null,
                hashtags LONGTEXT default null,
                users LONGTEXT default null,
                PRIMARY key (keyword)
            );"


for f in *
do
        sudo mysql -e "set unique_checks=0; set foreign_key_checks=0; set sql_log_bin=0; USE twitter; LOAD DATA LOCAL INFILE '"$f"'INTO TABLE q2 fields terminated by '\t' escaped by '' lines terminated by '\n'; set unique_checks=1; set foreign_key_checks=1; set sql_log_bin=1;"
        echo "finished loading file" ${f}
        sudo rm ${f}
done

echo "loaded data into table q2"

sudo rm -rf /home/ubuntu/frontend/data/q2/*




