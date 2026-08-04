# Etappe 4 „Gestalten": Text drehen und gezeichnete Rahmen

Stand 2026-08-04. Zwei Wünsche des Nutzers, die das Blatt betreffen — nicht den Text.
Deshalb eine eigene Etappe neben den Absatzstilen (Etappe 3 Teil 4), die den Text formen.

## Teil 1: Text in 90°-Schritten drehen

### Wozu

Der Anlass ist die Maschine, nicht der Geschmack: **A6 hoch (105 × 148 mm) passt nicht auf den
Tisch (155 × 105 mm).** Man kann ein A6-Blatt hochkant beschreiben — aber nur, indem man es
quer auf den Tisch legt und den Text dreht. Bis Etappe 4 gab es dafür keinen Weg.

### Entschieden

- **Vier Stufen** (0/90/180/270), keine freie Winkelwahl. Das gedrehte Rechteck bleibt
  achsparallel, und damit bleibt „passt das noch aufs Blatt?" exakt beantwortbar. Ein freier
  Regler wäre außerdem leicht versehentlich zu verstellen.
- **Der Satz dreht sich im Rahmen, nicht der Rahmen.** Der Rahmen bleibt das Rechteck, das auf
  dem Blatt platziert wurde; bei 90°/270° wird darin auf einer Fläche mit vertauschten Maßen
  gesetzt und das Ergebnis hineingekippt. Aus einem Rahmen 132 × 89 wird so eine Satzfläche
  89 breit × 132 hoch.
- Die Drehung gehört zum **Rahmen**, nicht zum Stil: Sie betrifft die ganze Seite, nicht
  einzelne Absätze. Also global in `AppSettings` und in der Vorlage.

### Wie

Die Drehung wirkt als **letzter Schritt** über die fertigen Züge (`layoutAbsaetze` →
`gedreht`). Dadurch braucht der ganze Umbruch, die Ausrichtung und die Einlauf-Korrektur von
ihr nichts zu wissen. Die Ränder drehen mit.

90° dreht **gegen den Uhrzeigersinn**: die erste Zeile kippt nach links.

Vorschau und G-Code brauchten **keine** Änderung — beide arbeiten auf denselben fertigen
Strichzügen. Genau dafür ist diese Invariante da.

## Teil 2: Gezeichnete Rahmen

### Entschieden

- **Um den Textrahmen, mit einstellbarem Abstand** nach außen. Der Textsatz bleibt dadurch
  völlig unberührt; es entsteht keine neue Wechselwirkung mit Umbruch und Einpassen.
- **Selbst gerechnet, nicht als Grafik mitgeliefert.** Eine fertige Zeichnung müsste auf jedes
  Seitenverhältnis gedehnt werden, und ein in die Länge gezogener Schnörkel sieht sofort falsch
  aus. Gerechnet richten sich Eckradius und Zier nach der **kürzeren** Seite und bleiben
  unverzerrt. Nebenbei entfällt die Lizenzfrage.
- Fünf Formen: Rechteck, Doppellinie, abgerundet, Sprechblase (mit wählbarer Zipfelseite),
  Zierecken (eingezogene Ecken).
- **Der Rahmen dreht sich nicht mit dem Text mit.** Er hängt am Rahmen, nicht am Satz — eine
  gedrehte Textseite in einem geraden Rahmen ist das, was man erwartet.

### Was sich beim Bauen zeigte

1. **Bögen werden an der Größe bemessen aufgelöst** (Sehnenfehler unter 0,05 mm), nicht mit
   fester Punktzahl. Sonst bekäme ein kleiner Bogen unnötig viele G-Code-Zeilen und ein großer
   würde sichtbar eckig.
2. **Der Mittelpunkt jedes Eckbogens gehört in die Ecke selbst.** Der erste Versuch setzte die
   Bögen frei; Bogenanfang und Kantenende liefen auseinander, und das Musterbild zeigte schiefe
   Kanten und zufällige Halbkreise. Mit dem Mittelpunkt in der Ecke treffen sie zwangsläufig
   aufeinander. **Ohne das Musterbild wäre das nicht aufgefallen** — die Tests waren grün.
3. **`orderedStrokes` liest aus `LaidOutText.lines`, nicht aus `strokes`.** Ein Rahmen steht in
   keiner Zeile und fiel dadurch stillschweigend aus dem G-Code (0 Hübe im Musterbild). Dafür
   gibt es jetzt `plotJobAus(zuege, profil)`; das ViewModel legt Rahmen und Text selbst
   zusammen — **Rahmen zuerst**, weil ein paar lange Züge am Anfang sofort zeigen, ob das Blatt
   richtig liegt, bevor Hunderte Zeilen Text laufen.
4. **Die Grenzprüfung braucht die GESAMTEN Züge.** Der Rahmen liegt weiter außen als jeder
   Buchstabe; prüfte man nur den Text, schlüge das Softlimit erst mitten im Auftrag zu — mit
   halb beschriebenem Blatt. Dazu die eigene Warnung `zierrahmenPasstAufsBlatt`: Der Textkasten
   kann bequem passen und der Rahmen darum trotzdem über die Karte hinausragen.

## Datenhaltung

Room **5 → 6** (Drehung) und **6 → 7** (Rahmenform, Abstand, Zipfelseite), beide nur mit
angehängten Spalten. Vorhandene Vorlagen stehen aufrecht und bekommen keinen Rahmen — alles
andere wäre eine Änderung am Ergebnis, die niemand angeordnet hat.

## Verifikation

408 Tests grün. Am Gerät geprüft: beide Migrationen liefen, 90° zeigt den Text hochkant im
Rahmen, die Sprechblase umschließt den Textkasten mit Abstand, die Kennzahlen enthalten den
Rahmen. Danach beides wieder zurückgestellt.

**Offen: die Maschine.** Weder Drehung noch Rahmen sind auf Papier gefahren.
