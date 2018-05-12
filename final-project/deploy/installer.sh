#!/bin/bash

# usage: ./installer.sh <username> <password>

# enter mysql username
if [ -z ${1} ]; then
    echo "missing mysql username";
    exit 1;
fi

# enter mysql password
if [ -z ${2} ]; then
    echo "missing mysql password";
    exit 1;
fi

# check for the mysql config file
if [ ! -f /home/ubuntu/frontend/deploy/mysqld.cnf ]; then
    echo "mysqld.cnf missing";
    exit 1;
fi

# update and upgrade
sudo apt-get -y update && sudo apt-get -y upgrade

# install the java jdk
sudo apt-get -y install default-jdk

# export java home to dir containing bin
export JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk-amd64/"

# install maven
sudo apt install -y maven

# install mysql-server
export DEBIAN_FRONTEND=noninteractive
sudo -E apt-get -q -y install mysql-server

# add custom config
#sudo mv /home/ubuntu/frontend/deploy/mysqld.cnf /etc/mysql/mysql.conf.d/mysqld.cnf

echo "CREATE USER '${1}'@'localhost' IDENTIFIED BY '${2}';" | sudo mysql
echo "CREATE USER '${1}'@'%' IDENTIFIED BY '${2}';" | sudo mysql
echo "GRANT ALL PRIVILEGES ON *.* TO '${1}'@'localhost';" | sudo mysql
echo "GRANT ALL PRIVILEGES ON *.* TO '${1}'@'%';" | sudo mysql

# restart the service
sudo service mysql restart

# run the loading script
chmod +x mysql_load.sh
sudo ./mysql_load.sh

# build the package
cd /home/ubuntu/frontend && mvn clean package

# launch the service
sudo java -Xms1024m -Xmx2048m -cp /home/ubuntu/frontend/target/frontend-1.0-SNAPSHOT-jar-with-dependencies.jar frontend/controller/Controller -username ${1} -password ${2} -ip localhost
