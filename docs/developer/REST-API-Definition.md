# Well-known

## GET `/.well-known/skb`

Pfad zu der api, hier im Dokument `/api`

Body: json

```json
{
    "api_path": "/api"
}
```

# Backup REST-API

Kommunikation nur über HTTPS um Identität zu sichern.

Unter dem Path: `/api/bak/v1`

Authentifizierung: JWT Token im Header: `Authorization: Bearer <token>`

JWT Token werden durch die Föderierte REST-API erstellt und managed.

Übersicht:

- POST `/block/<id>`
- PUT `/block/<id>`
- GET `/block/<id>`

## POST `/block/<id>`

Neuen Block hochladen

Header:

- `Content-Type: application/octet-stream`

Body: Block als Binär Daten

## PUT `/block/<id>`

Block Daten ersetzen

Header:

- `Content-Type: application/octet-stream`

Body: Block als Binär Daten

## GET `/block/<id>`

Block Daten erhalten

Header:

- `Content-Type: application/octet-stream`

Body: Block als Binär Daten

# Föderierte REST-API

Kommunikation nur über HTTPS um Identität zu sichern. Damit mTLS richtig funktioniert, muss bei jeder Request der Header `domain` mit dem hostnamen mitgegeben werden.

Unter dem Path: `/api/fed/v1`

Übersicht:

- GET `/server/info`
- PUT `/server/verify`
- POST `/server/verify`
- PUT `/server/restore`
- PUT `/server/migrate`
- POST `/server/maintenance`
- DELETE `/server`
- GET `/block`
- POST `/block`
- GET `/block/<id>`
- GET `/block/<id>/jwt`
- POST `/block/<id>`
- DELETE `/block/<id>`

## GET `/server/info`

Body: JSON mit Server Infos

```json
{
    "hostname": "<hostname>",
    "owner": "<owner>",
    "block_size": 123, // in Bytes
    "free_blocks": 123,
    "healthcheck_percent": 10, // in Percent
    "healthcheck_interval": 60, // in minutes
    "hash-methods": [
        "<method>",
        "<method2>"
    ],
    "is_verified" : bool,
    "known_server": [
        "<hostname>",
        ...
    ]
}
```

## PUT `/server/verify`

Server will bei anderem Server validiert sein.

### Response

- 202: Server ist validiert
- 209: Anfrage angenommen, Validation noch nicht getan

```json
{
    "backup_code": "<backup_code>"
}
```

## POST `/server/verify`

Server wurde validiert durch anderen Server

### Response

```json
{
    "backup_code": "<backup_code>"
}
```

## PUT `/server/restore`

Falls kein Zugang mehr zur alten Domain besteht und kein `/server/migrate` gemacht wurde, kann hiermit die Domain von Blöcken geändert werden.

```json
{
    "backup_code": "<backup_code>"
}
```

## PUT `/server/migrate`

Notiz an andere Server dass die Domain des Servers sich bald ändert. Der Server soll dann die neue Domain abspeichern und bei der ersten Verbindung mit der neuen Domain, die alte löschen.

Body:

```json
{
    "domain": "<domain>"
}
```

## POST `/server/maintenance`

Falls der Server geplanter weise eine gewisse Zeit nicht erreichbar ist (maximal 2 Tage). Während dieser Zeit werden keine Daten vom Server gelöscht.

Body:

```json
{
    "from": <unix-timestamp-in-sec>,
    "until": <unix-timestamp-in-sec>
}
```

## DELETE `/server`

Server und die gespeicherten Blöcke vom Server, werden hiermit gelöscht.

## GET `/block`

Falls `last-modified` auf 0 gesetzt ist, ist der Block nur reserviert aber keine Daten.

Body: JSON mit Block infos die der anfragender Server hat

```json
{
    "blocks": [
        {
            "id": "<uuid64>",
            "last_modified": <unix-timestamp-in-sec>
        },
        ...
    ]
}
```

## POST `/block`

Anzahl an Blöcken reservieren. Der Anfragende muss auf seiner Seite schon die Blöcke für den anderen Reserviert haben.

Body:

```json
{
    "amount": <amount>
}
```

## GET `/block/<id>`

Body: JSON mit Block infos

```json
{
    "id": "<uuid64>",
    "last_modified": <unix-timestamp-in-sec>
}
```

## GET `/block/<id>/jwt`

JWT Tokens sollen nur 5 min gültig sein

Body: JSON mit JWT Token um den Block zu holen bei der anderen API

```json
{
    "jwt": "<jwt-token>"
}
```

## POST `/block/<id>`

Body:

```json
{
    "hash_method": "<method>",
    "salt": "<base64-salt>"
}
```

### Response

Body:

```json
{
    "hash": "<hash>"
}
```

## DELETE `/block/<id>`

Block wird gelöscht

# Client REST-API

Authentifizierung wird mit RSA Keys gemacht. Dabei wird der Body der Nachricht mit PSS signiert. Die Signatur wird dann im `SIGNATURE` Header übergeben. Dabei immer ein Body präsent ist, wird immer (egal ob es schon ein Body gibt) ein Nonce hinzugefügt.

```json
{
    "nonce": "<128Bytes Base64 Kodiert>"
}
```

Unter dem Path: `/api/client/v1`

Übersicht:

- GET `/info`
- POST `/file`
- POST `/file/<id>`
- PUT `/file/<id>`
- GET `/file/<id>`
- DELETE `/file/<id>`
- GET `/server?depth=<depth>`
- POST `/server?hostname=<hostname>`
- PUT `/server?hostname=<hostname>`
- DELETE `/server?hostname=<hostname>`

`Last-Modified` format: [spec](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Last-Modified)

## GET `/info`

Im JSON Array `servers` werden nur Server aufgelistet die explizit verbunden wurde (über POST/PUT `/server`).

Body:

```json
{
    "total_usage_size": 123, // in Bytes
    "used_data": 123, // in Bytes
    "data_unsecured": 123, // in Bytes
    "data_secured": 123, // in Bytes
    "data_safely_secured": 123, // in Bytes
    "servers": [
        {
            "hostname": "<hostname>",
            "old_Hostnames": [
                "<hostname>",
                ...
            ],
            "owner": "<owner>",
            "block_size": 123, // in Bytes
            "free_blocks": 123,
            "used_blocks": 123,
            "healthcheck_percent": 10, // in Percent
            "healthcheck_interval": 60, // in minutes
            "hash-methods": [
              "<method>",
              "<method2>"
            ],
            "is_verified" : bool,
            "is_confirmed": bool,
            "healthy": bool
        },
        ...
    ],
    "files": [
        {
            "id": "<uuid64>",
            "path": "<path>",
            "last_modified": <unix-timestamp-in-sec>
        },
        ...
    ]
}
```

## POST `/file`

Neue Datei anlegen

Body:

```json
{
    "path": "<path>"
}
```

### Response

```json
{
    "id": "<uuid64>"
}
```

## POST `/file/<id>`

Neue Datei hochladen

Header:

- `Content-Type: application/octet-stream`
- `Last-Modified: <last-modified-date-of-file>`

Body: Datei als Binär Daten, es wird kein Nonce angehängt.

## PUT `/file/<id>`

Datei ersetzen

Header:

- `Content-Type: application/octet-stream`
- `Last-Modified: <last-modified-date-of-file>`

Body: Datei als Binär Daten, es wird kein Nonce angehängt.

## GET `/file/<id>`

Datei erhalten

Header:

- `Content-Type: application/octet-stream`
- `Last-Modified: <last-modified-date-of-file>`

Body: Datei als Binär Daten

## DELETE `/file/<id>`

Datei löschen.

## GET `/server?depth=<depth>`

Liste an bekannten Server kriegen.

Das Argument `depth` is optional. `depth` sagt aus wie tief nach Servern gesucht werden soll (Also bekannte server von bekannte Server von ...)

Bekannte Server (Server die mit GET `/info` gekriegt werden) kommen nicht in die Liste.

Body:

```json
{
    "servers": [
        {
            "hostname": "<hostname>",
            "owner": "<owner>",
            "block_size": 123, // in Bytes
            "free_blocks": 123,
            "healthcheck_percent": 10, // in Percent
            "healthcheck_interval": 60, // in minutes
            "hash_methods": [
                "<method>",
                "<method2>"
            ]
        },
        ...
    ]
}
```

## POST `/server?hostname=<hostname>`

Neuen Server einrichten.

### Response

Body:

```json
{
    "backup_code": "<backup-code>"
}
```

## PUT `/server?hostname=<hostname>`

Neuen Server akzeptieren (`IsVerified` wird auf `true` gesetzt).

### Response

Body:

```json
{
    "backup_code": "<backup-code>"
}
```

## DELETE `/server?hostname=<hostname>`

Server löschen.
