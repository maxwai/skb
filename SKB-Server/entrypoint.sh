#!/bin/sh

PUID=${PUID:-1000}
PGID=${PGID:-1000}

groupmod -o -g "$PGID" abc
usermod -o -u "$PUID" abc

chown -R abc:abc .

echo "running as user $PUID:$PGID"

runuser -u abc -- java -Dquarkus.http.host=0.0.0.0 -jar ./quarkus-run.jar
