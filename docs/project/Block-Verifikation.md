# Verifikation von gesicherten Blöcken

In unserem föderierten Backupsystem implementieren wir eine Methode zur Überprüfung der Integrität von gespeicherten Blöcken auf anderen Servern. Dies ist entscheidend, um sicherzustellen, dass die Daten korrekt und zuverlässig gesichert sind.

## Funktionsweise

1. Hashing von Blöcken:  
   Bevor ein Block auf einem entfernten Server gespeichert wird, wird er gehasht, um einen eindeutigen Fingerabdruck zu erzeugen.
2. Salt-Versand mit Heartbeat:  
   Um sicherzustellen, dass der entfernte Server den Block tatsächlich speichert, senden wir regelmäßig ein Salt. Das Salt wird dazu verwendet, einen neuen Hash des Blocks auf dem anderen Server zu generieren.
3. Verifikation des gespeicherten Blocks:  
   Der entfernte Server erstellt aus dem gefragten Block und dem gesendeten Salt einen neuen Hash. Dieser Hash wird dann an uns zurückgesendet.
4. Überprüfung der Integrität:  
   Wir vergleichen den empfangenen Hash mit unserem eigenen. Wenn beide übereinstimmen, können wir sicher sein, dass der Block ordnungsgemäß gespeichert ist.

## Herausforderung

Als ersten Ansatz, wollten wir jeden Tag die Gesamtheit aller von uns gespeicherten Blöcke überprüfen. Das führt allerdings zu erheblichen Rechenaufwand auf dem anderen Server, was bei einem Backupmedium eigentlich vermieden werden sollte.

## Lösungsansatz

Um den Rechenaufwand zu minimieren, schlagen wir folgende Lösung vor:

- Tägliche Stichprobenprüfung:  
  Statt täglich alle Blöcke zu überprüfen, wählen wir einen Prozentsatz (zum Beispiel 10%) aller Blöcke, die auf einem bestimmten entfernten Server gespeichert sind, aus. Diese ausgewählten Blöcke werden dann wie oben beschrieben einer Prüfung unterzogen.
- Rotation der zu prüfenden Blöcke:  
  Die überprüften Blöcke werden aus dem Pool der zu prüfenden Blöcke entfernt. Dadurch wird sichergestellt, dass nach einer bestimmten Zeit alle Blöcke einmal geprüft wurden.
- Wiederherstellung des Pool-Zustands:  
  Sobald alle Blöcke überprüft wurden, werden sie wieder in den Pool der zu prüfenden Blöcke eingefügt, um sicherzustellen, dass das Überprüfungssystem kontinuierlich arbeitet.
