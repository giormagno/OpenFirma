# JavaSign

Applicazione desktop Java per firmare uno o piu file in formato `.p7m` usando certificati di firma qualificata disponibili su Windows.

Il progetto e pensato per un caso operativo preciso:
- firma locale dal PC che vede chiavetta USB o smart card;
- accesso al certificato tramite store `Windows-MY`;
- esclusione dei certificati `AUT/CNS`;
- utilizzo dei soli certificati `SIG/QES` idonei alla firma qualificata.

## Stato del progetto

Attualmente la GUI e focalizzata su `Windows-MY`, che e la modalita richiesta per l'uso reale su postazioni Windows con middleware gia installato.

Nel backend restano disponibili anche modalita tecniche per test o diagnostica:
- `PKCS#11`
- `PKCS#12`

## Funzioni principali

- caricamento multiplo documenti;
- drag-and-drop nella GUI;
- lista documenti in coda con selezione rapida del file attivo;
- filtro automatico dei certificati qualificati `SIG/QES`;
- produzione di file `nomefile.ext.p7m`;
- verifica locale post-firma;
- CLI per automazione e test.

## Requisiti

- Windows;
- Java 21 installato e disponibile in `PATH`;
- middleware del token o della smart card gia installato nel sistema;
- accesso internet al primo build per scaricare le dipendenze Bouncy Castle.

## Avvio rapido

Compila e apre la GUI:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\build.ps1
```

Compila senza aprire la GUI:

```powershell
.\build.ps1 -BuildOnly
```

Avvia la GUI usando una build gia compilata:

```powershell
.\run.ps1
```

## Packaging Windows

Per generare una cartella Windows eseguibile con runtime incluso:

```powershell
.\package.ps1
```

L'output viene creato qui:

```text
release\JavaSign\JavaSign.exe
```

Se vuoi rigenerare tutto da zero:

```powershell
.\package.ps1 -Clean
```

## Uso operativo

1. collega token o smart card al PC;
2. verifica che Windows veda il certificato di firma;
3. avvia l'app;
4. seleziona il certificato `SIG/QES` proposto;
5. carica uno o piu file;
6. scegli la cartella di output;
7. firma i documenti.

I file firmati vengono generati con estensione `.p7m` nella directory di output selezionata.

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

## Struttura del progetto

```text
src/main/java/it/firmadigitale/
  AppLauncher.java
  SignatureFrame.java
  SignatureService.java
  CliRunner.java
  SigningIdentity.java
  CertificateProfile.java
```

## Note tecniche

- il file prodotto e una firma CMS allegata con estensione `.p7m`;
- viene aggiunto l'attributo `signingCertificateV2`;
- la GUI mostra solo certificati qualificati coerenti con il flusso `Windows-MY`;
- per conformita piu avanzata, LTV o marca temporale, il passo successivo naturale e l'integrazione con una libreria specialistica come DSS.

## Limitazioni attuali

- il progetto non e pensato come servizio remoto: la firma deve partire dalla macchina locale;
- la UI e in evoluzione e sta convergendo verso un layout piu vicino a una web app tecnica;
- il comportamento reale dipende anche dal middleware installato e dal tipo di dispositivo usato.

## Roadmap breve

- ulteriore rifinitura UI/UX;
- miglioramento gestione coda file;
- verifica piu esplicita del certificato selezionato;
- eventuale packaging come eseguibile Windows.
