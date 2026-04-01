package it.firmadigitale;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CliRunner {

    private final SignatureService signatureService;

    public CliRunner(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    public void run(String[] args) throws Exception {
        Options options = parse(args);

        if (options.help) {
            printUsage();
            return;
        }

        if (options.mode == null) {
            throw new IllegalArgumentException("Specificare --mode PKCS11|WINDOWS_MY|PKCS12");
        }

        SignatureConfig config = new SignatureConfig(options.mode, options.resource, options.secret);

        if (options.list) {
            List<SigningIdentity> identities = signatureService.listSigningIdentities(config);
            if (identities.isEmpty()) {
                System.out.println("Nessun certificato adatto alla firma trovato.");
                return;
            }
            for (SigningIdentity identity : identities) {
                System.out.println(
                        identity.alias()
                                + " | "
                                + identity.displayName()
                                + " | "
                                + identity.profileLabel()
                                + " | "
                                + identity.thumbprint()
                );
            }
            return;
        }

        if (options.files.isEmpty()) {
            throw new IllegalArgumentException("Specificare almeno un --file");
        }

        if (options.outputDirectory == null) {
            throw new IllegalArgumentException("Specificare --out <cartella-output>");
        }

        List<Path> signedFiles = signatureService.signFiles(config, options.alias, options.files, options.outputDirectory);
        for (Path signedFile : signedFiles) {
            boolean verified = signatureService.verifySignature(signedFile);
            System.out.println("Creato: " + signedFile + " | verifica locale: " + (verified ? "OK" : "KO"));
        }
    }

    private Options parse(String[] args) {
        Options options = new Options();

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--help" -> options.help = true;
                case "--list" -> options.list = true;
                case "--mode" -> options.mode = KeyStoreMode.parse(requireValue(args, ++index, "--mode"));
                case "--resource" -> options.resource = requireValue(args, ++index, "--resource");
                case "--secret" -> options.secret = requireValue(args, ++index, "--secret");
                case "--alias" -> options.alias = requireValue(args, ++index, "--alias");
                case "--out" -> options.outputDirectory = Path.of(requireValue(args, ++index, "--out"));
                case "--file" -> options.files.add(Path.of(requireValue(args, ++index, "--file")));
                default -> throw new IllegalArgumentException("Argomento non riconosciuto: " + argument);
            }
        }

        return options;
    }

    private String requireValue(String[] args, int index, String optionName) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Valore mancante per " + optionName);
        }
        return args[index];
    }

    private void printUsage() {
        System.out.println("Uso:");
        System.out.println("  --mode PKCS11|WINDOWS_MY|PKCS12");
        System.out.println("  [--resource <dll-o-p12>]");
        System.out.println("  [--secret <pin-o-password>]");
        System.out.println("  [--alias <alias-certificato>]");
        System.out.println("  [--list]");
        System.out.println("  [--out <cartella-output> --file <path> --file <path> ...]");
    }

    private static final class Options {
        private boolean help;
        private boolean list;
        private KeyStoreMode mode;
        private String resource = "";
        private String secret = "";
        private String alias = "";
        private Path outputDirectory;
        private final List<Path> files = new ArrayList<>();
    }
}
