# OpenFirma

<p align="center">
  <img src="design/icon/logo.svg" alt="OpenFirma logo" width="140" />
</p>

Applicazione desktop Java per la firma locale di documenti in formato `.p7m` tramite certificati di firma disponibili nello store `Windows-MY`.

## Obiettivo

OpenFirma e pensata per un uso operativo reale su postazioni Windows che vedono token USB, smart card o certificati installati localmente. La GUI privilegia il flusso `Windows-MY` e filtra automaticamente i certificati non idonei alla firma.

## Funzionalita rilasciate

- interfaccia desktop a colonna singola con layout ottimizzato per Windows;
- drag-and-drop dei file da firmare e selezione multipla da file chooser;
- coda firme dinamica con conteggio file, peso totale e rimozione rapida dei documenti;
- selezione del certificato di firma con popup dedicato al dettaglio certificato;
- gestione cartella di destinazione direttamente nel blocco della coda;
- controllo pre-firma dei conflitti in output;
- popup conflitto con azioni `SOVRASCRIVI`, `ANNULLA`, `VERIFICA FILE` e `RINOMINA`;
- verifica del file gia presente in output con riepilogo nome file, data creazione, data firma e firmatari;
- rinomina del nuovo output prima della firma per mantenere i file gia presenti;
- popup di avanzamento firma con stato per ogni file e verifica locale post-firma;
- svuotamento automatico della coda al termine di una firma completata con successo;
- popup `LOG` per consultare il tracciamento operativo dell'app;
- CLI tecnica per automazione, test e diagnostica;
- packaging Windows con runtime incluso.

## Requisiti

- Windows;
- Java 21 installato e disponibile in `PATH`;
- middleware del token o della smart card gia installato;
- accesso internet al primo build per scaricare le dipendenze.

## Avvio rapido

Compila e avvia la GUI:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\build.ps1
```

Compila senza aprire la GUI:

```powershell
.\build.ps1 -BuildOnly
```

Avvia una build gia compilata:

```powershell
.\run.ps1
```

## Packaging Windows

Genera la distribuzione Windows con runtime incluso:

```powershell
.\package.ps1
```

Output previsto:

```text
release\OpenFirma\OpenFirma.exe
```

Rigenerazione pulita:

```powershell
.\package.ps1 -Clean
```

## Flusso operativo

1. Collega token o smart card e verifica che il certificato sia visibile in Windows.
2. Avvia OpenFirma.
3. Seleziona il certificato di firma proposto.
4. Carica uno o piu file da firmare.
5. Scegli la cartella di destinazione.
6. Avvia la firma.
7. In caso di file gia presenti in output, scegli se sovrascrivere, verificare o rinominare il nuovo output.

## Uso CLI

Elenco certificati in `Windows-MY`:

```powershell
.\run.ps1 --mode WINDOWS_MY --list
```

Firma di un file con certificato presente nello store Windows:

```powershell
.\run.ps1 --mode WINDOWS_MY --alias "mio-alias" --out ".\output" --file "C:\docs\contratto.pdf"
```

Firma di test con `PKCS#12`:

```powershell
.\run.ps1 --mode PKCS12 --resource ".\testdata\signer.p12" --secret "changeit" --out ".\output" --file ".\testdata\sample.txt"
```

## Struttura progetto

```text
design/icon/
src/main/java/it/firmadigitale/
build.ps1
package.ps1
run.ps1
```

## Note tecniche

- il file prodotto e una firma CMS allegata con estensione `.p7m`;
- la verifica locale controlla la validita della firma CMS generata;
- la GUI e orientata al flusso `Windows-MY`, mentre il backend mantiene anche supporto tecnico a `PKCS#11` e `PKCS#12`;
- gli asset grafici SVG della UI sono in `design/icon`.

## Licenza

Questo progetto e rilasciato come software open source sotto licenza `MIT`.
