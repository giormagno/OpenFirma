# Planning

## Obiettivo

Quando il file di output previsto esiste gia nella cartella di destinazione, il software deve chiedere all'utente se sovrascrivere o rinominare il file, senza rinominare automaticamente con suffisso `_firmato`.

## Analisi attuale

- Il flusso GUI usa `SignatureFrame.resolveOutputTargets(...)` per calcolare i target e intercettare file gia esistenti.
- Esiste gia un popup con azioni `SOVRASCRIVI`, `RINOMINA`, `VERIFICA FILE`, `ANNULLA`.
- Il suffisso automatico `_firmato` e applicato in `SignatureFrame.resolveSignedOutputFileName(...)` per la modalita PDF quando il target coincide col file sorgente.
- La scrittura effettiva avviene in `SignatureService.signFile(...)` e `SignatureService.signPdfFile(...)`.

## Modifica pianificata

1. In `SignatureFrame.resolveSignedOutputFileName(...)`, rimuovere la rinomina automatica `_firmato` per i PDF quando il target coincide col sorgente.
2. Lasciare che `resolveOutputTargets(...)` rilevi quel target come conflitto reale e mostri il popup gia esistente.
3. Migliorare `showRenameOutputPopup(...)` per proporre come nome iniziale un candidato sicuro, ad esempio `nome_firmato.pdf`, solo dopo scelta esplicita di `RINOMINA`.
4. Mantenere invariati i flussi `VERIFICA FILE`, `ANNULLA` e gestione conflitti multipli.
5. In `SignatureService.signPdfFile(...)`, gestire in modo sicuro il caso in cui `inputFile` e `outputFile` coincidono:
   - firmare su file temporaneo nella stessa cartella;
   - sostituire il file finale solo a firma completata;
   - eliminare il temporaneo in caso di errore.
6. Valutare la stessa strategia temp-and-replace per `SignatureService.signFile(...)` solo se il target esiste gia, per evitare perdita del vecchio output in caso di errore durante sovrascrittura.

## Vincoli

- Non modificare UI, testi o flussi non collegati ai conflitti di output.
- Non cambiare la firma CLI salvo necessita tecnica diretta.
- Non cambiare il nome standard degli output non conflittuali:
  - P7M: `nome_originale.ext.p7m`
  - PDF: `nome_originale.pdf`

## Verifiche

1. Build: `.\build.ps1 -BuildOnly`.
2. Test manuale GUI PDF:
   - output diverso dal sorgente e libero: nessun popup;
   - output uguale al sorgente: popup conflitto;
   - `SOVRASCRIVI`: firma completata sullo stesso nome;
   - `RINOMINA`: proposta `*_firmato.pdf`, modificabile;
   - `ANNULLA`: firma non avviata.
3. Test manuale GUI P7M:
   - output libero: comportamento invariato;
   - output `.p7m` gia esistente: popup conflitto.
4. Verificare che i log riportino sovrascrittura, rinomina o annullamento senza messaggi fuorvianti.

## Delega

La delega esecutiva richiesta con modello `5.3-codex` non puo partire: gli strumenti disponibili espongono solo `gpt-5.5`, `gpt-5.4` e `gpt-5.4-mini`.

