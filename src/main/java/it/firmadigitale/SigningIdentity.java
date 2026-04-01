package it.firmadigitale;

public record SigningIdentity(
        String alias,
        String displayName,
        String subject,
        String issuer,
        String thumbprint,
        String keyAlgorithm,
        CertificateProfile profile
) {

    public boolean isQualifiedSignature() {
        return profile == CertificateProfile.QUALIFIED_SIGNATURE;
    }

    public String profileLabel() {
        return profile.label();
    }

    @Override
    public String toString() {
        return displayName + " [" + profile.label() + "] - alias: " + alias;
    }
}
