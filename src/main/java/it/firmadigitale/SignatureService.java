package it.firmadigitale;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAttributeTableGenerationException;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

public final class SignatureService {

    private static final String EKU_CLIENT_AUTH = "1.3.6.1.5.5.7.3.2";
    private static final String ETSI_QC_TYPE = "0.4.0.1862.1.6";
    private static final String ETSI_QCT_ESIGN = "0.4.0.1862.1.6.1";
    private static final Pattern COMMON_NAME_PATTERN = Pattern.compile("(^|,\\s*)CN=([^,]+)");
    private static final Set<String> QUALIFIED_POLICY_OIDS = Set.of(
            "0.4.0.194112.1.2",
            "0.4.0.194112.1.3",
            "0.4.0.194112.1.4",
            "0.4.0.194112.1.5",
            "1.3.76.14.1.1.30",
            "1.3.76.16.6"
    );
    private static final Set<String> AUTHENTICATION_POLICY_OIDS = Set.of(
            "1.3.76.16.2.1",
            "1.3.76.14.1.1.80"
    );

    public List<SigningIdentity> listSigningIdentities(SignatureConfig config) throws Exception {
        LoadedKeyStore loadedKeyStore = loadKeyStore(config);
        IdentityCollection collection = collectSigningIdentities(loadedKeyStore.keyStore(), config.mode());
        if (collection.identities().isEmpty() && config.mode() == KeyStoreMode.WINDOWS_MY) {
            if (collection.authenticationCertificates() > 0) {
                throw new IllegalStateException("Sono stati trovati solo certificati AUT/CNS. Per una firma conforme serve il certificato SIG/QES.");
            }
            throw new IllegalStateException("Nessun certificato SIG/QES disponibile nello store Windows.");
        }
        return collection.identities();
    }

    public List<Path> signFiles(SignatureConfig config, String alias, List<Path> inputFiles, Path outputDirectory) throws Exception {
        if (inputFiles == null || inputFiles.isEmpty()) {
            throw new IllegalArgumentException("Non ci sono file da firmare.");
        }

        Files.createDirectories(outputDirectory);
        LoadedKeyStore loadedKeyStore = loadKeyStore(config);
        String effectiveAlias = resolveAlias(loadedKeyStore.keyStore(), config.mode(), alias);
        PrivateKey privateKey = (PrivateKey) loadedKeyStore.keyStore().getKey(effectiveAlias, loadedKeyStore.keyPassword());
        if (privateKey == null) {
            throw new IllegalStateException("Chiave privata non disponibile per alias " + effectiveAlias);
        }

        Certificate[] chain = loadedKeyStore.keyStore().getCertificateChain(effectiveAlias);
        if (chain == null || chain.length == 0) {
            throw new IllegalStateException("Catena certificati non disponibile per alias " + effectiveAlias);
        }

        X509Certificate signerCertificate = (X509Certificate) chain[0];
        List<X509Certificate> certificateChain = new ArrayList<>();
        for (Certificate certificate : chain) {
            certificateChain.add((X509Certificate) certificate);
        }

        List<Path> results = new ArrayList<>();
        for (Path inputFile : inputFiles) {
            if (!Files.exists(inputFile)) {
                throw new IllegalArgumentException("File non trovato: " + inputFile);
            }
            results.add(signSingleFile(privateKey, loadedKeyStore.signatureProviderName(), signerCertificate, certificateChain, inputFile, outputDirectory));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public boolean verifySignature(Path signedFile) throws Exception {
        CMSSignedData signedData = new CMSSignedData(Files.readAllBytes(signedFile));
        SignerInformationStore signerInformationStore = signedData.getSignerInfos();
        Store<X509CertificateHolder> certificateStore = signedData.getCertificates();

        if (signerInformationStore.getSigners().isEmpty()) {
            return false;
        }

        for (SignerInformation signerInformation : signerInformationStore.getSigners()) {
            Collection<X509CertificateHolder> matches = certificateStore.getMatches(signerInformation.getSID());
            if (matches.isEmpty()) {
                return false;
            }
            X509CertificateHolder signerCertificate = matches.iterator().next();
            if (!signerInformation.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(signerCertificate))) {
                return false;
            }
        }

        return true;
    }

    private Path signSingleFile(
            PrivateKey privateKey,
            String signatureProviderName,
            X509Certificate signerCertificate,
            List<X509Certificate> certificateChain,
            Path inputFile,
            Path outputDirectory
    ) throws Exception {
        JcaContentSignerBuilder contentSignerBuilder = new JcaContentSignerBuilder(resolveSignatureAlgorithm(privateKey));
        if (signatureProviderName != null && !signatureProviderName.isBlank()) {
            contentSignerBuilder.setProvider(signatureProviderName);
        }
        ContentSigner contentSigner = contentSignerBuilder.build(privateKey);

        DigestCalculatorProvider digestCalculatorProvider =
                new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build();

        JcaSignerInfoGeneratorBuilder signerInfoGeneratorBuilder = new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider);
        signerInfoGeneratorBuilder.setSignedAttributeGenerator(new CadesSignedAttributeGenerator(signerCertificate));

        CMSSignedDataGenerator signedDataGenerator = new CMSSignedDataGenerator();
        signedDataGenerator.addSignerInfoGenerator(signerInfoGeneratorBuilder.build(contentSigner, signerCertificate));
        signedDataGenerator.addCertificates(new JcaCertStore(certificateChain));

        byte[] payload = Files.readAllBytes(inputFile);
        CMSTypedData data = new CMSProcessableByteArray(payload);
        CMSSignedData signedData = signedDataGenerator.generate(data, true);

        Path outputFile = outputDirectory.resolve(inputFile.getFileName().toString() + ".p7m");
        Files.write(outputFile, signedData.getEncoded());
        return outputFile;
    }

    private LoadedKeyStore loadKeyStore(SignatureConfig config) throws Exception {
        return switch (config.mode()) {
            case PKCS11 -> loadPkcs11(config);
            case WINDOWS_MY -> loadWindowsMy();
            case PKCS12 -> loadPkcs12(config);
        };
    }

    private LoadedKeyStore loadPkcs11(SignatureConfig config) throws Exception {
        if (config.resourcePath().isBlank()) {
            throw new IllegalArgumentException("La DLL PKCS#11 e obbligatoria.");
        }

        Provider baseProvider = Security.getProvider("SunPKCS11");
        if (baseProvider == null) {
            throw new IllegalStateException("Provider SunPKCS11 non disponibile nel runtime Java.");
        }

        Path tempConfig = Files.createTempFile("pkcs11-", ".cfg");
        try {
            String content = "name=FirmaDigitaleToken" + System.lineSeparator()
                    + "library=" + Path.of(config.resourcePath()).toAbsolutePath() + System.lineSeparator();
            Files.writeString(tempConfig, content, StandardCharsets.UTF_8);

            Provider configuredProvider = baseProvider.configure(tempConfig.toAbsolutePath().toString());
            Provider registeredProvider = Security.getProvider(configuredProvider.getName());
            if (registeredProvider == null) {
                Security.addProvider(configuredProvider);
                registeredProvider = configuredProvider;
            }

            KeyStore keyStore = KeyStore.getInstance("PKCS11", registeredProvider);
            keyStore.load(null, config.secretChars());
            return new LoadedKeyStore(keyStore, registeredProvider.getName(), config.secretChars());
        } finally {
            Files.deleteIfExists(tempConfig);
        }
    }

    private LoadedKeyStore loadWindowsMy() throws Exception {
        Provider provider = Security.getProvider("SunMSCAPI");
        if (provider == null) {
            throw new IllegalStateException("Provider SunMSCAPI non disponibile nel runtime Java.");
        }

        KeyStore keyStore = KeyStore.getInstance("Windows-MY", provider);
        keyStore.load(null, null);
        return new LoadedKeyStore(keyStore, provider.getName(), null);
    }

    private LoadedKeyStore loadPkcs12(SignatureConfig config) throws Exception {
        if (config.resourcePath().isBlank()) {
            throw new IllegalArgumentException("Il percorso del file PKCS#12 e obbligatorio.");
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream inputStream = Files.newInputStream(Path.of(config.resourcePath()))) {
            keyStore.load(inputStream, config.secretChars());
        }
        return new LoadedKeyStore(keyStore, null, config.secretChars());
    }

    private String resolveAlias(KeyStore keyStore, KeyStoreMode mode, String requestedAlias) throws Exception {
        if (requestedAlias != null && !requestedAlias.isBlank()) {
            if (!keyStore.containsAlias(requestedAlias)) {
                throw new IllegalArgumentException("Alias non trovato: " + requestedAlias);
            }
            Certificate certificate = keyStore.getCertificate(requestedAlias);
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new IllegalArgumentException("L'alias " + requestedAlias + " non contiene un certificato X.509 valido.");
            }
            CertificateProfile profile = classifyCertificate(x509Certificate);
            if (!isSelectableForMode(profile, mode)) {
                throw new IllegalArgumentException("L'alias " + requestedAlias + " non punta a un certificato SIG/QES idoneo alla firma conforme.");
            }
            return requestedAlias;
        }

        IdentityCollection collection = collectSigningIdentities(keyStore, mode);
        List<SigningIdentity> usableAliases = collection.identities();

        if (usableAliases.isEmpty()) {
            if (mode == KeyStoreMode.WINDOWS_MY && collection.authenticationCertificates() > 0) {
                throw new IllegalStateException("Sono presenti solo certificati AUT/CNS. Selezionare o installare il certificato SIG/QES.");
            }
            throw new IllegalStateException("Nessun certificato adatto alla firma trovato.");
        }
        if (usableAliases.size() > 1) {
            throw new IllegalArgumentException("Sono presenti piu certificati. Specificare --alias o selezionarlo dalla GUI.");
        }
        return usableAliases.get(0).alias();
    }

    private IdentityCollection collectSigningIdentities(KeyStore keyStore, KeyStoreMode mode) throws Exception {
        List<SigningIdentity> identities = new ArrayList<>();
        int authenticationCertificates = 0;

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }

            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                continue;
            }

            CertificateProfile profile = classifyCertificate(x509Certificate);
            if (profile == CertificateProfile.AUTHENTICATION) {
                authenticationCertificates++;
            }

            if (!isSelectableForMode(profile, mode)) {
                continue;
            }

            identities.add(toSigningIdentity(alias, x509Certificate, profile));
        }

        identities.sort(Comparator.comparing(SigningIdentity::displayName, String.CASE_INSENSITIVE_ORDER));
        return new IdentityCollection(List.copyOf(identities), authenticationCertificates);
    }

    private SigningIdentity toSigningIdentity(String alias, X509Certificate certificate, CertificateProfile profile) throws Exception {
        return new SigningIdentity(
                alias,
                extractDisplayName(certificate),
                certificate.getSubjectX500Principal().getName(),
                certificate.getIssuerX500Principal().getName(),
                computeThumbprint(certificate),
                certificate.getPublicKey().getAlgorithm(),
                profile
        );
    }

    private boolean isSelectableForMode(CertificateProfile profile, KeyStoreMode mode) {
        return switch (mode) {
            case WINDOWS_MY, PKCS11 -> profile == CertificateProfile.QUALIFIED_SIGNATURE;
            case PKCS12 -> profile == CertificateProfile.QUALIFIED_SIGNATURE || profile == CertificateProfile.GENERIC_SIGNATURE;
        };
    }

    private CertificateProfile classifyCertificate(X509Certificate certificate) {
        boolean[] keyUsage = certificate.getKeyUsage();
        boolean digitalSignature = keyUsage == null || (keyUsage.length > 0 && keyUsage[0]);
        boolean nonRepudiation = keyUsage != null && keyUsage.length > 1 && keyUsage[1];

        Set<String> extendedKeyUsages = extractExtendedKeyUsages(certificate);
        Set<String> policyOids = extractCertificatePolicies(certificate);
        Set<String> qcStatementOids = extractQcStatementOids(certificate);

        String subject = certificate.getSubjectX500Principal().getName().toUpperCase(Locale.ROOT);
        String issuer = certificate.getIssuerX500Principal().getName().toUpperCase(Locale.ROOT);

        boolean hasQcStatements = certificate.getExtensionValue(Extension.qCStatements.getId()) != null;
        boolean hasQualifiedPolicy = intersects(policyOids, QUALIFIED_POLICY_OIDS);
        boolean hasAuthenticationPolicy = intersects(policyOids, AUTHENTICATION_POLICY_OIDS);
        boolean hasClientAuth = extendedKeyUsages.contains(EKU_CLIENT_AUTH);
        boolean hasQcEsign = qcStatementOids.contains(ETSI_QCT_ESIGN);
        boolean looksQualifiedBySubject = subject.contains("DNQUALIFIER=SIG") || subject.contains("SERIALNUMBER=TINIT-");
        boolean looksAuthenticationBySubject = subject.contains("DNQUALIFIER=AUT");
        boolean looksQualifiedByIssuer = issuer.contains("QUALIFIED ELECTRONIC SIGNATURE CA");
        boolean looksAuthenticationByIssuer = issuer.contains("SERVIZI DI CERTIFICAZIONE CA");

        if ((nonRepudiation || hasQcEsign)
                && !hasClientAuth
                && (hasQcEsign || hasQcStatements || hasQualifiedPolicy || looksQualifiedBySubject || looksQualifiedByIssuer)) {
            return CertificateProfile.QUALIFIED_SIGNATURE;
        }

        if (hasClientAuth || hasAuthenticationPolicy || looksAuthenticationBySubject || looksAuthenticationByIssuer) {
            return CertificateProfile.AUTHENTICATION;
        }

        if (digitalSignature || nonRepudiation) {
            return CertificateProfile.GENERIC_SIGNATURE;
        }

        return CertificateProfile.UNKNOWN;
    }

    private String resolveSignatureAlgorithm(PrivateKey privateKey) {
        return switch (privateKey.getAlgorithm().toUpperCase()) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            case "DSA" -> "SHA256withDSA";
            default -> throw new IllegalArgumentException("Algoritmo chiave non supportato: " + privateKey.getAlgorithm());
        };
    }

    private Set<String> extractExtendedKeyUsages(X509Certificate certificate) {
        try {
            List<String> usages = certificate.getExtendedKeyUsage();
            return usages == null ? Set.of() : new HashSet<>(usages);
        } catch (CertificateParsingException ex) {
            return Set.of();
        }
    }

    private Set<String> extractCertificatePolicies(X509Certificate certificate) {
        try {
            ASN1Primitive extensionValue = readExtensionValue(certificate, Extension.certificatePolicies.getId());
            if (extensionValue == null) {
                return Set.of();
            }

            Set<String> policies = new HashSet<>();
            ASN1Sequence policySequence = ASN1Sequence.getInstance(extensionValue);
            Enumeration<?> objects = policySequence.getObjects();
            while (objects.hasMoreElements()) {
                PolicyInformation policyInformation = PolicyInformation.getInstance(objects.nextElement());
                policies.add(policyInformation.getPolicyIdentifier().getId());
            }
            return policies;
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private Set<String> extractQcStatementOids(X509Certificate certificate) {
        try {
            ASN1Primitive extensionValue = readExtensionValue(certificate, Extension.qCStatements.getId());
            if (extensionValue == null) {
                return Set.of();
            }

            Set<String> qcOids = new HashSet<>();
            ASN1Sequence statementSequence = ASN1Sequence.getInstance(extensionValue);
            Enumeration<?> statements = statementSequence.getObjects();
            while (statements.hasMoreElements()) {
                ASN1Sequence statement = ASN1Sequence.getInstance(statements.nextElement());
                ASN1ObjectIdentifier statementId = ASN1ObjectIdentifier.getInstance(statement.getObjectAt(0));
                qcOids.add(statementId.getId());

                if (ETSI_QC_TYPE.equals(statementId.getId()) && statement.size() > 1) {
                    ASN1Sequence types = ASN1Sequence.getInstance(statement.getObjectAt(1));
                    Enumeration<?> typeObjects = types.getObjects();
                    while (typeObjects.hasMoreElements()) {
                        qcOids.add(ASN1ObjectIdentifier.getInstance(typeObjects.nextElement()).getId());
                    }
                }
            }

            return qcOids;
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private ASN1Primitive readExtensionValue(X509Certificate certificate, String extensionOid) throws Exception {
        byte[] extension = certificate.getExtensionValue(extensionOid);
        if (extension == null) {
            return null;
        }

        try (ASN1InputStream firstStream = new ASN1InputStream(extension)) {
            ASN1OctetString octetString = ASN1OctetString.getInstance(firstStream.readObject());
            try (ASN1InputStream secondStream = new ASN1InputStream(octetString.getOctets())) {
                return secondStream.readObject();
            }
        }
    }

    private boolean intersects(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String extractDisplayName(X509Certificate certificate) {
        String subject = certificate.getSubjectX500Principal().getName();
        Matcher matcher = COMMON_NAME_PATTERN.matcher(subject);
        if (matcher.find()) {
            return matcher.group(2).trim().replace("\"", "");
        }
        return subject;
    }

    private String computeThumbprint(X509Certificate certificate) throws Exception {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(certificate.getEncoded());
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (CertificateEncodingException ex) {
            throw new IllegalStateException("Impossibile calcolare il thumbprint del certificato.", ex);
        }
    }

    private record LoadedKeyStore(KeyStore keyStore, String signatureProviderName, char[] keyPassword) {
    }

    private record IdentityCollection(List<SigningIdentity> identities, int authenticationCertificates) {
    }

    private static final class CadesSignedAttributeGenerator implements CMSAttributeTableGenerator {
        private final DefaultSignedAttributeTableGenerator delegate = new DefaultSignedAttributeTableGenerator();
        private final SigningCertificateV2 signingCertificateV2;

        private CadesSignedAttributeGenerator(X509Certificate signerCertificate) throws Exception {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] certificateHash = messageDigest.digest(signerCertificate.getEncoded());
            ESSCertIDv2 certId = new ESSCertIDv2(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256), certificateHash);
            this.signingCertificateV2 = new SigningCertificateV2(certId);
        }

        @Override
        public AttributeTable getAttributes(Map parameters) throws CMSAttributeTableGenerationException {
            AttributeTable attributeTable = delegate.getAttributes(parameters);
            return attributeTable.add(PKCSObjectIdentifiers.id_aa_signingCertificateV2, signingCertificateV2);
        }
    }
}
