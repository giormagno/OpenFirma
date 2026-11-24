package it.firmadigitale;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Calendar;
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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.util.Matrix;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.Time;
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
        LoadedSigningMaterial signingMaterial = loadSigningMaterial(config, alias);

        List<Path> results = new ArrayList<>();
        for (Path inputFile : inputFiles) {
            if (!Files.exists(inputFile)) {
                throw new IllegalArgumentException("File non trovato: " + inputFile);
            }
            Path outputFile = outputDirectory.resolve(inputFile.getFileName().toString() + ".p7m");
            results.add(signSingleFile(
                    signingMaterial.privateKey(),
                    signingMaterial.signatureProviderName(),
                    signingMaterial.signerCertificate(),
                    signingMaterial.certificateChain(),
                    inputFile,
                    outputFile
            ));
        }
        return results;
    }

    public Path signFile(SignatureConfig config, String alias, Path inputFile, Path outputFile) throws Exception {
        if (inputFile == null || !Files.exists(inputFile)) {
            throw new IllegalArgumentException("File non trovato: " + inputFile);
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Il file di output non e valido.");
        }

        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        LoadedSigningMaterial signingMaterial = loadSigningMaterial(config, alias);
        boolean replaceExistingOutput = Files.exists(outputFile);
        Path writeTarget = replaceExistingOutput ? createSiblingTempFile(outputFile) : outputFile;
        try {
            Path signedOutput = signSingleFile(
                    signingMaterial.privateKey(),
                    signingMaterial.signatureProviderName(),
                    signingMaterial.signerCertificate(),
                    signingMaterial.certificateChain(),
                    inputFile,
                    writeTarget
            );
            if (replaceExistingOutput) {
                replaceFile(writeTarget, outputFile);
            }
            return replaceExistingOutput ? outputFile : signedOutput;
        } catch (Exception ex) {
            deleteTempFileQuietly(writeTarget, outputFile);
            throw ex;
        }
    }

    public Path signPdfFile(
            SignatureConfig config,
            String alias,
            Path inputFile,
            Path outputFile,
            PdfVisibleSignaturePlacement placement,
            String signerDisplayName,
            boolean applyToEveryPage
    ) throws Exception {
        List<PdfVisibleSignaturePlacement> placements;
        if (applyToEveryPage) {
            placements = expandPlacementToEveryPage(inputFile, placement);
        } else {
            placements = List.of(placement);
        }
        return signPdfFile(config, alias, inputFile, outputFile, placements, signerDisplayName);
    }

    public Path signPdfFile(
            SignatureConfig config,
            String alias,
            Path inputFile,
            Path outputFile,
            List<PdfVisibleSignaturePlacement> placements,
            String signerDisplayName
    ) throws Exception {
        if (inputFile == null || !Files.exists(inputFile)) {
            throw new IllegalArgumentException("File PDF non trovato: " + inputFile);
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Il file PDF di output non e valido.");
        }
        if (placements == null || placements.isEmpty()) {
            throw new IllegalArgumentException("Definire almeno una posizione visibile per la firma PDF.");
        }

        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        LoadedSigningMaterial signingMaterial = loadSigningMaterial(config, alias);
        SignatureOptions signatureOptions = null;
        boolean replaceExistingOutput = Files.exists(outputFile);
        boolean sameInputAndOutput = inputFile.toAbsolutePath().normalize().equals(outputFile.toAbsolutePath().normalize());
        Path writeTarget = (replaceExistingOutput || sameInputAndOutput) ? createSiblingTempFile(outputFile) : outputFile;

        try (PDDocument document = Loader.loadPDF(inputFile.toFile());
             OutputStream outputStream = Files.newOutputStream(writeTarget)) {
            if (document.getNumberOfPages() == 0) {
                throw new IllegalArgumentException("Il PDF selezionato non contiene pagine.");
            }

            List<PdfVisibleSignaturePlacement> normalizedPlacements = validateAndNormalizePdfPlacements(document, placements);

            String resolvedSignerName = resolvePdfSignerName(signingMaterial.signerCertificate(), signerDisplayName);
            Calendar signingCalendar = Calendar.getInstance();

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            signature.setName(resolvedSignerName);
            signature.setLocation("OpenFirma");
            signature.setReason("Firma PDF");
            signature.setSignDate(signingCalendar);

            if (normalizedPlacements.size() == 1) {
                PdfVisibleSignaturePlacement placement = normalizedPlacements.get(0);
                signatureOptions = new SignatureOptions();
                signatureOptions.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);
                signatureOptions.setPage(placement.pageIndex());
                signatureOptions.setVisualSignature(createPdfVisibleSignatureTemplate(
                        document,
                        placement,
                        signature,
                        resolvedSignerName
                ));
                document.addSignature(signature, signatureOptions);
            } else {
                for (PdfVisibleSignaturePlacement placement : normalizedPlacements) {
                    stampVisibleSignatureOnPage(document, placement, resolvedSignerName, signingCalendar);
                }
                document.addSignature(signature);
            }

            ExternalSigningSupport externalSigning = document.saveIncrementalForExternalSigning(outputStream);
            byte[] cmsSignature = signDetachedPdfContent(signingMaterial, externalSigning.getContent());
            externalSigning.setSignature(cmsSignature);
        } catch (Exception ex) {
            deleteTempFileQuietly(writeTarget, outputFile);
            throw ex;
        } finally {
            if (signatureOptions != null) {
                try {
                    signatureOptions.close();
                } catch (Exception ignored) {
                    // SignatureOptions puo gia essere stato chiuso dal runtime PDFBox.
                }
            }
        }

        if (!writeTarget.equals(outputFile)) {
            try {
                replaceFile(writeTarget, outputFile);
            } catch (IOException ex) {
                deleteTempFileQuietly(writeTarget, outputFile);
                throw ex;
            }
        }
        return outputFile;
    }

    private List<PdfVisibleSignaturePlacement> expandPlacementToEveryPage(
            Path inputFile,
            PdfVisibleSignaturePlacement placement
    ) throws IOException {
        if (placement == null) {
            throw new IllegalArgumentException("La posizione della firma PDF non e stata definita.");
        }

        try (PDDocument document = Loader.loadPDF(inputFile.toFile())) {
            if (document.getNumberOfPages() == 0) {
                throw new IllegalArgumentException("Il PDF selezionato non contiene pagine.");
            }
            if (placement.pageIndex() < 0 || placement.pageIndex() >= document.getNumberOfPages()) {
                throw new IllegalArgumentException("La pagina selezionata per la firma PDF non e valida.");
            }

            List<PdfVisibleSignaturePlacement> placements = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                placements.add(remapPdfPlacement(document, placement, pageIndex));
            }
            return List.copyOf(placements);
        }
    }

    private List<PdfVisibleSignaturePlacement> validateAndNormalizePdfPlacements(
            PDDocument document,
            List<PdfVisibleSignaturePlacement> placements
    ) {
        List<PdfVisibleSignaturePlacement> normalizedPlacements = new ArrayList<>();
        for (PdfVisibleSignaturePlacement placement : placements) {
            if (placement == null) {
                throw new IllegalArgumentException("La lista delle posizioni PDF contiene un elemento non valido.");
            }
            if (placement.pageIndex() < 0 || placement.pageIndex() >= document.getNumberOfPages()) {
                throw new IllegalArgumentException("Una delle pagine selezionate per la firma PDF non e valida.");
            }
            normalizedPlacements.add(normalizePdfPlacement(document, placement));
        }

        normalizedPlacements.sort(Comparator.comparingInt(PdfVisibleSignaturePlacement::pageIndex));
        return List.copyOf(normalizedPlacements);
    }

    private PdfVisibleSignaturePlacement normalizePdfPlacement(
            PDDocument document,
            PdfVisibleSignaturePlacement placement
    ) {
        PDRectangle pageBox = document.getPage(placement.pageIndex()).getCropBox();
        float maxWidth = Math.max(72f, pageBox.getWidth() - 56f);
        float maxHeight = Math.max(36f, pageBox.getHeight() - 56f);
        float width = Math.max(72f, Math.min(maxWidth, placement.width()));
        float height = Math.max(36f, Math.min(maxHeight, placement.height()));
        float x = Math.max(28f, Math.min(pageBox.getWidth() - width - 28f, placement.x()));
        float y = Math.max(28f, Math.min(pageBox.getHeight() - height - 28f, placement.y()));
        return new PdfVisibleSignaturePlacement(placement.pageIndex(), x, y, width, height);
    }

    @SuppressWarnings("unchecked")
    public boolean verifySignature(Path signedFile) throws Exception {
        if (isPdfFile(signedFile)) {
            return verifyPdfSignature(signedFile);
        }

        CMSSignedData signedData = new CMSSignedData(Files.readAllBytes(signedFile));
        return verifyCmsSignedData(signedData);
    }

    @SuppressWarnings("unchecked")
    public SignedFileInspection inspectSignedFile(Path signedFile) throws Exception {
        if (!Files.exists(signedFile)) {
            throw new IllegalArgumentException("File non trovato: " + signedFile);
        }

        if (isPdfFile(signedFile)) {
            return inspectPdfSignedFile(signedFile);
        }

        BasicFileAttributes attributes = Files.readAttributes(signedFile, BasicFileAttributes.class);
        CMSSignedData signedData = new CMSSignedData(Files.readAllBytes(signedFile));
        SignerInformationStore signerInformationStore = signedData.getSignerInfos();
        Store<X509CertificateHolder> certificateStore = signedData.getCertificates();
        byte[] embeddedContent = extractEmbeddedContent(signedData);
        SignedContentDescriptor contentDescriptor = describeEmbeddedContent(embeddedContent);

        List<SignedFileSigner> signers = new ArrayList<>();
        for (SignerInformation signerInformation : signerInformationStore.getSigners()) {
            Collection<X509CertificateHolder> matches = certificateStore.getMatches(signerInformation.getSID());
            String signerName = "Firmatario non disponibile";
            if (!matches.isEmpty()) {
                signerName = extractDisplayName(matches.iterator().next().getSubject().toString());
            }
            signers.add(new SignedFileSigner(signerName, extractSigningTime(signerInformation)));
        }

        return new SignedFileInspection(
                signedFile.getFileName().toString(),
                signedFile.toAbsolutePath(),
                attributes.creationTime().toInstant(),
                List.copyOf(signers),
                embeddedContent,
                contentDescriptor.label(),
                contentDescriptor.textPreviewAvailable(),
                contentDescriptor.suggestedExtension()
        );
    }

    private Path signSingleFile(
            PrivateKey privateKey,
            String signatureProviderName,
            X509Certificate signerCertificate,
            List<X509Certificate> certificateChain,
            Path inputFile,
            Path outputFile
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

        Files.write(outputFile, signedData.getEncoded());
        return outputFile;
    }

    private Path createSiblingTempFile(Path outputFile) throws IOException {
        Path absoluteOutput = outputFile.toAbsolutePath();
        Path parent = absoluteOutput.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }

        String fileName = absoluteOutput.getFileName().toString();
        String prefix = stripTempFileExtension(fileName);
        if (prefix.length() < 3) {
            prefix = (prefix + "___").substring(0, 3);
        }
        return Files.createTempFile(parent, prefix + "-", ".tmp");
    }

    private String stripTempFileExtension(String fileName) {
        int separatorIndex = fileName.lastIndexOf('.');
        if (separatorIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, separatorIndex);
    }

    private void replaceFile(Path sourceFile, Path targetFile) throws IOException {
        try {
            Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTempFileQuietly(Path candidate, Path finalOutput) {
        if (candidate == null || candidate.equals(finalOutput)) {
            return;
        }
        try {
            Files.deleteIfExists(candidate);
        } catch (IOException ignored) {
            // Mantiene l'errore originale della firma come causa principale.
        }
    }

    private byte[] signDetachedPdfContent(LoadedSigningMaterial signingMaterial, InputStream contentStream) throws Exception {
        JcaContentSignerBuilder contentSignerBuilder =
                new JcaContentSignerBuilder(resolveSignatureAlgorithm(signingMaterial.privateKey()));
        if (signingMaterial.signatureProviderName() != null && !signingMaterial.signatureProviderName().isBlank()) {
            contentSignerBuilder.setProvider(signingMaterial.signatureProviderName());
        }
        ContentSigner contentSigner = contentSignerBuilder.build(signingMaterial.privateKey());

        DigestCalculatorProvider digestCalculatorProvider =
                new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build();

        JcaSignerInfoGeneratorBuilder signerInfoGeneratorBuilder = new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider);
        signerInfoGeneratorBuilder.setSignedAttributeGenerator(
                new CadesSignedAttributeGenerator(signingMaterial.signerCertificate()));

        CMSSignedDataGenerator signedDataGenerator = new CMSSignedDataGenerator();
        signedDataGenerator.addSignerInfoGenerator(
                signerInfoGeneratorBuilder.build(contentSigner, signingMaterial.signerCertificate()));
        signedDataGenerator.addCertificates(new JcaCertStore(signingMaterial.certificateChain()));

        CMSTypedData data = new CMSProcessableInputStream(contentStream);
        CMSSignedData signedData = signedDataGenerator.generate(data, false);
        return signedData.getEncoded();
    }

    private void stampVisibleSignatureOnEveryPage(
            PDDocument document,
            PdfVisibleSignaturePlacement placement,
            String signerDisplayName,
            Calendar signingCalendar
    ) throws IOException {
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PdfVisibleSignaturePlacement pagePlacement = remapPdfPlacement(document, placement, pageIndex);
            stampVisibleSignatureOnPage(document, pagePlacement, signerDisplayName, signingCalendar);
        }
    }

    private PdfVisibleSignaturePlacement remapPdfPlacement(
            PDDocument document,
            PdfVisibleSignaturePlacement placement,
            int targetPageIndex
    ) {
        PDRectangle sourcePageBox = document.getPage(placement.pageIndex()).getCropBox();
        PDRectangle targetPageBox = document.getPage(targetPageIndex).getCropBox();

        float widthRatio = placement.width() / sourcePageBox.getWidth();
        float heightRatio = placement.height() / sourcePageBox.getHeight();
        float xRatio = placement.x() / sourcePageBox.getWidth();
        float yRatio = placement.y() / sourcePageBox.getHeight();

        float width = Math.max(72f, Math.min(targetPageBox.getWidth() - 56f, targetPageBox.getWidth() * widthRatio));
        float height = Math.max(36f, Math.min(targetPageBox.getHeight() - 56f, targetPageBox.getHeight() * heightRatio));
        float x = Math.max(28f, Math.min(targetPageBox.getWidth() - width - 28f, targetPageBox.getWidth() * xRatio));
        float y = Math.max(28f, Math.min(targetPageBox.getHeight() - height - 28f, targetPageBox.getHeight() * yRatio));
        return new PdfVisibleSignaturePlacement(targetPageIndex, x, y, width, height);
    }

    private void stampVisibleSignatureOnPage(
            PDDocument document,
            PdfVisibleSignaturePlacement placement,
            String signerDisplayName,
            Calendar signingCalendar
    ) throws IOException {
        PDPage page = document.getPage(placement.pageIndex());
        PDRectangle signatureRectangle = createPdfSignatureRectangle(document, placement);

        try (PDPageContentStream contentStream = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
        )) {
            contentStream.saveGraphicsState();
            AffineTransform transform = new AffineTransform();
            switch (page.getRotation()) {
                case 90 -> {
                    transform.translate(signatureRectangle.getLowerLeftX(), signatureRectangle.getUpperRightY());
                    transform.rotate(-Math.PI / 2d);
                }
                case 180 -> {
                    transform.translate(signatureRectangle.getUpperRightX(), signatureRectangle.getUpperRightY());
                    transform.rotate(Math.PI);
                }
                case 270 -> {
                    transform.translate(signatureRectangle.getUpperRightX(), signatureRectangle.getLowerLeftY());
                    transform.rotate(Math.PI / 2d);
                }
                default -> transform.translate(signatureRectangle.getLowerLeftX(), signatureRectangle.getLowerLeftY());
            }
            contentStream.transform(new Matrix(transform));
            drawPdfSignatureAppearance(contentStream, placement.width(), placement.height(), signerDisplayName, signingCalendar);
            contentStream.restoreGraphicsState();
        }
    }

    private void drawPdfSignatureAppearance(
            PDPageContentStream contentStream,
            float width,
            float height,
            String signerDisplayName,
            Calendar signingCalendar
    ) throws IOException {
        PDFont titleFont = new PDType1Font(FontName.HELVETICA_BOLD);
        PDFont bodyFont = new PDType1Font(FontName.HELVETICA);
        String signerLine = abbreviateSignatureText(signerDisplayName, 42);
        String signedAtLine = "Firmato il " + signingCalendar.getTime();

        contentStream.setNonStrokingColor(new Color(247, 250, 250));
        contentStream.addRect(0, 0, width, height);
        contentStream.fill();

        contentStream.setNonStrokingColor(new Color(0, 102, 108));
        contentStream.addRect(0, 0, Math.min(8, width * 0.08f), height);
        contentStream.fill();

        contentStream.setStrokingColor(new Color(207, 212, 214));
        contentStream.setLineWidth(1f);
        contentStream.addRect(0.5f, 0.5f, width - 1f, height - 1f);
        contentStream.stroke();

        float paddingLeft = Math.min(18f, width * 0.14f);
        float titleY = height - 18f;

        contentStream.beginText();
        contentStream.setFont(titleFont, Math.max(11f, Math.min(14f, width / 13f)));
        contentStream.setNonStrokingColor(new Color(0, 65, 69));
        contentStream.newLineAtOffset(paddingLeft, titleY);
        contentStream.showText("OpenFirma");
        contentStream.endText();

        contentStream.beginText();
        contentStream.setFont(bodyFont, Math.max(8f, Math.min(10f, width / 16f)));
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.newLineAtOffset(paddingLeft, titleY - 16f);
        contentStream.showText(signerLine);
        contentStream.endText();

        contentStream.beginText();
        contentStream.setFont(bodyFont, Math.max(7f, Math.min(9f, width / 18f)));
        contentStream.setNonStrokingColor(new Color(84, 88, 89));
        contentStream.newLineAtOffset(paddingLeft, Math.max(10f, titleY - 30f));
        contentStream.showText(abbreviateSignatureText(signedAtLine, 48));
        contentStream.endText();
    }

    private InputStream createPdfVisibleSignatureTemplate(
            PDDocument sourceDocument,
            PdfVisibleSignaturePlacement placement,
            PDSignature signature,
            String signerDisplayName
    ) throws Exception {
        PDRectangle signatureRectangle = createPdfSignatureRectangle(sourceDocument, placement);

        try (PDDocument appearanceDocument = new PDDocument()) {
            PDPage templatePage = new PDPage(sourceDocument.getPage(placement.pageIndex()).getMediaBox());
            appearanceDocument.addPage(templatePage);

            PDAcroForm acroForm = new PDAcroForm(appearanceDocument);
            appearanceDocument.getDocumentCatalog().setAcroForm(acroForm);
            acroForm.setSignaturesExist(true);
            acroForm.setAppendOnly(true);
            acroForm.getCOSObject().setDirect(true);

            PDSignatureField signatureField = new PDSignatureField(acroForm);
            acroForm.getFields().add(signatureField);

            PDAnnotationWidget widget = signatureField.getWidgets().get(0);
            widget.setRectangle(signatureRectangle);

            PDStream stream = new PDStream(appearanceDocument);
            PDFormXObject form = new PDFormXObject(stream);
            form.setResources(new PDResources());
            form.setFormType(1);

            PDRectangle box = new PDRectangle(signatureRectangle.getWidth(), signatureRectangle.getHeight());
            float appearanceHeight = box.getHeight();
            Matrix initialScale = null;
            int rotation = sourceDocument.getPage(placement.pageIndex()).getRotation();

            switch (rotation) {
                case 90 -> {
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(1));
                    initialScale = Matrix.getScaleInstance(box.getWidth() / box.getHeight(), box.getHeight() / box.getWidth());
                    appearanceHeight = box.getWidth();
                }
                case 180 -> form.setMatrix(AffineTransform.getQuadrantRotateInstance(2));
                case 270 -> {
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(3));
                    initialScale = Matrix.getScaleInstance(box.getWidth() / box.getHeight(), box.getHeight() / box.getWidth());
                    appearanceHeight = box.getWidth();
                }
                default -> {
                }
            }

            form.setBBox(box);

            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.getCOSObject().setDirect(true);
            PDAppearanceStream appearanceStream = new PDAppearanceStream(form.getCOSObject());
            appearance.setNormalAppearance(appearanceStream);
            widget.setAppearance(appearance);

            try (PDPageContentStream contentStream = new PDPageContentStream(appearanceDocument, appearanceStream)) {
                if (initialScale != null) {
                    contentStream.transform(initialScale);
                }

                drawPdfSignatureAppearance(
                        contentStream,
                        box.getWidth(),
                        appearanceHeight,
                        signerDisplayName,
                        signature.getSignDate()
                );
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            appearanceDocument.save(output);
            return new ByteArrayInputStream(output.toByteArray());
        }
    }

    private PDRectangle createPdfSignatureRectangle(PDDocument document, PdfVisibleSignaturePlacement placement) {
        float x = placement.x();
        float y = placement.y();
        float width = placement.width();
        float height = placement.height();

        PDPage page = document.getPage(placement.pageIndex());
        PDRectangle pageBox = page.getCropBox();
        PDRectangle rectangle = new PDRectangle();

        switch (page.getRotation()) {
            case 90 -> {
                rectangle.setLowerLeftY(x);
                rectangle.setUpperRightY(x + width);
                rectangle.setLowerLeftX(y);
                rectangle.setUpperRightX(y + height);
            }
            case 180 -> {
                rectangle.setUpperRightX(pageBox.getWidth() - x);
                rectangle.setLowerLeftX(pageBox.getWidth() - x - width);
                rectangle.setLowerLeftY(y);
                rectangle.setUpperRightY(y + height);
            }
            case 270 -> {
                rectangle.setLowerLeftY(pageBox.getHeight() - x - width);
                rectangle.setUpperRightY(pageBox.getHeight() - x);
                rectangle.setLowerLeftX(pageBox.getWidth() - y - height);
                rectangle.setUpperRightX(pageBox.getWidth() - y);
            }
            default -> {
                rectangle.setLowerLeftX(x);
                rectangle.setUpperRightX(x + width);
                rectangle.setLowerLeftY(pageBox.getHeight() - y - height);
                rectangle.setUpperRightY(pageBox.getHeight() - y);
            }
        }
        return rectangle;
    }

    private String resolvePdfSignerName(X509Certificate certificate, String signerDisplayName) {
        if (signerDisplayName != null && !signerDisplayName.isBlank()) {
            return signerDisplayName;
        }
        return extractDisplayName(certificate.getSubjectX500Principal().getName());
    }

    private String abbreviateSignatureText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    @SuppressWarnings("unchecked")
    private boolean verifyPdfSignature(Path signedFile) throws Exception {
        byte[] pdfBytes = Files.readAllBytes(signedFile);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            List<PDSignature> signatures = document.getSignatureDictionaries();
            if (signatures.isEmpty()) {
                return false;
            }

            for (PDSignature signature : signatures) {
                CMSSignedData signedData = extractPdfSignedData(signature, pdfBytes);
                if (!verifyCmsSignedData(signedData)) {
                    return false;
                }
            }
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private SignedFileInspection inspectPdfSignedFile(Path signedFile) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(signedFile, BasicFileAttributes.class);
        byte[] pdfBytes = Files.readAllBytes(signedFile);
        List<SignedFileSigner> signers = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            List<PDSignature> signatures = document.getSignatureDictionaries();
            if (signatures.isEmpty()) {
                throw new IllegalStateException("Il PDF selezionato non contiene firme digitali.");
            }

            for (PDSignature signature : signatures) {
                CMSSignedData signedData = extractPdfSignedData(signature, pdfBytes);
                SignerInformationStore signerInformationStore = signedData.getSignerInfos();
                Store<X509CertificateHolder> certificateStore = signedData.getCertificates();
                Instant signatureInstant = signature.getSignDate() == null
                        ? null
                        : signature.getSignDate().toInstant();

                for (SignerInformation signerInformation : signerInformationStore.getSigners()) {
                    Collection<X509CertificateHolder> matches = certificateStore.getMatches(signerInformation.getSID());
                    String signerName = signature.getName();
                    if (matches.isEmpty()) {
                        signerName = (signerName == null || signerName.isBlank())
                                ? "Firmatario non disponibile"
                                : signerName;
                    } else {
                        signerName = extractDisplayName(matches.iterator().next().getSubject().toString());
                    }

                    Instant signingTime = extractSigningTime(signerInformation);
                    signers.add(new SignedFileSigner(signerName, signingTime != null ? signingTime : signatureInstant));
                }
            }
        }

        return new SignedFileInspection(
                signedFile.getFileName().toString(),
                signedFile.toAbsolutePath(),
                attributes.creationTime().toInstant(),
                List.copyOf(signers),
                pdfBytes,
                "PDF",
                false,
                ".pdf"
        );
    }

    private CMSSignedData extractPdfSignedData(PDSignature signature, byte[] pdfBytes) throws Exception {
        byte[] signedContent = signature.getSignedContent(pdfBytes);
        byte[] signatureContent = signature.getContents(pdfBytes);
        return new CMSSignedData(new CMSProcessableByteArray(signedContent), signatureContent);
    }

    @SuppressWarnings("unchecked")
    private boolean verifyCmsSignedData(CMSSignedData signedData) throws Exception {
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
            if (!signerInformation.verify(
                    new JcaSimpleSignerInfoVerifierBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build(signerCertificate))) {
                return false;
            }
        }

        return true;
    }

    private boolean isPdfFile(Path file) throws Exception {
        String lowerCaseName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".pdf")) {
            return true;
        }

        if (!Files.exists(file) || Files.size(file) < 4) {
            return false;
        }

        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] header = inputStream.readNBytes(4);
            return header.length == 4
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F';
        }
    }

    private LoadedSigningMaterial loadSigningMaterial(SignatureConfig config, String alias) throws Exception {
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

        return new LoadedSigningMaterial(privateKey, loadedKeyStore.signatureProviderName(), signerCertificate, List.copyOf(certificateChain));
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
        return extractDisplayName(certificate.getSubjectX500Principal().getName());
    }

    private String extractDisplayName(String subject) {
        Matcher matcher = COMMON_NAME_PATTERN.matcher(subject);
        if (matcher.find()) {
            return matcher.group(2).trim().replace("\"", "");
        }
        return subject;
    }

    private Instant extractSigningTime(SignerInformation signerInformation) {
        try {
            AttributeTable signedAttributes = signerInformation.getSignedAttributes();
            if (signedAttributes == null) {
                return null;
            }

            Attribute signingTime = signedAttributes.get(PKCSObjectIdentifiers.pkcs_9_at_signingTime);
            if (signingTime == null || signingTime.getAttrValues().size() == 0) {
                return null;
            }

            Time time = Time.getInstance(signingTime.getAttrValues().getObjectAt(0));
            return time.getDate().toInstant();
        } catch (Exception ex) {
            return null;
        }
    }

    private byte[] extractEmbeddedContent(CMSSignedData signedData) throws Exception {
        CMSTypedData signedContent = signedData.getSignedContent();
        if (signedContent == null) {
            return new byte[0];
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        signedContent.write(outputStream);
        return outputStream.toByteArray();
    }

    private SignedContentDescriptor describeEmbeddedContent(byte[] embeddedContent) {
        if (embeddedContent == null || embeddedContent.length == 0) {
            return new SignedContentDescriptor("Nessun contenuto incorporato", false, ".bin");
        }

        if (startsWith(embeddedContent, "%PDF-")) {
            return new SignedContentDescriptor("PDF", false, ".pdf");
        }

        String textProbe = new String(embeddedContent, 0, Math.min(embeddedContent.length, 512), StandardCharsets.UTF_8).trim();
        if (textProbe.startsWith("<?xml") || textProbe.startsWith("<")) {
            return new SignedContentDescriptor("XML", true, ".xml");
        }
        if (textProbe.startsWith("{") || textProbe.startsWith("[")) {
            return new SignedContentDescriptor("JSON", true, ".json");
        }
        if (isProbablyTextContent(embeddedContent)) {
            return new SignedContentDescriptor("Testo", true, ".txt");
        }

        return new SignedContentDescriptor("Binario", false, ".bin");
    }

    private boolean isProbablyTextContent(byte[] embeddedContent) {
        if (embeddedContent == null || embeddedContent.length == 0) {
            return false;
        }

        int sampleSize = Math.min(embeddedContent.length, 2048);
        int printable = 0;
        for (int index = 0; index < sampleSize; index++) {
            int value = embeddedContent[index] & 0xFF;
            if (value == 9 || value == 10 || value == 13 || (value >= 32 && value < 127)) {
                printable++;
            }
        }
        return printable >= (int) (sampleSize * 0.82);
    }

    private boolean startsWith(byte[] content, String prefix) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        if (content.length < prefixBytes.length) {
            return false;
        }
        for (int index = 0; index < prefixBytes.length; index++) {
            if (content[index] != prefixBytes[index]) {
                return false;
            }
        }
        return true;
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

    private record LoadedSigningMaterial(
            PrivateKey privateKey,
            String signatureProviderName,
            X509Certificate signerCertificate,
            List<X509Certificate> certificateChain
    ) {
    }

    public record PdfVisibleSignaturePlacement(
            int pageIndex,
            float x,
            float y,
            float width,
            float height
    ) {
    }

    public record SignedFileInspection(
            String fileName,
            Path absolutePath,
            Instant creationTime,
            List<SignedFileSigner> signers,
            byte[] embeddedContent,
            String contentTypeLabel,
            boolean textPreviewAvailable,
            String suggestedExtension
    ) {
    }

    public record SignedFileSigner(
            String displayName,
            Instant signingTime
    ) {
    }

    private record SignedContentDescriptor(
            String label,
            boolean textPreviewAvailable,
            String suggestedExtension
    ) {
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

    private static final class CMSProcessableInputStream implements CMSTypedData {
        private final InputStream inputStream;

        private CMSProcessableInputStream(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public ASN1ObjectIdentifier getContentType() {
            return PKCSObjectIdentifiers.data;
        }

        @Override
        public Object getContent() {
            return inputStream;
        }

        @Override
        public void write(OutputStream outputStream) throws IOException {
            inputStream.transferTo(outputStream);
            inputStream.close();
        }
    }
}
