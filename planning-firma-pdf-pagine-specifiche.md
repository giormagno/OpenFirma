# Planning firma PDF su pagine specifiche

## Obiettivo

In modalita firma PDF, permettere all'utente di scegliere pagine specifiche su cui mostrare la firma visibile.

Interazione richiesta:

- l'utente naviga il PDF;
- su ogni pagina desiderata fa doppio click nel punto in cui vuole inserire la firma;
- il doppio click conferma la firma per quella pagina;
- il documento viene firmato una sola volta in formato PAdES, con apparizioni visibili sulle pagine confermate.

## Stato attuale

### GUI

File: `src/main/java/it/firmadigitale/SignatureFrame.java`

- `PdfSigningDialog` mantiene una sola `PdfVisibleSignaturePlacement` per file.
- La navigazione pagina sposta quella singola posizione da una pagina all'altra.
- `signEveryPageCheckBox` abilita il comportamento "firma su ogni pagina".
- `PdfSigningPlan` contiene:
  - `placement`
  - `applyToEveryPage`

### Servizio firma

File: `src/main/java/it/firmadigitale/SignatureService.java`

- `signPdfFile(...)` riceve una sola posizione e un booleano `applyToEveryPage`.
- Se `applyToEveryPage == false`, usa una firma PDFBox con visual signature widget su una pagina.
- Se `applyToEveryPage == true`, timbra graficamente tutte le pagine e poi applica una sola firma digitale al PDF.
- Esistono gia helper riutilizzabili:
  - `stampVisibleSignatureOnEveryPage(...)`
  - `stampVisibleSignatureOnPage(...)`
  - `remapPdfPlacement(...)`
  - `createPdfVisibleSignatureTemplate(...)`

## Scelta tecnica validata

Usare una singola firma digitale PAdES per documento, con una o piu apparizioni visibili.

Motivo:

- e coerente con l'attuale implementazione "firma su ogni pagina";
- evita di introdurre firme crittografiche multiple sequenziali;
- riduce il rischio di regressioni;
- risponde all'esigenza utente: scegliere su quali pagine mostrare la firma.

## Modifica GUI pianificata

### Stato per documento

Sostituire lo stato singolo:

- `Map<Path, PdfVisibleSignaturePlacement> placements`
- `Map<Path, Boolean> signEveryPageFlags`

con uno stato esplicito:

- pagina corrente per documento;
- posizione preview corrente per pagina;
- mappa delle pagine confermate:
  - `Map<Path, Map<Integer, PdfVisibleSignaturePlacement>> confirmedPlacements`

Mantenere una posizione preview anche per pagine non confermate, cosi l'utente puo continuare a trascinare/disegnare il riquadro prima del doppio click.

### Interazione doppio click

Nel `PdfPreviewSurface`:

- intercettare `MouseEvent` con `getClickCount() >= 2`;
- convertire il punto di doppio click in un rettangolo firma usando dimensioni correnti o dimensioni standard;
- clampare il rettangolo dentro la pagina;
- salvare la posizione in `confirmedPlacements` per la pagina corrente;
- aggiornare subito la preview e il contatore.

Nota: la sezione seguente integra e supera la regola iniziale a una firma per pagina.

## Integrazione: piu firme sulla stessa pagina

### Nuovo obiettivo

Consentire due o piu firme visibili sulla stessa pagina PDF.

Interazione richiesta:

- ogni doppio click aggiunge una nuova firma visibile nella pagina corrente;
- la pagina corrente puo contenere piu rettangoli firma;
- l'utente deve poter eliminare almeno l'ultima firma della pagina corrente;
- il contatore deve indicare il totale delle firme visibili confermate, non solo il numero di pagine.

### Modifica stato GUI

Lo stato attuale indicizzato come `pageIndex -> placement` deve diventare `pageIndex -> List<placement>`.

Aggiornare in `PdfSigningDialog`:

- `confirmedPlacements` da:
  - `Map<Path, Map<Integer, PdfVisibleSignaturePlacement>>`
- a:
  - `Map<Path, Map<Integer, List<PdfVisibleSignaturePlacement>>>`

Mantenere `previewPlacements` come singola preview per pagina:

- serve a definire dimensione e posizione del prossimo rettangolo;
- il doppio click deve creare una nuova placement dalla preview centrata sul punto cliccato;
- dopo l'aggiunta, la preview puo restare sull'ultimo rettangolo inserito.

### Interazione doppio click aggiornata

Nel doppio click:

1. calcolare il rettangolo firma centrato sul punto cliccato, usando dimensioni della preview corrente;
2. convertire in `PdfVisibleSignaturePlacement`;
3. aggiungerlo alla lista della pagina corrente;
4. aggiornare preview, contatore e stato pulsanti.

Nuova regola:

- doppio click non sostituisce piu la firma esistente;
- ogni doppio click aggiunge una nuova firma;
- non serve introdurre un limite numerico, salvo clamp del rettangolo dentro la pagina.

### Rimozione

Aggiornare il pulsante `Rimuovi firma pagina`:

- comportamento minimo: rimuove l'ultima firma confermata nella pagina corrente;
- se dopo la rimozione la lista e vuota, rimuovere la chiave della pagina dalla mappa;
- abilitare il pulsante solo quando la pagina corrente contiene almeno una firma confermata;
- testo consigliato: `Rimuovi ultima firma`.

### Preview

`PdfPreviewSurface` deve disegnare:

- tutti i rettangoli confermati della pagina corrente;
- la preview del prossimo rettangolo, distinguendola visivamente dai rettangoli gia confermati.

Evitare testo lungo dentro i rettangoli: usare label brevi come `Firma` e `Nuova`.

### Firma tutte

Il pulsante `Firma tutte` deve restare efficiente:

- usare la preview corrente come rettangolo base;
- sostituire le firme confermate con una firma per ogni pagina;
- non duplicare firme esistenti su ogni pagina.

### Preparazione piano firma

In `confirmAndStartSigning()`:

- appiattire tutte le liste `pageIndex -> List<placement>` in una sola `List<PdfVisibleSignaturePlacement>`;
- ordinare per pagina e poi per ordine di inserimento;
- richiedere almeno una firma confermata per ogni PDF.

### Modifica servizio

In `SignatureService.validateAndNormalizePdfPlacements(...)`:

- rimuovere il vincolo che vieta piu placement con lo stesso `pageIndex`;
- mantenere validazione pagina, null e clamp rettangolo;
- ordinare in modo stabile per `pageIndex`, preservando l'ordine relativo delle firme sulla stessa pagina.

Comportamento firma:

- se la lista contiene una sola placement, mantenere il flusso widget visual signature PDFBox;
- se contiene due o piu placement, usare `stampVisibleSignatureOnPage(...)` per ogni placement e poi una sola firma digitale PAdES.

### Toolbar aggiornata

Rimuovere o sostituire `Firma su ogni pagina` con controlli piu chiari:

- badge/label: `Firme: N`;
- pulsante `Rimuovi ultima firma`;
- opzionale pulsante `Firma tutte` per preservare l'attuale flusso rapido, usando la posizione corrente remappata su tutte le pagine.

Comportamento:

- `Rimuovi ultima firma` attivo solo se la pagina corrente ha almeno una firma confermata;
- `Ripristina posizione` resta limitato alla preview della pagina corrente;
- `Avvia firma` richiede almeno una firma confermata per ogni PDF.

### Preview aggiornata

Aggiornare il disegno:

- tutte le firme confermate della pagina corrente;
- la preview del prossimo rettangolo;
- label brevi: `Firma` per confermate, `Nuova` per preview.

## Modifica servizio pianificata

### API interna

Aggiungere un overload o modificare il metodo GUI-oriented:

```java
signPdfFile(..., List<PdfVisibleSignaturePlacement> placements, ...)
```

Mantenere il metodo esistente come wrapper per compatibilita:

- singola pagina: lista con un elemento;
- tutte le pagine: lista generata tramite `remapPdfPlacement(...)`.

### Firma

Validare che:

- la lista posizioni non sia nulla o vuota;
- ogni `pageIndex` sia valido;
- piu placement sulla stessa pagina siano accettate.

Comportamento:

- 1 sola posizione: mantenere il flusso attuale con `SignatureOptions` e visual signature widget;
- piu posizioni: usare `stampVisibleSignatureOnPage(...)` per ogni posizione e poi `document.addSignature(signature)`.

La gestione temp-and-replace introdotta per la sovrascrittura deve restare invariata.

## Dati passati al processo

Aggiornare `PdfSigningPlan`:

```java
private record PdfSigningPlan(
        List<SignatureService.PdfVisibleSignaturePlacement> placements
) {}
```

In `startSigningProcess(...)`, quando `signingMode == PDF`, passare la lista al nuovo overload del servizio.

## Verifiche

1. Build:
   - `.\build.ps1 -BuildOnly`

2. Test manuale singola pagina:
   - aprire PDF multipagina;
   - doppio click su una pagina;
   - avviare firma;
   - verificare una sola apparizione visibile.

3. Test manuale pagine specifiche:
   - doppio click su pagina 1;
   - navigare a pagina 3;
   - doppio click su pagina 3;
   - avviare firma;
   - verificare apparizioni solo su pagina 1 e 3.

4. Test riposizionamento:
   - doppio click due volte sulla stessa pagina in punti diversi;
   - verificare che vengano prodotte due firme visibili sulla stessa pagina.

5. Test rimozione:
   - confermare firma su una pagina;
   - aggiungere una seconda firma sulla stessa pagina;
   - rimuovere l'ultima firma pagina;
   - verificare che rimanga la prima firma;
   - avviare firma solo dopo aver confermato almeno una pagina.

6. Test stessa pagina:
   - inserire tre firme sulla stessa pagina;
   - avviare firma;
   - verificare che tutte e tre siano visibili.

7. Test regressione:
   - conflitto output PDF gia esistente;
   - sovrascrittura;
   - rinomina;
   - firma P7M invariata.

## Ambito delega

File modificabili:

- `src/main/java/it/firmadigitale/SignatureFrame.java`
- `src/main/java/it/firmadigitale/SignatureService.java`

Non modificare documentazione, packaging o asset salvo richiesta esplicita.
