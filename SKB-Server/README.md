# SKB-Server

Java Project using Maven and Quarkus. Uses Java 17.

## Structure

- `src/main`
  - `java/edu/hm/skb`
    - `api` -- Package für die REST APIs
      - `client` -- Schnittstelle für den Client
      - `fed` -- Kommunikation zwischen Servern
      - `bak` -- Datenaustausch zwischen Servern
    - `config` -- lesen/ändern Konfiguration des Servers
    - `data` -- Funktionen für Dateioperationen
    - `util`
      - `model` -- Objekt Modelle für die JSON Kommunikation
    - `worker` -- Worker Threads des Servers
  - `resources` -- microprofile Konfigurationen für den Quarkus Server

## Run Quarkus Service in dev mode

```bash
./mvnw quarkus:dev
```

```bash
./mvnw quarkus:dev -Dquarkus.profile=dev2
```

## Format code

```bash
./mvnw formatter:format
```

Validate:

```bash
./mvnw formatter:validate
```

## PMD

```bash
./mvnw pmd:pmd
```

## Roadmap

- Fix multiple TODOs and FIXME comments:
  - `ClientResource#getFilteredBlockSize`: fix size calculation
  - `DataInstance#createFile`: reuse blocks
  - `DataInstance#updateFile`: handle if file is bigger/smaller
  - `DataInstance#getBlock`: encrypt block content
  - `HashMethods#checkIntegrity`: check the HashMethods
  - `BackupWorker#backupKnownBlocks`: what happens when a deleted Block couldn't be deleted on the remote server
- Add handling if a server is not online anymore
- Add handling if a healthcheck finds a problem with a block (the same handling needs to be applied in the BackupWorker when checking the integrity)
- Add handling if a server declared a maintenance window
- If adding a new server and all blocks are already synced on other servers, balance the blocks to spread out the blocks more
- Add workflow if one server has deleted a lot of his blocks on another server and the first server wants to gracefully allow the other server to relocate the surplus blocks
- When making requests over other api paths (for example the client api to the fed api on another server), add correct error handling
- Add Integration Tests for the api
  - CLI Client <-> client api
  - client api <-> fed api
  - fed api <-> fed api
  - fed api <-> backup api
- Add correct mTLS Integration, currently every server is accepted, no certificate verification is made with CAs
- Add `/.well-known/skb` path
- Add `GET /api/client/v1/server/{hostname}` API Path where a client can get information about a server without adding it
- Blöcke erstellen wenn Server nicht gleiche Blockgröße haben
