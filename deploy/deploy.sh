#!/usr/bin/env bash
# Pulls the latest code, rebuilds, and restarts the service. Run this ON THE EC2 INSTANCE (Amazon Linux 2023)
# from /opt/gat2027/src, logged in as a user that can sudo (e.g. ec2-user) - not as the "gat" service user.
# First-time setup: git clone https://github.com/drkusman/gat07.git /opt/gat2027/src
set -euo pipefail

cd /opt/gat2027/src
git pull
JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto ./mvnw -q clean package -DskipTests
sudo cp target/grassroot-0.0.1.jar /opt/gat2027/app.jar
sudo chown gat:gat /opt/gat2027/app.jar
sudo systemctl restart gat2027
sudo systemctl status gat2027 --no-pager -l | head -15
