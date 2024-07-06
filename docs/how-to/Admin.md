# Aufsetzten einer SKB Instanz

- Domain Einrichten wo der Server erreichbar sein soll und Zertifikat erstellen
- Client-Server Schlüsselpaar erstellen, der Private Key wird vom Client verwendet und der Public Key vom Server

  ```bash
  openssl genrsa -out private.pem 2048
  openssl rsa -in private.pem -outform PEM -pubout -out public.pem
  ```

- Truststore und Keystore erstellen
  - Zertifikat zu pkcs12 konvertieren

    ```bash
    openssl pkcs12 -export -in cert.crt -inkey cert.key -out server.p12 -name server
    ```

  - Keystore `server.keystore` (Das password muss `password` sein)

    ```bash
    keytool -importkeystore -deststorepass password -destkeypass password -destkeystore server.keystore -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass password -alias server
    ```

  - Truststore `server.truststore`

    ```bash
    cp server.keystore server.truststore
    ```

- Server Docker-Image pull

  ```bash
  docker pull gitlab.lrz.de:5005/vl-foederierteinformationssysteme/ss2024/repos/repos-grpb/skb-server:latest
  ```

  - URL kann abweichen, je nachdem wo das Image gehostet wird
  - nicht vergessen vorher `docker login <URL>` auszuführen
- Docker-Container konfiguration
  - Environment Variablen
    | Variable              | Beschreibung                                                                                     |
    | --------------------- | ------------------------------------------------------------------------------------------------ |
    | MOUNT_PATH            | Pfad zum Mountpoint (z.B. `/app/appdata/data`)                                                   |
    | CLIENT_PUBLIC_KEY     | Pfad zum Public Key                                                                              |
    | OWNER                 | Name des Server Besitzers                                                                        |
    | HOSTNAME              | Hostname unter dem der Server erreichbar ist                                                     |
    | BLOCK_SIZE            | Größe der Blöcke z.B. 4096 Byte                                                                  |
    | HEALTH_CHECK_PERCENT  | Wie viel % der Blöcke bei einem Check geprüft werden sollen<br>Format: Zahl in (0, 100]          |
    | HEALTH_CHECK_INTERVAL | Wie oft der Check durchgeführt werden<br>Format: Zahl gefolgt von m, h oder d (z.B. 1d oder 90m) |
    | QUARKUS_LOG_LEVEL     | Log Level kann mit QUARKUS_LOG_LEVEL gesteuert werden.                                           |
    | PUID                  | User ID mit dem der Container ausgeführt werden soll                                             |
    | PGID                  | Gruppen ID mit dem der Container ausgeführt werden soll                                          |
  - Volume Mounts
    - Es ist empfohlen zwei Mounts zu verwenden, einen für die Appdaten und einen für den Dateispeicher
    - Appdaten müssen bei MOUNT_PATH gemountet werden
    - (Optional) Daten können bei MOUNT_PATH/data gemountet werden
  - Ports
    - Der Server hört auf Port 8443
- Vor dem start
  - `public.pem`, `server.keystore` und `server.truststore` nach `<MOUNT_PATH>/cert/*` kopieren
- nginx konfigurieren
  - hierzu können die Konfigurationen [im unteren Abschnitt](#nginx-konfigurationen) verwendet werden
- Container starten
  - mit `docker CLI`

  ```docker
  docker run \
    -d \
    --name='SKB-Server' \
    -e 'PGID'='100' \
    -e 'PUID'='99' \
    -e 'CLIENT_PUBLIC_KEY'='/app/appdata/cert/public.pem' \
    -e 'HOSTNAME'='skb.example.com' \
    -e 'OWNER'='Max Mustermann' \
    -e 'BLOCK_SIZE'='4096' \
    -e 'HEALTH_CHECK_PERCENT'='10' \
    -e 'HEALTH_CHECK_INTERVAL'='1d' \
    -e 'MOUNT_PATH'='/app/appdata/data' \
    -e 'QUARKUS_LOG_LEVEL'='INFO' \
    -p '8443:8443/tcp' \
    -v '/mnt/user/appdata/skb':'/app/appdata':'rw' \
    -v '/mnt/user/appdata/skb/virtual_volume/':'/app/appdata/data':'rw' 'gitlab.lrz.de:5005/vl-foederierteinformationssysteme/ss2024/repos/repos-grpb/skb-server:dev'
  ```

  - mit `docker-compose`

  ```yml
  version: '3.2'

  services:
    skb-server:
      container_name: SKB-Server
      build: .
      restart: "unless-stopped"
      volumes:
        - /mnt/user/appdata/skb:/app/appdata
        - /mnt/user/appdata/skb/virtual_volume:/app/appdata/data
      ports:
        - "8443:8443"
      environment:
        - PUID=100
        - PGID=99
        - CLIENT_PUBLIC_KEY="/app/appdata/cert/public.pem"
        - HOSTNAME="skb.example.com"
        - OWNER="Max Mustermann"
        - BLOCK_SIZE=4096
        - HEALTH_CHECK_PERCENT="10"
        - HEALTH_CHECK_INTERVAL="1d"
        - MOUNT_PATH="/app/appdata/data"
        - QUARKUS_LOG_LEVEL="INFO"
  ```

# Nginx Konfigurationen

Falls eine andere [Nginx](https://nginx.org/) läuft wo Webserver laufen, dann eine neue Aufsetzten und die alte hinter der neuen Instanz laufen lassen: (Config auf das wichtige reduziert)

```nginx
stream {
    include /config/nginx/resolver.conf;
    map $ssl_preread_server_name $domain {
        skb.example.com SKB;
        default https_default_backend;
    }
    upstream SKB {
        server SKB:8443;
    }
    upstream https_default_backend {
        server swag:443;
    }

    server {
      ssl_preread on;
      listen 443;
      proxy_pass $domain;
    }
    server {
      listen 80;
      proxy_pass swag:80;
    }
    log_format proxy '$protocol $status $bytes_sent $bytes_received $session_time';
    access_log .../access.log proxy;
}
```

SKB ist alleine als Öffentlicher Service: (Config auf das wichtige reduziert)

```nginx
stream {
    include /config/nginx/resolver.conf;
    map $ssl_preread_server_name $domain {
        skb.example.com SKB;
    }
    upstream SKB {
        server SKB:8443;
    }

    server {
      ssl_preread on;
      listen 443;
      proxy_pass $domain;
    }
    log_format proxy '$protocol $status $bytes_sent $bytes_received $session_time';
    access_log .../access.log proxy;
}
```
