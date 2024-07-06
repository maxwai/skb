# Aufsetzten der CLI

1. Client Herunterladen (zum Beispiel von der Gitlab Release Seite)
    - wird mit `skb-cli [OPTIONS] <COMMAND>` (Linux) oder `.\skb-cli.exe [OPTIONS] <COMMAND>` (Windows) genutzt
    - im folgenden wird die Linux Schreibweise verwendet
2. `private.pem` Schlüssel zu dem auf dem Server hinterlegten `public.pem` Schlüssel und Konfiguration
   - Das Schlüsselpaar wird [hier](Admin.md) vom Admin erstellt
3. `config.json` welche die URL zum Server enthält

    ```json
    {
      "url": "https://skb.example.com/api/client/v1"
    }
    ```

4. die Dateistruktur sollte muss nun, mit Ausnahme vom Namen `some-folder`, so aussehen

    ```text
    some-folder
    |- config.json
    |- private.pem
    |- skb-cli[.exe]
    ```

5. Verbindung überprüfen `skb-cli status`

# Erstes Backup

- Dateien mit `skb-client file add <FILE-PATH>` hinterlegen und mit `skb-client file sync` zum Server synchronisieren
- um einen Backup Server für zusätzliche Redundanz hinzufügen zu können wird `skb-client server new <HOSTNAME>` verwendet
  - bevor dieser Server in Kraft tritt muss dessen Admin die Verbindung erst mit `skb-client server verify <HOSTNAME>` akzeptieren
  - wenn der andere Server die Verbindung zugelassen hat kann `skb-cli server discover <DEPTH>` weitere potentielle Backup Server liefern
- wenn eine lokale Datei nun verloren geht kann diese mit `skb-cli file download <FILE-PATH>` wiederhergestellt werden
- soll eine Datei nichtmehr synchronisiert und von allen Systemen entfernt werden muss `skb-cli file delete <FILE-PATH>` genutzt werden
- damit alle Datein die im System aufgenommen wurden gesehen werden können gitbt es `skb-cli file list`
- der Stand der Daten wird mit `skb-cli status` ausgegeben

# Ausführliche Liste aller Kommandos

> Aufrufe haben das Format `skb-cli [OPTIONS] <COMMAND> ...`  

| Command                           | Info                                                       |
| --------------------------------- | ---------------------------------------------------------- |
| skb-cli status                    | Server Status ausgeben                                     |
| skb-cli server list               | Verbundene Server auflisten                                |
| skb-cli server discover \<DEPTH>  | Neue Server finden. DEPTH tief Server Suchen               |
| skb-cli server verify \<HOSTNAME> | Neuen Server bestätigen                                    |
| skb-cli server new \<HOSTNAME>    | Neuen Server hinzufügen                                    |
| skb-cli server delete \<HOSTNAME> | Server Löschen                                             |
| skb-cli file list                 | Gesicherte Dateien auflisten                               |
| skb-cli file add \<FILE>          | Neue Datei sichern                                         |
| skb-cli file update \<FILE>       | Datei auf dem Server updaten                               |
| skb-cli file download \<FILE>     | Lokale Datei updaten                                       |
| skb-cli file delete \<FILE>       | Datei Löschen vom Server                                   |
| skb-cli file sync                 | Dateien updated mit neuester Version (in beide Richtungen) |

## Anmerkungen

- \<HOSTNAME> folgt dem [HTTP Host Format](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/host)
- \<FILE> ist entweder ein relativer oder absoluter Pfad zu einer spezifischen Datei. Es gibt keine Wildcard Optionen (mit `*` oder `.`)

## Optionen

| Option                   | Info                                     |
| ------------------------ | ---------------------------------------- |
| `-v` or `--verbose`      | Verbose level, bis zu 3 Level            |
| `-k` or `--allow-unsafe` | Selbs-signierte TLS Zertifikate erlauben |
| `-h` or `--help`         | Hilfe zeigen                             |
| `-V` or `--version`      | Version zeigen                           |
