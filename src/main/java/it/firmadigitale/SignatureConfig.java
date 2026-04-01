package it.firmadigitale;

public record SignatureConfig(KeyStoreMode mode, String resourcePath, String secret) {

    public SignatureConfig {
        if (mode == null) {
            throw new IllegalArgumentException("La modalita del keystore e obbligatoria.");
        }
        resourcePath = resourcePath == null ? "" : resourcePath.trim();
        secret = secret == null ? "" : secret;
    }

    public char[] secretChars() {
        return secret.toCharArray();
    }
}
