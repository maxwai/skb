# Blockstruktur im Backupsystem

Ein Block in unserem Backupsystem hat eine spezifische Struktur, die es ermöglicht, Daten effizient zu speichern und wiederherzustellen. Die Größe eines Blocks wird durch eine Konfigurationsdatei festgelegt.

## Blockaufbau

Ein Block besteht aus mehreren Teilen:

1. **Header**: Dieser Teil des Blocks speichert Informationen darüber, welche Bytes im Block zu welchen Dateien gehören. Es hat das folgende Format:

    ```raw
    xy<filename><0x1E>...<0x1D>
    ```

   In diesem Format repräsentieren x und y den Bytebereich in der Datei (in Big-Endian und als 64-Bit-Zahlen). Die Zahlen sind hierbei immer inclusive-exclusive angegeben, also [x, y). <0x1E> und <0x1D> sind spezielle Trennzeichen in Unicode, welche wir in Dateipfaden nicht erlauben.

   Dabei ist \<filename> nur der Name der Datei, nicht der Pfad

2. **Daten**: Dies sind die eigentlichen Daten, die in den Block geschrieben werden. Sie werden in der Reihenfolge `<data1><data2><data3>...` gespeichert, wobei jedes `data` ein Bytebereich aus der Originaldatei ist. Die Länge der Blöcke lässt dich durch den Bytebereich berechnen.

## Beispiel

Angenommen, wir haben eine Blockgröße von 4096 Bytes und wir speichern Daten aus zwei Dateien `file1.txt` und `file2.txt`. Ein Beispiel für einen Block könnte so aussehen: (Die Leerzeichen sind nur zur Veranschaulichung da)

```raw
0x0000000000000000 0x0000000000000800 file1.txt<0x1E>0x0000000000000000 0x00000000000007CC file2.txt<0x1D><data1><data2>
```

In diesem Beispiel repräsentiert `data1` die ersten 2048 Bytes von `file1.txt`, während `data2` die ersten 1996 Bytes von `file2.txt` repräsentieren. Die kleinere Größe kommt davon dass der Header Platz nimmt.
