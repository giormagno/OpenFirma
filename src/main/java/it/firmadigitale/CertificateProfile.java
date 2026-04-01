package it.firmadigitale;

public enum CertificateProfile {
    QUALIFIED_SIGNATURE("Firma qualificata", "Certificato SIG/QES idoneo alla firma CAdES.", true),
    AUTHENTICATION("Autenticazione", "Certificato AUT/CNS o di autenticazione, non idoneo alla firma qualificata.", false),
    GENERIC_SIGNATURE("Firma generica", "Certificato di firma generico, ammesso solo in modalita tecniche.", true),
    UNKNOWN("Non classificato", "Certificato non riconosciuto come idoneo alla firma qualificata.", false);

    private final String label;
    private final String description;
    private final boolean selectable;

    CertificateProfile(String label, String description, boolean selectable) {
        this.label = label;
        this.description = description;
        this.selectable = selectable;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean selectable() {
        return selectable;
    }
}
