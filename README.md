# OpenFirma

<p align="center">
  <img src="design/icon/logo.svg" alt="OpenFirma logo" width="140" />
</p>

Applicazione desktop Java per la firma digitale locale e la verifica di documenti firmati, con interfaccia Windows ottimizzata per certificati presenti nello store `Windows-MY`.

OpenFirma gestisce due modalita di firma dalla stessa home page:

- `Busta P7M`
- `PDF` con firma grafica visibile in stile PAdES

## Cosa fa

OpenFirma copre il flusso operativo completo lato desktop:

- selezione certificato di firma da `Windows-MY`;
- drag-and-drop dei file da firmare;
- gestione di una coda firme dinamica;
- firma in busta `.p7m`;
- firma PDF con viewer integrato, zoom e posizionamento grafico;
- opzione di firma grafica su ogni pagina del PDF;
- verifica di file firmati P7M e PDF;
- ispezione dei firmatari e del documento firmato;
- gestione dei conflitti nella cartella di destinazione;
- consultazione log applicativi da popup dedicato;
- packaging Windows con runtime incluso.

## Funzionalita disponibili

### Home page

- layout a colonna singola;
- larghezza minima finestra `650px`;
- blocco certificato con popup dettagli;
- blocco `FILE DA FIRMARE` con drag-and-drop e link `NAVIGA TRA LE CARTELLE`;
- blocco `Coda delle firme` con altezza dinamica;
- scelta `Tipo firma` direttamente accanto alla coda;
- selezione cartella di destinazione;
- pulsanti `Firma file`, `Verifica`, `LOG`.

### Firma in busta P7M

- firma CMS allegata con estensione `.p7m`;
- supporto multi-file;
- verifica locale automatica a fine firma;
- svuotamento automatico della coda se la firma termina correttamente.

### Firma PDF

- popup dedicato con preview PDF integrata;
- selezione dei documenti tramite tab;
- zoom `+` e `-`;
- pulsante `Adatta larghezza` per vedere l'intera larghezza della pagina nel viewer;
- posizionamento grafico della firma con drag del riquadro;
- cambio pagina del documento corrente;
- ripristino della posizione di default;
- opzione `Firma su ogni pagina`.

### Verifica file firmati

- popup iniziale con drag-and-drop nello stesso stile della home;
- supporto multi-file;
- verifica esplicita tramite pulsante `Verifica`;
- risultati organizzati in tab, una per file;
- elenco firmatari;
- riepilogo date e metadati principali;
- pulsante `Visualizza documento` per aprire il contenuto verificato.

### Gestione conflitti in output

Prima della firma, OpenFirma verifica se nella cartella di destinazione esiste gia il file che sta per produrre.

Se trova un conflitto, apre un popup con:

- `SOVRASCRIVI`
- `ANNULLA`
- `VERIFICA FILE`
- `RINOMINA`

Con `VERIFICA FILE` viene aperto un popup di ispezione del file gia presente in output, con:

- nome file;
- data creazione;
- data firma;
- firmatari.

Con `RINOMINA` viene aperto un secondo popup che consente di definire un nuovo nome per il file in uscita e, con `OK`, proseguire la firma.

### Log applicativi

- il pannello log non e fisso nella home;
- il pulsante `LOG` apre un popup con il tracciamento operativo dell'app;
- i log registrano caricamento certificati, gestione file, firma, verifica e errori.

## Requisiti

- Windows;
- Java 21 installato e disponibile in `PATH`;
- token USB, smart card o certificato gia disponibile in `Windows-MY` per l'uso GUI;
- middleware del dispositivo correttamente installato, se richiesto;
- accesso internet al primo build per scaricare le dipendenze mancanti.

## Avvio rapido

Compila senza avviare la GUI:

```powershell
.\build.ps1 -BuildOnly
```

Compila e avvia la GUI:

```powershell
.\build.ps1
```

Avvia la GUI passando dalla build standard:

```powershell
.\run.ps1
```

## Packaging Windows

Genera la distribuzione Windows con runtime incluso:

```powershell
.\package.ps1
```

Rigenerazione pulita:

```powershell
.\package.ps1 -Clean
```

Output atteso:

```text
release\OpenFirma\OpenFirma.exe
```

## Flussi operativi GUI

### 1. Firma in busta P7M

Usa questo flusso quando devi produrre un file `.p7m`.

1. Avvia OpenFirma.
2. Seleziona o aggiorna il certificato nel blocco `Seleziona Certificato`.
3. Carica uno o piu file nel blocco `FILE DA FIRMARE`.
4. Verifica la `Cartella destinazione`.
5. Nel menu `Tipo firma`, scegli `Busta P7M`.
6. Controlla la `Coda delle firme`.
7. Premi `Firma file`.
8. Se esiste un conflitto in output, scegli tra `SOVRASCRIVI`, `ANNULLA`, `VERIFICA FILE` o `RINOMINA`.
9. Attendi il popup di avanzamento.
10. A fine firma, la coda viene svuotata automaticamente.

### 2. Firma PDF con firma grafica

Usa questo flusso quando devi firmare direttamente un PDF con firma visibile.

1. Avvia OpenFirma.
2. Seleziona il certificato di firma.
3. Carica uno o piu file PDF.
4. Nel menu `Tipo firma`, scegli `PDF`.
5. Premi `Firma file`.
6. Nel popup PDF:
7. Seleziona il documento tramite tab.
8. Usa `+`, `-` o `Adatta larghezza` per regolare la preview.
9. Posiziona il riquadro della firma nel punto desiderato.
10. Se necessario, attiva `Firma su ogni pagina`.
11. Passa agli altri documenti tramite tab e ripeti il posizionamento.
12. Premi `Avvia firma`.
13. Gestisci eventuali conflitti output dai popup dedicati.

### 3. Verifica di un file firmato

Usa questo flusso per controllare firme gia prodotte, P7M o PDF.

1. Premi `Verifica`.
2. Nel popup iniziale trascina i file oppure selezionali tramite il link.
3. Premi `Verifica`.
4. Consulta il risultato finale nelle tab:
5. verifica generale;
6. elenco firmatari;
7. data creazione;
8. data firma;
9. pulsante `Visualizza documento`.

### 4. Consultazione log

1. Premi `LOG`.
2. Leggi il tracciamento operativo della sessione.
3. Chiudi il popup quando hai terminato.

## Uso CLI

La CLI e utile per test, automazione o uso tecnico del backend.

### Elencare i certificati

Store Windows:

```powershell
java --add-modules jdk.crypto.cryptoki,jdk.crypto.mscapi -cp "out;lib\*" it.firmadigitale.AppLauncher --mode WINDOWS_MY --list
```

Archivio PKCS#12:

```powershell
java --add-modules jdk.crypto.cryptoki,jdk.crypto.mscapi -cp "out;lib\*" it.firmadigitale.AppLauncher --mode PKCS12 --resource ".\testdata\signer.p12" --secret "changeit" --list
```

### Firmare da CLI in P7M

```powershell
java --add-modules jdk.crypto.cryptoki,jdk.crypto.mscapi -cp "out;lib\*" it.firmadigitale.AppLauncher --mode PKCS12 --resource ".\testdata\signer.p12" --secret "changeit" --out ".\output" --file ".\testdata\sample.txt"
```

### Mostrare l'help CLI

```powershell
java --add-modules jdk.crypto.cryptoki,jdk.crypto.mscapi -cp "out;lib\*" it.firmadigitale.AppLauncher --help
```

## Struttura del progetto

```text
design/icon/                         asset SVG della UI
mockup/                              riferimenti di design
src/main/java/it/firmadigitale/      sorgenti Java
build.ps1                            build locale
package.ps1                          packaging Windows
run.ps1                              avvio applicazione
LICENSE                              licenza MIT
README.md                            documentazione progetto
```

## Note tecniche

- la GUI e centrata sullo store `Windows-MY`;
- il backend mantiene supporto tecnico anche a `PKCS12` e `PKCS11`;
- la verifica locale P7M usa CMS/CAdES embedded;
- la verifica locale PDF legge le firme presenti nel documento;
- la modalita PDF usa preview integrata e firma visibile;
- il packaging genera un'app Windows autonoma con runtime Java incluso.

## Licenza

Questo progetto e rilasciato come software open source sotto licenza `MIT`.
