package it.firmadigitale;

public enum KeyStoreMode {
    PKCS11("PKCS#11", true, "Token USB o smart card tramite DLL"),
    WINDOWS_MY("Windows-MY", false, "Certificati nello store Windows"),
    PKCS12("PKCS#12", true, "File P12 o PFX");

    private final String label;
    private final boolean requiresResourcePath;
    private final String description;

    KeyStoreMode(String label, boolean requiresResourcePath, String description) {
        this.label = label;
        this.requiresResourcePath = requiresResourcePath;
        this.description = description;
    }

    public boolean requiresResourcePath() {
        return requiresResourcePath;
    }

    public String description() {
        return description;
    }

    public static KeyStoreMode parse(String rawValue) {
        for (KeyStoreMode mode : values()) {
            if (mode.name().equalsIgnoreCase(rawValue) || mode.label.equalsIgnoreCase(rawValue)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Modalita non supportata: " + rawValue);
    }

    @Override
    public String toString() {
        return label;
    }
}
