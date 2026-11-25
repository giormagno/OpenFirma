package it.firmadigitale;

import com.kitfox.svg.app.beans.SVGIcon;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.image.BufferedImage;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.JTabbedPane;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

public final class SignatureFrame extends JFrame {

    private static final Color APP_BACKGROUND = new Color(245, 247, 247);
    private static final Color CARD_BACKGROUND = new Color(239, 241, 241);
    private static final Color CARD_BORDER = new Color(171, 173, 174, 70);
    private static final Color HEADER_SURFACE = new Color(255, 255, 255);
    private static final Color ACCENT = new Color(0, 102, 108);
    private static final Color ACCENT_BRIGHT = new Color(0, 241, 254);
    private static final Color ACCENT_SOFT = new Color(207, 248, 251);
    private static final Color ACCENT_PALE = new Color(240, 251, 252);
    private static final Color DROP_SURFACE = new Color(255, 255, 255);
    private static final Color DROP_BORDER = new Color(171, 173, 174, 110);
    private static final Color DROP_ACTIVE = new Color(0, 102, 108);
    private static final Color QUEUE_SURFACE = new Color(255, 255, 255);
    private static final Color PANEL_SURFACE = new Color(217, 222, 222);
    private static final Color TEXT_PRIMARY = new Color(44, 47, 48);
    private static final Color TEXT_MUTED = new Color(89, 92, 92);
    private static final Color CERTIFICATE_BADGE_AVAILABLE = new Color(197, 231, 255);
    private static final Color CERTIFICATE_BADGE_EMPTY = new Color(182, 39, 52);
    private static final Color ASSET_ROW_ODD = new Color(240, 242, 242);
    private static final Color ASSET_ROW_EVEN = new Color(232, 236, 236);
    private static final int EMPTY_QUEUE_PANEL_HEIGHT = 176;
    private static final int QUEUE_PANEL_BASE_HEIGHT = 164;
    private static final int ASSET_ROW_HEIGHT = 88;
    private static final float PDF_PREVIEW_DPI = 144f;
    private static final float PDF_SIGNATURE_DEFAULT_WIDTH = 176f;
    private static final float PDF_SIGNATURE_DEFAULT_HEIGHT = 64f;
    private static final float PDF_SIGNATURE_MARGIN = 28f;
    private static final Pattern COMMON_NAME_PATTERN = Pattern.compile("(^|,\\s*)CN=([^,]+)");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_VALUE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String INVALID_FILE_NAME_CHARS = "\\/:*?\"<>|";

    private final SignatureService signatureService;
    private final JComboBox<SigningIdentity> identityCombo = new JComboBox<>();
    private final JTextField outputField = new JTextField(Path.of(System.getProperty("user.dir"), "output").toString());
    private final DefaultComboBoxModel<Path> fileQueueModel = new DefaultComboBoxModel<>();
    private final JComboBox<Path> fileQueueCombo = new JComboBox<>(fileQueueModel);
    private final JTextArea fileDetailsArea = new JTextArea();
    private final JTextArea logArea = new JTextArea();
    private final DropSurfacePanel dropZonePanel = new DropSurfacePanel();
    private final JLabel certificateCountLabel = new JLabel("Nessun certificato disponibile");
    private final JLabel fileCountLabel = new JLabel("0 file");
    private final JLabel queueSummaryLabel = new JLabel("Nessun documento caricato");
    private final JLabel totalSizeLabel = new JLabel("0 B total");
    private final JLabel statusLabel = new JLabel("Pronto");
    private final JLabel activeAssetLabel = new JLabel("Focused asset");
    private final JButton logButton = new JButton("LOG");
    private final JButton verifyButton = new JButton("Verifica documenti");
    private final JButton loadCertificatesButton = new JButton("Aggiorna certificati");
    private final InfoIconLabel certificateInfoIcon = new InfoIconLabel();
    private final JButton browseFilesButton = new JButton("Aggiungi file…");
    private final JButton useP7mButton = new JButton("Usa P7M per tutti");
    private boolean loadingCertificates;
    private final JButton browseOutputButton = new JButton("Scegli");
    private final JButton removeFileButton = new JButton("Rimuovi selezionato");
    private final JButton clearFilesButton = new JButton("Svuota coda");
    private final JButton signButton = new JButton("Firma file");
    private final JComboBox<SigningModeOption> signingModeCombo = new JComboBox<>(SigningModeOption.values());
    private final JProgressBar progressBar = new JProgressBar();

    private JPanel filesWorkspacePanel;
    private JPanel leftColumnPanel;
    private JPanel rightColumnPanel;
    private JPanel queuePanel;
    private JPanel queueBodyPanel;
    private JPanel queueEmptyStatePanel;
    private JPanel stagedAssetsSurfacePanel;
    private JPanel stagedAssetsListPanel;
    private boolean busy;
    private boolean wideFilesLayout;

    public SignatureFrame(SignatureService signatureService) {
        super("OpenFirma");
        this.signatureService = signatureService;
        setIconImage(createSvgImage("/design/icon/logo.svg", new Dimension(64, 64)));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(650, Math.min(540, computeInitialWindowSize().height)));

        setContentPane(buildRootPanel());
        setSize(computeInitialWindowSize());
        setLocationRelativeTo(null);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (getWidth() < 650) {
                    SwingUtilities.invokeLater(() -> setSize(650, getHeight()));
                }
            }
        });
        configureComponents();
        bindActions();
        refreshCertificateInfoState();
        refreshFileDetails();
        updateActionState();

        SwingUtilities.invokeLater(() -> {
            refreshFilesWorkspaceLayout();
            loadCertificates();
        });
    }

    private JPanel buildRootPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(APP_BACKGROUND);
        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(20, 0));
        panel.setBackground(HEADER_SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                new EmptyBorder(8, 18, 8, 18)
        ));

        JPanel textPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        textPanel.setOpaque(false);

        JLabel markLabel = new JLabel();
        markLabel.setOpaque(false);
        markLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
        markLabel.setIcon(createSvgIcon("/design/icon/logo.svg", new Dimension(40, 40)));

        JLabel titleLabel = new JLabel("OpenFirma");
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));

        textPanel.add(markLabel);
        textPanel.add(titleLabel);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        badgePanel.setOpaque(false);

        panel.add(textPanel, BorderLayout.CENTER);
        badgePanel.add(signButton);
        badgePanel.add(verifyButton);
        badgePanel.add(logButton);
        panel.add(badgePanel, BorderLayout.EAST);
        return panel;
    }

    private JComponent buildCenterPanel() {
        BlueprintPanel mainPanel = new BlueprintPanel();
        mainPanel.setLayout(new BorderLayout());

        filesWorkspacePanel = new JPanel(new GridBagLayout());
        filesWorkspacePanel.setOpaque(false);
        filesWorkspacePanel.setBorder(new EmptyBorder(24, 22, 26, 18));
        filesWorkspacePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                refreshFilesWorkspaceLayout();
            }
        });

        JScrollPane contentScrollPane = new JScrollPane(filesWorkspacePanel);
        styleContentScrollPane(contentScrollPane);
        mainPanel.add(contentScrollPane, BorderLayout.CENTER);
        return mainPanel;
    }

    private JPanel buildLeftColumn() {
        leftColumnPanel = new JPanel(new GridBagLayout());
        leftColumnPanel.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 18, 0);
        leftColumnPanel.add(buildConfigurationCard(), constraints);

        constraints.gridy = 1;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 18, 0);
        leftColumnPanel.add(buildFilesCard(), constraints);

        constraints.gridy = 2;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 18, 0);
        leftColumnPanel.add(buildQueuePanel(), constraints);

        constraints.gridy = 3;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 0);
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        leftColumnPanel.add(spacer, constraints);
        return leftColumnPanel;
    }

    private JPanel buildRightColumn() {
        rightColumnPanel = new JPanel(new GridBagLayout());
        rightColumnPanel.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 18, 0);
        rightColumnPanel.add(buildQueuePanel(), constraints);

        constraints.gridy = 1;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        rightColumnPanel.add(buildFooterBar(), constraints);

        constraints.gridy = 2;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 0);
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        rightColumnPanel.add(spacer, constraints);
        return rightColumnPanel;
    }

    private JPanel buildConfigurationCard() {
        JPanel card = createCard();
        card.setLayout(new GridBagLayout());

        JLabel cardTitle = createSectionTitle("Seleziona Certificato");

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 4;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 10, 0);
        card.add(cardTitle, constraints);

        constraints.gridy = 1;
        constraints.insets = new Insets(0, 0, 18, 0);
        certificateCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        certificateCountLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
        card.add(certificateCountLabel, constraints);

        constraints.gridwidth = 1;
        constraints.gridy = 2;
        constraints.insets = new Insets(0, 0, 8, 0);
        card.add(createFieldLabel("Certificato"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 12, 8, 12);
        card.add(identityCombo, constraints);

        constraints.gridx = 2;
        constraints.insets = new Insets(0, 0, 8, 10);
        card.add(certificateInfoIcon, constraints);

        constraints.gridx = 3;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 8, 0);
        card.add(loadCertificatesButton, constraints);

        return card;
    }

    private JPanel buildFilesCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout());
        card.setPreferredSize(new Dimension(420, 298));
        card.add(buildDropZonePanel(), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildDropZonePanel() {
        return configureUploadDropZone(
                dropZonePanel,
                "FILE DA FIRMARE",
                browseFilesButton,
                new FileDropHandler(),
                () -> {
                    if (!busy) {
                        addFilesFromChooser();
                    }
                }
        );
    }

    private JPanel configureUploadDropZone(
            DropSurfacePanel targetPanel,
            String titleText,
            JButton browseButton,
            TransferHandler transferHandler,
            Runnable browseAction
    ) {
        targetPanel.removeAll();
        targetPanel.setLayout(new BorderLayout());
        targetPanel.setTransferHandler(transferHandler);
        targetPanel.setMinimumSize(new Dimension(380, 228));
        targetPanel.setPreferredSize(new Dimension(420, 252));
        targetPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        for (var listener : targetPanel.getMouseListeners()) {
            targetPanel.removeMouseListener(listener);
        }
        targetPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                browseAction.run();
            }
        });

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(14, 18, 20, 18));

        JComponent glyph = createDropZoneSvgLabel();
        glyph.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel(titleText);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("INSERISCI QUI I TUOI FILE");
        subtitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel orLabel = new JLabel("o");
        orLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        orLabel.setForeground(TEXT_MUTED);
        orLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        styleButton(browseButton, false);
        browseButton.setFocusPainted(true);
        browseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        browseButton.addActionListener(event -> browseAction.run());
        browseButton.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("pressed ENTER"), "pressed");
        browseButton.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("released ENTER"), "released");

        content.add(Box.createVerticalGlue());
        content.add(glyph);
        content.add(Box.createVerticalStrut(6));
        content.add(title);
        content.add(Box.createVerticalStrut(2));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(2));
        content.add(orLabel);
        content.add(Box.createVerticalStrut(2));
        content.add(browseButton);
        content.add(Box.createVerticalGlue());

        targetPanel.add(content, BorderLayout.CENTER);
        return targetPanel;
    }

    private JPanel buildQueuePanel() {
        queuePanel = createQueueSurface();
        queuePanel.setLayout(new BorderLayout(0, 16));
        queuePanel.setMinimumSize(new Dimension(420, 220));
        queuePanel.setPreferredSize(new Dimension(520, 300));

        JPanel headerPanel = new JPanel(new BorderLayout(12, 0));
        headerPanel.setOpaque(false);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleRow.setOpaque(false);

        JLabel queueTitle = new JLabel("Coda delle firme");
        queueTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        queueTitle.setForeground(TEXT_PRIMARY);

        JLabel modeLabel = new JLabel("Tipo firma");
        modeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        modeLabel.setForeground(TEXT_MUTED);

        titleRow.add(queueTitle);
        titleRow.add(modeLabel);
        titleRow.add(signingModeCombo);
        titlePanel.add(titleRow, BorderLayout.CENTER);

        JPanel rightHeader = new JPanel(new BorderLayout(10, 0));
        rightHeader.setOpaque(false);
        rightHeader.add(fileCountLabel, BorderLayout.WEST);
        rightHeader.add(totalSizeLabel, BorderLayout.EAST);

        headerPanel.add(titlePanel, BorderLayout.CENTER);
        headerPanel.add(rightHeader, BorderLayout.EAST);

        stagedAssetsListPanel = new JPanel();
        stagedAssetsListPanel.setOpaque(false);
        stagedAssetsListPanel.setLayout(new BoxLayout(stagedAssetsListPanel, BoxLayout.Y_AXIS));
        stagedAssetsListPanel.setBorder(new EmptyBorder(10, 0, 14, 0));

        stagedAssetsSurfacePanel = new JPanel(new BorderLayout());
        stagedAssetsSurfacePanel.setOpaque(true);
        stagedAssetsSurfacePanel.setBackground(Color.WHITE);
        stagedAssetsSurfacePanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CARD_BORDER));

        JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
        centerPanel.setOpaque(false);
        centerPanel.add(buildOutputDirectoryRow(), BorderLayout.NORTH);

        queueBodyPanel = new JPanel(new BorderLayout());
        queueBodyPanel.setOpaque(false);
        centerPanel.add(queueBodyPanel, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new BorderLayout(12, 0));
        footerPanel.setOpaque(false);
        footerPanel.add(clearFilesButton, BorderLayout.WEST);
        footerPanel.add(useP7mButton, BorderLayout.CENTER);

        JPanel removePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        removePanel.setOpaque(false);
        removePanel.add(removeFileButton);
        footerPanel.add(removePanel, BorderLayout.EAST);

        queuePanel.add(headerPanel, BorderLayout.NORTH);
        queuePanel.add(centerPanel, BorderLayout.CENTER);
        queuePanel.add(footerPanel, BorderLayout.SOUTH);
        return queuePanel;
    }

    private JPanel buildOutputDirectoryRow() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        panel.add(createFieldLabel("Cartella destinazione"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 12, 0, 12);
        panel.add(outputField, constraints);

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(0, 0, 0, 0);
        panel.add(browseOutputButton, constraints);
        return panel;
    }

    private void refreshFilesWorkspaceLayout() {
        if (filesWorkspacePanel == null) {
            return;
        }

        if (filesWorkspacePanel.getComponentCount() > 0) {
            return;
        }

        filesWorkspacePanel.removeAll();
        leftColumnPanel = buildLeftColumn();

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.insets = new Insets(0, 0, 0, 0);
        filesWorkspacePanel.add(leftColumnPanel, constraints);

        filesWorkspacePanel.revalidate();
        filesWorkspacePanel.repaint();
        refreshFileDetails();
    }

    private JPanel buildFooterBar() {
        JPanel footer = createCard();
        footer.setLayout(new BorderLayout(0, 16));

        JPanel topPanel = new JPanel(new BorderLayout(12, 0));
        topPanel.setOpaque(false);

        JPanel leftMeta = new JPanel(new BorderLayout(0, 4));
        leftMeta.setOpaque(false);
        JLabel footerTitle = new JLabel("Execution panel");
        footerTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        footerTitle.setForeground(TEXT_PRIMARY);
        JLabel hashLabel = new JLabel("Auth hash  0x" + Integer.toHexString(Math.abs(outputField.getText().hashCode())).toUpperCase());
        hashLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        hashLabel.setForeground(ACCENT);
        leftMeta.add(footerTitle, BorderLayout.NORTH);
        leftMeta.add(hashLabel, BorderLayout.SOUTH);

        JPanel rightMeta = new JPanel(new BorderLayout(0, 4));
        rightMeta.setOpaque(false);
        activeAssetLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        activeAssetLabel.setForeground(TEXT_MUTED);
        rightMeta.add(activeAssetLabel, BorderLayout.NORTH);
        rightMeta.add(fileQueueCombo, BorderLayout.CENTER);

        topPanel.add(leftMeta, BorderLayout.WEST);
        topPanel.add(rightMeta, BorderLayout.CENTER);

        JPanel detailsPanel = new JPanel(new BorderLayout(0, 10));
        detailsPanel.setOpaque(true);
        detailsPanel.setBackground(Color.WHITE);
        detailsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                new EmptyBorder(16, 16, 16, 16)
        ));

        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        fileDetailsArea.setEditable(false);
        fileDetailsArea.setLineWrap(true);
        fileDetailsArea.setWrapStyleWord(true);
        fileDetailsArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fileDetailsArea.setForeground(TEXT_PRIMARY);
        fileDetailsArea.setBackground(Color.WHITE);
        fileDetailsArea.setBorder(new EmptyBorder(0, 0, 0, 0));

        JScrollPane detailScrollPane = new JScrollPane(fileDetailsArea);
        detailScrollPane.setBorder(BorderFactory.createEmptyBorder());
        detailScrollPane.getViewport().setBackground(Color.WHITE);
        detailScrollPane.setPreferredSize(new Dimension(280, 120));

        detailsPanel.add(detailScrollPane, BorderLayout.CENTER);
        detailsPanel.add(statusLabel, BorderLayout.SOUTH);

        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setStringPainted(false);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(progressBar);
        rightPanel.add(signButton);

        footer.add(topPanel, BorderLayout.NORTH);
        footer.add(detailsPanel, BorderLayout.CENTER);
        footer.add(rightPanel, BorderLayout.SOUTH);
        return footer;
    }

    private void configureComponents() {
        getContentPane().setBackground(APP_BACKGROUND);

        identityCombo.setRenderer(new IdentityRenderer());
        signingModeCombo.setRenderer(new SigningModeRenderer());
        identityCombo.setMaximumRowCount(12);
        signingModeCombo.setMaximumRowCount(2);
        fileQueueCombo.setMaximumRowCount(16);

        styleInput(identityCombo);
        styleInput(signingModeCombo);
        styleInput(fileQueueCombo);
        styleInput(outputField);
        styleOutputField(outputField);
        styleInfoIcon(certificateInfoIcon);
        styleButton(verifyButton, false);
        styleButton(logButton, false);
        certificateCountLabel.setOpaque(true);
        styleBadge(fileCountLabel, new Color(231, 243, 238), new Color(18, 97, 79));
        totalSizeLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        totalSizeLabel.setForeground(ACCENT);

        styleButton(loadCertificatesButton, false);
        styleButton(removeFileButton, false);
        styleButton(clearFilesButton, false);
        styleButton(signButton, true);
        styleButton(browseOutputButton, false);

        alignInputHeight(identityCombo, loadCertificatesButton);
        alignInputHeight(outputField, browseOutputButton);
        alignSquareComponent(certificateInfoIcon, loadCertificatesButton);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        logArea.setBackground(new Color(248, 248, 244));
        signButton.setPreferredSize(new Dimension(144, 40));
        signingModeCombo.setPreferredSize(new Dimension(136, 34));
        signingModeCombo.setMinimumSize(new Dimension(136, 34));
        signingModeCombo.setMaximumSize(new Dimension(136, 34));
        verifyButton.setPreferredSize(new Dimension(148, 40));
        logButton.setPreferredSize(new Dimension(82, 40));
        progressBar.setPreferredSize(new Dimension(170, 18));
        updateCertificateCountBadge(0);

        appendLog("Applicazione avviata. Modalita GUI fissata su Windows-MY.");
    }

    private void bindActions() {
        browseOutputButton.addActionListener(event -> selectOutputDirectory());
        logButton.addActionListener(event -> showLogPopup());
        verifyButton.addActionListener(event -> verifySignedFileFromChooser());
        certificateInfoIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (certificateInfoIcon.isEnabled()) {
                    showCertificateDetailsPopup();
                }
            }
        });
        loadCertificatesButton.addActionListener(event -> loadCertificates());
        removeFileButton.addActionListener(event -> removeSelectedFile());
        clearFilesButton.addActionListener(event -> clearFiles());
        signButton.addActionListener(event -> signFiles());
        useP7mButton.addActionListener(event -> signingModeCombo.setSelectedItem(SigningModeOption.ENVELOPED));
        styleButton(useP7mButton, false);
        signingModeCombo.addActionListener(event -> {
            refreshFileDetails();
            updateActionState();
        });
        fileQueueCombo.addActionListener(event -> {
            refreshFileDetails();
            updateActionState();
        });
        identityCombo.addActionListener(event -> {
            refreshCertificateInfoState();
            updateActionState();
        });
        outputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshFileDetails();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshFileDetails();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshFileDetails();
            }
        });
    }

    private void addFilesFromChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            List<Path> paths = new ArrayList<>();
            for (File file : chooser.getSelectedFiles()) {
                paths.add(file.toPath());
            }
            addFiles(paths);
        }
    }

    private void addFiles(List<Path> files) {
        int addedCount = 0;
        Path firstAdded = null;

        for (Path file : files) {
            if (file == null || !Files.isRegularFile(file) || containsFile(file)) {
                continue;
            }
            fileQueueModel.addElement(file);
            if (firstAdded == null) {
                firstAdded = file;
            }
            addedCount++;
        }

        if (firstAdded != null) {
            fileQueueCombo.setSelectedItem(firstAdded);
        }

        if (addedCount > 0) {
            appendLog("Aggiunti " + addedCount + " file alla coda di firma.");
        }

        refreshFileDetails();
        updateActionState();
    }

    private boolean containsFile(Path candidate) {
        for (int index = 0; index < fileQueueModel.getSize(); index++) {
            Path existing = fileQueueModel.getElementAt(index);
            if (existing.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void removeSelectedFile() {
        Path selectedFile = (Path) fileQueueCombo.getSelectedItem();
        if (selectedFile == null) {
            return;
        }

        fileQueueModel.removeElement(selectedFile);
        appendLog("Rimosso dalla coda: " + selectedFile.getFileName());
        refreshFileDetails();
        updateActionState();
    }

    private void clearFiles() {
        if (fileQueueModel.getSize() == 0) {
            return;
        }

        fileQueueModel.removeAllElements();
        appendLog("Coda file svuotata.");
        refreshFileDetails();
        updateActionState();
    }

    private void loadCertificates() {
        loadingCertificates = true;
        updateActionState();
        appendLog("Carico i certificati disponibili in Windows-MY...");

        SwingWorker<List<SigningIdentity>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SigningIdentity> doInBackground() throws Exception {
                return signatureService.listSigningIdentities(buildConfig());
            }

            @Override
            protected void done() {
                try {
                    List<SigningIdentity> identities = get();
                    identityCombo.removeAllItems();
                    for (SigningIdentity identity : identities) {
                        identityCombo.addItem(identity);
                    }

                    if (!identities.isEmpty()) {
                        identityCombo.setSelectedIndex(0);
                    }

                    appendLog("Certificati caricati: " + identities.size());
                    updateCertificateCountBadge(identities.size());
                    refreshCertificateInfoState();
                    statusLabel.setText(identities.isEmpty() ? "Verifica documenti disponibile." : "Certificati pronti.");
                } catch (Exception ex) {
                    identityCombo.removeAllItems();
                    updateCertificateCountBadge(0);
                    refreshCertificateInfoState();
                    appendLog("Lettura certificati fallita: " + resolveMessage(ex));
                    certificateCountLabel.setToolTipText(resolveMessage(ex));
                } finally {
                    loadingCertificates = false;
                    updateActionState();
                }
            }
        };

        worker.execute();
    }

    private void signFiles() {
        if (fileQueueModel.getSize() == 0) {
            showError("Aggiungi almeno un file da firmare.");
            return;
        }

        SigningIdentity selectedIdentity = (SigningIdentity) identityCombo.getSelectedItem();
        if (selectedIdentity == null) {
            showError("Seleziona un certificato di firma.");
            return;
        }

        List<Path> files = getQueuedFiles();
        Path outputDirectory = Path.of(outputField.getText().trim());
        SigningModeOption signingMode = getSelectedSigningMode();
        if (signingMode == SigningModeOption.PDF) {
            if (!validatePdfQueue(files)) {
                return;
            }

            try {
                PdfSigningDialog pdfSigningDialog = new PdfSigningDialog(selectedIdentity, outputDirectory, files);
                pdfSigningDialog.open();
            } catch (Exception ex) {
                showError("Impossibile inizializzare la firma PDF: " + resolveMessage(ex));
            }
            return;
        }

        OutputPlan outputTargets = resolveOutputTargets(files, outputDirectory, signingMode);
        if (outputTargets == null) {
            return;
        }

        startSigningProcess(selectedIdentity, outputDirectory, files, outputTargets, signingMode, null);
    }

    private void startSigningProcess(
            SigningIdentity selectedIdentity,
            Path outputDirectory,
            List<Path> files,
            OutputPlan outputTargets,
            SigningModeOption signingMode,
            Map<Path, PdfSigningPlan> pdfPlans
    ) {
        SigningProgressDialog signingDialog = new SigningProgressDialog(selectedIdentity, outputDirectory, files, outputTargets.paths());
        String signingLabel = signingMode == SigningModeOption.PDF ? "PDF" : "P7M";

        setBusy(true, "Firma " + signingLabel + " in corso su " + files.size() + " file...");
        appendLog("Avvio firma " + signingLabel + " di " + files.size() + " file con certificato "
                + selectedIdentity.displayName() + ".");
        signingDialog.open();

        SwingWorker<Void, SigningFileUpdate> worker = new SwingWorker<>() {
            private final List<SigningFileUpdate> created = new ArrayList<>();
            private Path activeInput;

            @Override
            protected Void doInBackground() throws Exception {
                for (int index = 0; index < files.size(); index++) {
                    Path file = files.get(index);
                    activeInput = file;
                    publish(new SigningFileUpdate(file, null, index, null));
                    Path result;
                    if (signingMode == SigningModeOption.PDF) {
                        PdfSigningPlan pdfPlan = pdfPlans == null ? null : pdfPlans.get(file);
                        result = signatureService.signPdfFile(
                                buildConfig(),
                                selectedIdentity.alias(),
                                file,
                                outputTargets.paths().get(file),
                                pdfPlan == null ? null : pdfPlan.placements(),
                                selectedIdentity.displayName(), outputTargets.overwrites().contains(file)
                        );
                    } else {
                        result = signatureService.signFile(
                                buildConfig(),
                                selectedIdentity.alias(),
                                file,
                                outputTargets.paths().get(file), outputTargets.overwrites().contains(file)
                        );
                    }
                    SigningFileUpdate update = new SigningFileUpdate(file, result, index + 1, checkOutput(result));
                    created.add(update);
                    publish(update);
                }
                return null;
            }

            @Override
            protected void process(List<SigningFileUpdate> updates) {
                for (SigningFileUpdate update : updates) {
                    if (update.outputFile() == null) {
                        signingDialog.markProcessing(update.inputFile(), update.completedCount());
                        continue;
                    }

                    appendLog("Creato " + update.outputFile() + " | " + update.verification().detail());
                    signingDialog.markCompleted(update);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    signingDialog.finish(created, null, null);
                } catch (Exception ex) {
                    signingDialog.finish(created, activeInput, resolveMessage(ex));
                } finally {
                    for (SigningFileUpdate update : created) fileQueueModel.removeElement(update.inputFile());
                    setBusy(false, signingDialog.subtitleLabel.getText());
                    refreshFileDetails();
                }
            }
        };

        worker.execute();
    }

    private SignatureConfig buildConfig() {
        return new SignatureConfig(KeyStoreMode.WINDOWS_MY, "", "");
    }

    private SigningModeOption getSelectedSigningMode() {
        Object selectedItem = signingModeCombo.getSelectedItem();
        return selectedItem instanceof SigningModeOption mode ? mode : SigningModeOption.ENVELOPED;
    }

    private void verifySignedFileFromChooser() {
        showVerifyQueuePopup();
    }

    private void showVerifyQueuePopup() {
        DefaultComboBoxModel<Path> verificationModel = new DefaultComboBoxModel<>();
        DropSurfacePanel verificationDropPanel = new DropSurfacePanel();
        JButton browseVerificationButton = new JButton("Aggiungi file…");

        JDialog dialog = new JDialog(this, "Verifica file", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JList<Path> verificationList = new JList<>(verificationModel);
        verificationList.setCellRenderer(new PathRenderer());
        verificationList.setVisibleRowCount(5);

        JScrollPane listScrollPane = new JScrollPane(verificationList);
        stylePopupScrollPane(listScrollPane, Color.WHITE, BorderFactory.createLineBorder(new Color(223, 227, 231)));

        JLabel fileCountBadge = createInfoBadge("0 file");
        JLabel hintLabel = createSectionSubtitle("Carica uno o piu file firmati e avvia la verifica quando la coda e completa.");

        JPanel listContainer = new JPanel(new BorderLayout());
        listContainer.setOpaque(false);

        JButton clearButton = new JButton("Svuota");
        JButton verifyActionButton = new JButton("Verifica");
        JButton closeButton = new JButton("Chiudi");
        styleButton(clearButton, false);
        styleButton(verifyActionButton, true);
        styleButton(closeButton, false);

        TransferHandler verificationTransferHandler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                boolean accepted = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                verificationDropPanel.setDragActive(support.isDrop() && accepted);
                return accepted;
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }

                try {
                    List<File> droppedFiles = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    List<Path> paths = new ArrayList<>();
                    for (File file : droppedFiles) {
                        paths.add(file.toPath());
                    }
                    int addedCount = addPathsToModel(verificationModel, paths);
                    if (addedCount > 0) {
                        appendLog("Aggiunti " + addedCount + " file alla verifica.");
                    }
                    return addedCount > 0;
                } catch (Exception ex) {
                    showError("Impossibile acquisire i file per la verifica: " + ex.getMessage());
                    return false;
                } finally {
                    verificationDropPanel.setDragActive(false);
                    refreshVerificationQueueState(verificationModel, fileCountBadge, hintLabel, listContainer, listScrollPane, verifyActionButton, clearButton);
                }
            }
        };

        configureUploadDropZone(
                verificationDropPanel,
                "FILE DA VERIFICARE",
                browseVerificationButton,
                verificationTransferHandler,
                () -> addVerificationFilesFromChooser(verificationModel, fileCountBadge, hintLabel, listContainer, listScrollPane, verifyActionButton, clearButton, dialog)
        );

        refreshVerificationQueueState(verificationModel, fileCountBadge, hintLabel, listContainer, listScrollPane, verifyActionButton, clearButton);

        clearButton.addActionListener(event -> {
            verificationModel.removeAllElements();
            refreshVerificationQueueState(verificationModel, fileCountBadge, hintLabel, listContainer, listScrollPane, verifyActionButton, clearButton);
        });
        verifyActionButton.addActionListener(event -> {
            List<Path> signedFiles = getPaths(verificationModel);
            if (signedFiles.isEmpty()) {
                return;
            }
            appendLog("Avvio verifica di " + signedFiles.size() + " file firmati.");
            dialog.dispose();
            showSignedFilesVerificationPopup(signedFiles, "Riepilogo dei file firmati caricati per la verifica.");
        });
        closeButton.addActionListener(event -> dialog.dispose());

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(APP_BACKGROUND);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
        headerPanel.setOpaque(false);
        headerPanel.add(createSectionTitle("Verifica file"), BorderLayout.NORTH);

        JPanel headerBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headerBottom.setOpaque(false);
        headerBottom.add(fileCountBadge);
        headerBottom.add(hintLabel);
        headerPanel.add(headerBottom, BorderLayout.SOUTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(verificationDropPanel);
        centerPanel.add(Box.createVerticalStrut(12));
        centerPanel.add(listContainer);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);
        footerPanel.add(clearButton, BorderLayout.WEST);

        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footerActions.setOpaque(false);
        footerActions.add(closeButton);
        footerActions.add(verifyActionButton);
        footerPanel.add(footerActions, BorderLayout.EAST);

        root.add(headerPanel, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
        root.add(footerPanel, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(760, dialog.getPreferredSize().height));
        dialog.setSize(new Dimension(760, dialog.getPreferredSize().height));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void addVerificationFilesFromChooser(
            DefaultComboBoxModel<Path> verificationModel,
            JLabel fileCountBadge,
            JLabel hintLabel,
            JPanel listContainer,
            JScrollPane listScrollPane,
            JButton verifyActionButton,
            JButton clearButton,
            JDialog dialog
    ) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleziona file firmati");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            List<Path> paths = new ArrayList<>();
            for (File file : chooser.getSelectedFiles()) {
                paths.add(file.toPath());
            }
            int addedCount = addPathsToModel(verificationModel, paths);
            if (addedCount > 0) {
                appendLog("Aggiunti " + addedCount + " file alla verifica.");
            }
            refreshVerificationQueueState(verificationModel, fileCountBadge, hintLabel, listContainer, listScrollPane, verifyActionButton, clearButton);
        }
    }

    private void refreshVerificationQueueState(
            DefaultComboBoxModel<Path> verificationModel,
            JLabel fileCountBadge,
            JLabel hintLabel,
            JPanel listContainer,
            JScrollPane listScrollPane,
            JButton verifyActionButton,
            JButton clearButton
    ) {
        int fileCount = verificationModel.getSize();
        fileCountBadge.setText(fileCount == 1 ? "1 file" : fileCount + " file");
        hintLabel.setText(fileCount == 0
                ? "Carica uno o piu file firmati e avvia la verifica quando la coda e completa."
                : "Coda di verifica pronta. Premi Verifica per analizzare i file caricati.");

        if (verifyActionButton != null) {
            verifyActionButton.setEnabled(fileCount > 0);
        }
        if (clearButton != null) {
            clearButton.setEnabled(fileCount > 0);
        }

        listContainer.removeAll();
        if (fileCount == 0) {
            JPanel emptyState = createQueueSurface();
            emptyState.setLayout(new BorderLayout());
            JLabel emptyLabel = new JLabel("nessun file da verificare", SwingConstants.CENTER);
            emptyLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            emptyLabel.setForeground(TEXT_MUTED);
            emptyState.add(emptyLabel, BorderLayout.CENTER);
            emptyState.setPreferredSize(new Dimension(0, 76));
            listContainer.add(emptyState, BorderLayout.CENTER);
        } else {
            listScrollPane.setPreferredSize(new Dimension(0, Math.min(220, 68 + (fileCount * 42))));
            listContainer.add(listScrollPane, BorderLayout.CENTER);
        }
        listContainer.revalidate();
        listContainer.repaint();
    }

    private int addPathsToModel(DefaultComboBoxModel<Path> model, List<Path> files) {
        int addedCount = 0;
        for (Path file : files) {
            if (file == null || !Files.isRegularFile(file) || containsFile(model, file)) {
                continue;
            }
            model.addElement(file);
            addedCount++;
        }
        return addedCount;
    }

    private boolean containsFile(DefaultComboBoxModel<Path> model, Path candidate) {
        for (int index = 0; index < model.getSize(); index++) {
            Path existing = model.getElementAt(index);
            if (existing.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<Path> getPaths(DefaultComboBoxModel<Path> model) {
        List<Path> paths = new ArrayList<>();
        for (int index = 0; index < model.getSize(); index++) {
            paths.add(model.getElementAt(index));
        }
        return List.copyOf(paths);
    }

    private record OutputPlan(Map<Path, Path> paths, java.util.Set<Path> overwrites) {}

    private OutputPlan resolveOutputTargets(List<Path> files, Path outputDirectory, SigningModeOption signingMode) {
        Map<Path, Path> outputTargets = new LinkedHashMap<>();
        for (Path file : files) {
            outputTargets.put(file, resolveSignedOutputPath(file, outputDirectory, signingMode));
        }
        java.util.Set<Path> claimed = new java.util.HashSet<>();
        java.util.Set<Path> overwrites = new java.util.HashSet<>();
        for (Path inputFile : files) {
            Path targetOutput = outputTargets.get(inputFile);
            boolean reserved = claimed.contains(targetOutput) || files.stream().anyMatch(other ->
                    !other.equals(inputFile) && other.toAbsolutePath().normalize().equals(targetOutput.toAbsolutePath().normalize()));
            while (reserved || Files.exists(targetOutput)) {
                String suggestedName = suggestRenameOutputFileName(inputFile, targetOutput, outputDirectory, outputTargets, signingMode);
                OverwriteDialogChoice choice = showOverwriteConflictPopup(targetOutput, suggestedName, reserved);
                if (choice == OverwriteDialogChoice.KEEP_BOTH) {
                    Path suggested = outputDirectory.resolve(suggestedName);
                    if (Files.exists(suggested)) {
                        continue;
                    }
                    outputTargets.put(inputFile, suggested);
                    appendLog("Mantieni entrambi: " + suggested.getFileName());
                    break;
                }
                if (choice == OverwriteDialogChoice.OVERWRITE) {
                    overwrites.add(inputFile);
                    appendLog("Sovrascrittura confermata per " + targetOutput.getFileName() + ".");
                    break;
                }
                if (choice == OverwriteDialogChoice.CANCEL) {
                    appendLog("Firma annullata: output esistente " + targetOutput.getFileName() + ".");
                    return null;
                }
                if (choice == OverwriteDialogChoice.INSPECT) {
                    appendLog("Verifica richiesta per il file esistente " + targetOutput.getFileName() + ".");
                    showExistingSignedFilePopup(targetOutput);
                    continue;
                }

                Path renamedOutput = showRenameOutputPopup(inputFile, targetOutput, outputDirectory, outputTargets, signingMode);
                if (renamedOutput == null) {
                    continue;
                }

                outputTargets.put(inputFile, renamedOutput);
                appendLog("Output rinominato per " + inputFile.getFileName() + ": " + renamedOutput.getFileName() + ".");
                break;
            }
            claimed.add(outputTargets.get(inputFile));
        }
        return new OutputPlan(Map.copyOf(outputTargets), java.util.Set.copyOf(overwrites));
    }

    private OverwriteDialogChoice showOverwriteConflictPopup(Path existingOutput, String suggestedName, boolean reserved) {
        OverwriteDialogChoice[] choice = new OverwriteDialogChoice[]{OverwriteDialogChoice.CANCEL};

        JDialog dialog = new JDialog(this, "File gia presente", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(APP_BACKGROUND);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
        headerPanel.setOpaque(false);
        headerPanel.add(createSectionTitle("File gia presente"), BorderLayout.NORTH);
        headerPanel.add(createSectionSubtitle(reserved ? "Due documenti in coda hanno lo stesso nome di destinazione." : "Esiste già un file con questo nome nella cartella di destinazione."), BorderLayout.SOUTH);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgePanel.setOpaque(false);
        badgePanel.add(createInfoBadge(existingOutput.getFileName().toString()));

        JPanel detailsPanel = createQueueSurface();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.add(createDialogDetailRow("Nome file", existingOutput.getFileName().toString()));
        detailsPanel.add(createDialogDetailRow("Percorso", existingOutput.toString()));

        detailsPanel.add(createDialogDetailRow("Nome con «Mantieni entrambi»", suggestedName));
        JButton keepButton = new JButton("Mantieni entrambi");
        styleButton(keepButton, true);
        keepButton.addActionListener(event -> {
            choice[0] = OverwriteDialogChoice.KEEP_BOTH;
            dialog.dispose();
        });
        JButton renameButton = new JButton("RINOMINA");
        JButton verifyButton = new JButton("VERIFICA FILE");
        JButton cancelButton = new JButton("ANNULLA");
        JButton overwriteButton = new JButton("SOVRASCRIVI");
        styleButton(renameButton, false);
        styleButton(verifyButton, false);
        styleButton(cancelButton, false);
        styleButton(overwriteButton, false);
        overwriteButton.setEnabled(!reserved);
        verifyButton.setEnabled(Files.exists(existingOutput));
        dialog.getRootPane().setDefaultButton(keepButton);
        renameButton.addActionListener(event -> {
            choice[0] = OverwriteDialogChoice.RENAME;
            dialog.dispose();
        });
        verifyButton.addActionListener(event -> {
            choice[0] = OverwriteDialogChoice.INSPECT;
            dialog.dispose();
        });
        cancelButton.addActionListener(event -> {
            choice[0] = OverwriteDialogChoice.CANCEL;
            dialog.dispose();
        });
        overwriteButton.addActionListener(event -> {
            choice[0] = OverwriteDialogChoice.OVERWRITE;
            dialog.dispose();
        });

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footerPanel.setOpaque(false);
        footerPanel.add(renameButton);
        footerPanel.add(verifyButton);
        footerPanel.add(cancelButton);
        footerPanel.add(overwriteButton);
        footerPanel.add(keepButton);

        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(badgePanel);
        centerPanel.add(Box.createVerticalStrut(12));
        centerPanel.add(detailsPanel);

        root.add(headerPanel, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
        root.add(footerPanel, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(dialog.getPreferredSize());
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return choice[0];
    }

    private void showExistingSignedFilePopup(Path signedFile) {
        showSignedFileVerificationPopup(signedFile, "Dettaglio del file gia presente nella cartella di output.");
    }

    private void showSignedFileVerificationPopup(Path signedFile, String subtitle) {
        showSignedFilesVerificationPopup(List.of(signedFile), subtitle);
    }

    private void showSignedFilesVerificationPopup(List<Path> signedFiles, String subtitle) {
        if (signedFiles == null || signedFiles.isEmpty()) {
            return;
        }

        List<SignedFileVerificationResult> results = new ArrayList<>();
        for (Path signedFile : signedFiles) {
            try {
                SignatureService.SignedFileInspection inspection = signatureService.inspectSignedFile(signedFile);
                boolean verified = signatureService.verifySignature(signedFile);
                results.add(new SignedFileVerificationResult(signedFile, inspection, verified, null));
            } catch (Exception ex) {
                results.add(new SignedFileVerificationResult(signedFile, null, false, resolveMessage(ex)));
            }
        }

        JDialog dialog = new JDialog(this, "Verifica file", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(true);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(APP_BACKGROUND);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
        headerPanel.setOpaque(false);
        headerPanel.add(createSectionTitle("Verifica file"), BorderLayout.NORTH);
        headerPanel.add(createSectionSubtitle(subtitle), BorderLayout.SOUTH);

        int validCount = 0;
        for (SignedFileVerificationResult result : results) {
            if (result.errorMessage() == null && result.verified()) {
                validCount++;
            }
        }

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgePanel.setOpaque(false);
        badgePanel.add(createInfoBadge(results.size() == 1 ? "1 file" : results.size() + " file"));
        badgePanel.add(createInfoBadge(validCount == 1 ? "1 verifica OK" : validCount + " verifiche OK"));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabbedPane.setBackground(HEADER_SURFACE);
        for (int index = 0; index < results.size(); index++) {
            SignedFileVerificationResult result = results.get(index);
            String tabTitle = result.signedFile().getFileName().toString();
            tabbedPane.addTab(tabTitle, createSignedFileVerificationTab(result, index));
            tabbedPane.setToolTipTextAt(index, result.signedFile().toAbsolutePath().toString());
        }

        JButton closeButton = new JButton("Chiudi");
        styleButton(closeButton, false);
        closeButton.addActionListener(event -> dialog.dispose());

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footerPanel.setOpaque(false);
        footerPanel.add(closeButton);

        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(badgePanel);
        centerPanel.add(Box.createVerticalStrut(12));
        centerPanel.add(tabbedPane);

        root.add(headerPanel, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
        root.add(footerPanel, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(860, 760));
        dialog.setSize(new Dimension(860, 760));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JScrollPane createSignedFileVerificationTab(SignedFileVerificationResult result, int index) {
        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(12, 0, 6, 0));

        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setOpaque(false);
        topBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pathPanel = new JPanel();
        pathPanel.setOpaque(false);
        pathPanel.setLayout(new BoxLayout(pathPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(result.signedFile().getFileName().toString());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel pathLabel = new JLabel(result.signedFile().toAbsolutePath().toString());
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        pathLabel.setForeground(TEXT_MUTED);
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathLabel.setToolTipText(pathLabel.getText());

        pathPanel.add(titleLabel);
        pathPanel.add(Box.createVerticalStrut(4));
        pathPanel.add(pathLabel);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(createInfoBadge("FILE " + (index + 1)));
        if (result.errorMessage() == null) {
            actionPanel.add(createInfoBadge(result.verified() ? "VERIFICA OK" : "VERIFICA KO"));
            JButton openButton = new JButton("Visualizza documento");
            styleButton(openButton, false);
            openButton.setEnabled(result.inspection().embeddedContent().length > 0);
            openButton.addActionListener(event -> openEmbeddedContent(result.inspection()));
            actionPanel.add(openButton);
        } else {
            JLabel errorBadge = createInfoBadge("ERRORE");
            errorBadge.setBackground(CERTIFICATE_BADGE_EMPTY);
            errorBadge.setForeground(Color.WHITE);
            actionPanel.add(errorBadge);
        }

        topBar.add(pathPanel, BorderLayout.CENTER);
        topBar.add(actionPanel, BorderLayout.EAST);

        contentPanel.add(topBar);
        contentPanel.add(Box.createVerticalStrut(12));

        if (result.errorMessage() != null) {
            JPanel errorPanel = createQueueSurface();
            errorPanel.setLayout(new BoxLayout(errorPanel, BoxLayout.Y_AXIS));
            errorPanel.add(createDialogDetailRow("Errore", result.errorMessage()));
            fitVerificationBlock(errorPanel);
            contentPanel.add(errorPanel);
            JScrollPane tabScrollPane = new JScrollPane(contentPanel);
            stylePopupScrollPane(tabScrollPane, APP_BACKGROUND, BorderFactory.createEmptyBorder());
            tabScrollPane.getViewport().setOpaque(false);
            return tabScrollPane;
        }

        SignatureService.SignedFileInspection inspection = result.inspection();

        JPanel signersPanel = createQueueSurface();
        signersPanel.setLayout(new BoxLayout(signersPanel, BoxLayout.Y_AXIS));
        JLabel signersTitle = createFieldLabel("Firmatari");
        signersTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        signersPanel.add(signersTitle);
        signersPanel.add(Box.createVerticalStrut(12));

        if (inspection.signers().isEmpty()) {
            signersPanel.add(createDialogDetailRow("Esito", "Nessun firmatario rilevato nel file selezionato."));
        } else {
            for (int signerIndex = 0; signerIndex < inspection.signers().size(); signerIndex++) {
                signersPanel.add(createSignerInfoRow(inspection.signers().get(signerIndex), signerIndex));
                if (signerIndex < inspection.signers().size() - 1) {
                    signersPanel.add(Box.createVerticalStrut(10));
                }
            }
        }
        fitVerificationBlock(signersPanel);

        JPanel infoPanel = createCompactVerificationInfoPanel(inspection);

        contentPanel.add(signersPanel);
        contentPanel.add(Box.createVerticalStrut(12));
        contentPanel.add(infoPanel);

        JScrollPane tabScrollPane = new JScrollPane(contentPanel);
        stylePopupScrollPane(tabScrollPane, APP_BACKGROUND, BorderFactory.createEmptyBorder());
        tabScrollPane.getViewport().setOpaque(false);
        return tabScrollPane;
    }

    private JPanel createCompactVerificationInfoPanel(SignatureService.SignedFileInspection inspection) {
        JPanel infoPanel = createQueueSurface();
        infoPanel.setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 10, 16);
        infoPanel.add(createCompactInfoLabel("Data creazione"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 10, 0);
        infoPanel.add(createCompactInfoValue(formatDateTime(inspection.creationTime())), constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(0, 0, 10, 16);
        infoPanel.add(createCompactInfoLabel("Data firma"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 10, 0);
        infoPanel.add(createCompactInfoValue(summarizeSigningTimes(inspection.signers())), constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(0, 0, 0, 16);
        infoPanel.add(createCompactInfoLabel("Contenuto"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 0, 0);
        infoPanel.add(createCompactInfoValue(inspection.contentTypeLabel() + "  |  " + formatSize(inspection.embeddedContent().length)), constraints);

        fitVerificationBlock(infoPanel);
        return infoPanel;
    }

    private JLabel createCompactInfoLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(TEXT_MUTED);
        return label;
    }

    private JLabel createCompactInfoValue(String text) {
        JLabel label = new JLabel(text == null || text.isBlank() ? "-" : text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(TEXT_PRIMARY);
        label.setToolTipText(label.getText());
        return label;
    }

    private void fitVerificationBlock(JPanel panel) {
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension preferredSize = panel.getPreferredSize();
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
    }

    private Path showRenameOutputPopup(
            Path inputFile,
            Path currentOutput,
            Path outputDirectory,
            Map<Path, Path> outputTargets,
            SigningModeOption signingMode
    ) {
        Path[] selectedOutput = new Path[1];

        JDialog dialog = new JDialog(this, "Rinomina output", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(APP_BACKGROUND);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
        headerPanel.setOpaque(false);
        headerPanel.add(createSectionTitle("Rinomina output"), BorderLayout.NORTH);
        headerPanel.add(createSectionSubtitle("Definisci il nuovo nome del file firmato per continuare con la firma."), BorderLayout.SOUTH);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgePanel.setOpaque(false);
        badgePanel.add(createInfoBadge(inputFile.getFileName().toString()));

        JPanel detailsPanel = createQueueSurface();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.add(createDialogDetailRow("Nome attuale", currentOutput.getFileName().toString()));
        detailsPanel.add(createDialogDetailRow("Cartella destinazione", outputDirectory.toString()));

        JPanel renamePanel = createQueueSurface();
        renamePanel.setLayout(new BorderLayout(0, 8));

        JLabel renameLabel = new JLabel("NUOVO NOME FILE");
        renameLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        renameLabel.setForeground(TEXT_MUTED);
        renamePanel.add(renameLabel, BorderLayout.NORTH);

        JTextField renameField = new JTextField(suggestRenameOutputFileName(
                inputFile,
                currentOutput,
                outputDirectory,
                outputTargets,
                signingMode
        ));
        styleInput(renameField);
        styleOutputField(renameField);
        renameField.setColumns(36);
        renameField.selectAll();
        renamePanel.add(renameField, BorderLayout.CENTER);

        JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        errorLabel.setForeground(CERTIFICATE_BADGE_EMPTY);
        renamePanel.add(errorLabel, BorderLayout.SOUTH);

        JButton cancelButton = new JButton("ANNULLA");
        JButton okButton = new JButton("OK");
        styleButton(cancelButton, false);
        styleButton(okButton, true);
        cancelButton.addActionListener(event -> dialog.dispose());
        okButton.addActionListener(event -> {
            String rawName = renameField.getText().trim();
            if (rawName.isBlank()) {
                errorLabel.setText("Inserisci un nome file.");
                return;
            }

            String normalizedName = normalizeOutputFileName(rawName, signingMode);
            if (hasInvalidFileNameChars(normalizedName) || normalizedName.contains("\\") || normalizedName.contains("/")) {
                errorLabel.setText("Il nome contiene caratteri non validi.");
                return;
            }

            Path renamedOutput = outputDirectory.resolve(normalizedName);
            if (Files.exists(renamedOutput)) {
                errorLabel.setText("Esiste gia un file con questo nome.");
                return;
            }
            if (isDuplicatePlannedOutput(inputFile, renamedOutput, outputTargets)) {
                errorLabel.setText("Questo nome e gia usato da un altro file in coda.");
                return;
            }

            selectedOutput[0] = renamedOutput;
            dialog.dispose();
        });

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footerPanel.setOpaque(false);
        footerPanel.add(cancelButton);
        footerPanel.add(okButton);

        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(badgePanel);
        centerPanel.add(Box.createVerticalStrut(12));
        centerPanel.add(detailsPanel);
        centerPanel.add(Box.createVerticalStrut(12));
        centerPanel.add(renamePanel);

        root.add(headerPanel, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
        root.add(footerPanel, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(dialog.getPreferredSize());
        dialog.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(renameField::requestFocusInWindow);
        dialog.setVisible(true);
        return selectedOutput[0];
    }

    private String suggestRenameOutputFileName(
            Path inputFile,
            Path currentOutput,
            Path outputDirectory,
            Map<Path, Path> outputTargets,
            SigningModeOption signingMode
    ) {
        String extension = signingMode == SigningModeOption.PDF ? ".pdf" : ".p7m";
        String stem = stripTrailingExtension(currentOutput.getFileName().toString()) + "_firmato";
        for (int index = 1; ; index++) {
            String name = stem + (index == 1 ? "" : " (" + index + ")") + extension;
            Path candidate = outputDirectory.resolve(name);
            if (!Files.exists(candidate) && !isDuplicatePlannedOutput(inputFile, candidate, outputTargets)) {
                return name;
            }
        }
    }

    private JPanel createDialogDetailRow(String labelText, String value) {
        JPanel row = new JPanel(new BorderLayout(0, 6));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(0, 0, 10, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText.toUpperCase());
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(TEXT_MUTED);
        row.add(label, BorderLayout.NORTH);

        JLabel valueLabel = new JLabel(toHtmlBlock(value == null || value.isBlank() ? "-" : value, 560));
        valueLabel.setOpaque(false);

        JPanel valuePanel = new JPanel(new BorderLayout());
        valuePanel.setOpaque(true);
        valuePanel.setBackground(Color.WHITE);
        valuePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(223, 227, 231)),
                new EmptyBorder(8, 10, 8, 10)
        ));
        valuePanel.add(valueLabel, BorderLayout.CENTER);

        row.add(valuePanel, BorderLayout.CENTER);
        return row;
    }

    private JPanel createSignerInfoRow(SignatureService.SignedFileSigner signer, int index) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(true);
        row.setBackground(index % 2 == 0 ? ASSET_ROW_ODD : ASSET_ROW_EVEN);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(223, 227, 231)),
                new EmptyBorder(12, 12, 12, 12)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel signerIndex = createInfoBadge("FIRMA " + (index + 1));
        signerIndex.setForeground(TEXT_PRIMARY);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(signer.displayName());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel timeLabel = new JLabel(signer.signingTime() == null
                ? "Data firma non disponibile"
                : "Data firma: " + formatDateTime(signer.signingTime()));
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        timeLabel.setForeground(TEXT_MUTED);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(nameLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(timeLabel);

        row.add(signerIndex, BorderLayout.WEST);
        row.add(textPanel, BorderLayout.CENTER);
        return row;
    }

    private void openEmbeddedContent(SignatureService.SignedFileInspection inspection) {
        try {
            if (inspection.embeddedContent().length == 0) {
                showError("Il file firmato non contiene un contenuto incorporato.");
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                showError("L'apertura del contenuto non e supportata in questo ambiente.");
                return;
            }

            String baseName = inspection.fileName().endsWith(".p7m")
                    ? inspection.fileName().substring(0, inspection.fileName().length() - 4)
                    : inspection.fileName();
            String safePrefix = sanitizeFileStem(baseName);
            if (safePrefix.length() < 3) {
                safePrefix = "ofm";
            }

            Path extractedFile = Files.createTempFile(safePrefix + "-", inspection.suggestedExtension());
            Files.write(extractedFile, inspection.embeddedContent());
            extractedFile.toFile().deleteOnExit();
            Desktop.getDesktop().open(extractedFile.toFile());
            appendLog("Contenuto estratto per verifica in " + extractedFile + ".");
        } catch (Exception ex) {
            showError("Impossibile aprire il contenuto incorporato: " + resolveMessage(ex));
        }
    }

    private String sanitizeFileStem(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.length() > 40 ? sanitized.substring(0, 40) : sanitized;
    }

    private Path resolveSignedOutputPath(Path inputFile, Path outputDirectory) {
        return resolveSignedOutputPath(inputFile, outputDirectory, getSelectedSigningMode());
    }

    private Path resolveSignedOutputPath(Path inputFile, Path outputDirectory, SigningModeOption signingMode) {
        return outputDirectory.resolve(resolveSignedOutputFileName(inputFile, outputDirectory, signingMode));
    }

    private String resolveSignedOutputFileName(Path inputFile, Path outputDirectory, SigningModeOption signingMode) {
        String fileName = inputFile.getFileName().toString();
        if (signingMode == SigningModeOption.PDF) {
            return fileName.toLowerCase(Locale.ROOT).endsWith(".pdf") ? fileName : fileName + ".pdf";
        }
        return fileName + ".p7m";
    }

    private String normalizeOutputFileName(String rawName, SigningModeOption signingMode) {
        String trimmedName = rawName.trim();
        String expectedExtension = signingMode == SigningModeOption.PDF ? ".pdf" : ".p7m";
        if (trimmedName.toLowerCase(Locale.ROOT).endsWith(expectedExtension)) {
            return trimmedName;
        }
        if (signingMode == SigningModeOption.PDF && trimmedName.contains(".")) {
            trimmedName = stripTrailingExtension(trimmedName);
        }
        return trimmedName + expectedExtension;
    }

    private String stripTrailingExtension(String value) {
        int extensionSeparatorIndex = value.lastIndexOf('.');
        if (extensionSeparatorIndex <= 0) {
            return value;
        }
        return value.substring(0, extensionSeparatorIndex);
    }

    private boolean validatePdfQueue(List<Path> files) {
        List<String> invalidFiles = new ArrayList<>();
        for (Path file : files) {
            if (isPdfFile(file)) {
                continue;
            }
            invalidFiles.add(file.getFileName().toString());
        }

        if (invalidFiles.isEmpty()) {
            return true;
        }

        showError("La modalita PDF accetta solo documenti PDF. File non compatibili: " + String.join(", ", invalidFiles));
        return false;
    }

    private boolean isPdfFile(Path file) {
        String lowerCaseName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".pdf")) {
            return true;
        }

        if (!Files.isRegularFile(file)) {
            return false;
        }

        try (var inputStream = Files.newInputStream(file)) {
            byte[] header = inputStream.readNBytes(4);
            return header.length == 4
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F';
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean isDuplicatePlannedOutput(Path currentInputFile, Path candidateOutput, Map<Path, Path> outputTargets) {
        for (Map.Entry<Path, Path> entry : outputTargets.entrySet()) {
            if (entry.getKey().equals(currentInputFile)) {
                continue;
            }
            if (entry.getValue().equals(candidateOutput)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInvalidFileNameChars(String fileName) {
        for (int index = 0; index < fileName.length(); index++) {
            if (INVALID_FILE_NAME_CHARS.indexOf(fileName.charAt(index)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private String summarizeSigningTimes(List<SignatureService.SignedFileSigner> signers) {
        List<String> values = new ArrayList<>();
        for (SignatureService.SignedFileSigner signer : signers) {
            if (signer.signingTime() == null) {
                continue;
            }
            String formatted = formatDateTime(signer.signingTime());
            if (!values.contains(formatted)) {
                values.add(formatted);
            }
        }
        return values.isEmpty() ? "Non disponibile" : String.join(" | ", values);
    }

    private String formatDateTime(Instant instant) {
        if (instant == null) {
            return "Non disponibile";
        }
        return DATE_TIME_VALUE_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    private String toHtmlBlock(String text, int width) {
        return "<html><div style='width:" + width + "px; font-family:Segoe UI; font-size:13px; color:#2C2F30;'>"
                + escapeHtml(text).replace("\n", "<br/>")
                + "</div></html>";
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private List<Path> getQueuedFiles() {
        List<Path> files = new ArrayList<>();
        for (int index = 0; index < fileQueueModel.getSize(); index++) {
            files.add(fileQueueModel.getElementAt(index));
        }
        return Collections.unmodifiableList(files);
    }

    private void selectOutputDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputField.setText(chooser.getSelectedFile().getAbsolutePath());
            appendLog("Cartella output aggiornata: " + chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void refreshFileDetails() {
        Path selectedFile = (Path) fileQueueCombo.getSelectedItem();
        int fileCount = fileQueueModel.getSize();
        fileCountLabel.setText(fileCount == 1 ? "1 file" : fileCount + " file");
        totalSizeLabel.setText(formatSize(calculateTotalSize()) + " total");
        queueSummaryLabel.setText(fileCount == 0
                ? "La coda e vuota: usa la zona di acquisizione per inizializzare il payload."
                : fileCount == 1
                ? "Un documento in stage. Selezionalo dalla lista o dal dropdown tecnico."
                : fileCount + " documenti in stage. Ogni riga resta visibile nell'elenco a destra.");

        if (selectedFile == null) {
            fileDetailsArea.setText("Nessun asset attivo.\n\nAggiungi file dal riquadro di caricamento per vedere percorso sorgente, output previsto e dimensione del documento selezionato.");
            refreshStagedAssetsList();
            return;
        }

        int selectedIndex = fileQueueCombo.getSelectedIndex();
        StringBuilder details = new StringBuilder();
        details.append("Documento ").append(selectedIndex + 1).append(" di ").append(fileCount).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Nome: ").append(selectedFile.getFileName()).append(System.lineSeparator())
                .append("Percorso: ").append(selectedFile.toAbsolutePath()).append(System.lineSeparator())
                .append("Output previsto: ")
                .append(resolveSignedOutputPath(
                        selectedFile,
                        Path.of(outputField.getText().isBlank() ? "." : outputField.getText().trim()),
                        getSelectedSigningMode()
                ))
                .append(System.lineSeparator());

        try {
            details.append("Dimensione: ").append(formatSize(Files.size(selectedFile)));
        } catch (IOException ex) {
            details.append("Dimensione: non disponibile");
        }

        fileDetailsArea.setText(details.toString());
        fileDetailsArea.setCaretPosition(0);
        refreshStagedAssetsList();
    }

    private void refreshStagedAssetsList() {
        if (stagedAssetsListPanel == null || queueBodyPanel == null || stagedAssetsSurfacePanel == null) {
            return;
        }

        stagedAssetsListPanel.removeAll();
        stagedAssetsSurfacePanel.removeAll();
        queueBodyPanel.removeAll();

        if (fileQueueModel.getSize() == 0) {
            queueBodyPanel.add(buildEmptyQueueState(), BorderLayout.CENTER);
            queueBodyPanel.setPreferredSize(new Dimension(0, 52));
            queueBodyPanel.setMinimumSize(new Dimension(0, 52));
            if (queuePanel != null) {
                queuePanel.setPreferredSize(new Dimension(520, EMPTY_QUEUE_PANEL_HEIGHT));
                queuePanel.setMinimumSize(new Dimension(420, EMPTY_QUEUE_PANEL_HEIGHT));
            }
        } else {
            Path selected = (Path) fileQueueCombo.getSelectedItem();
            for (int index = 0; index < fileQueueModel.getSize(); index++) {
                Path path = fileQueueModel.getElementAt(index);
                stagedAssetsListPanel.add(createAssetRow(path, path.equals(selected), index));
            }

            int listHeight = Math.max(ASSET_ROW_HEIGHT, fileQueueModel.getSize() * ASSET_ROW_HEIGHT) + 24;
            stagedAssetsSurfacePanel.add(stagedAssetsListPanel, BorderLayout.NORTH);
            stagedAssetsSurfacePanel.setPreferredSize(new Dimension(0, listHeight + 1));
            stagedAssetsSurfacePanel.setMinimumSize(new Dimension(0, listHeight + 1));
            queueBodyPanel.add(stagedAssetsSurfacePanel, BorderLayout.CENTER);
            queueBodyPanel.setPreferredSize(new Dimension(0, listHeight));
            queueBodyPanel.setMinimumSize(new Dimension(0, listHeight));
            if (queuePanel != null) {
                int queueHeight = QUEUE_PANEL_BASE_HEIGHT + listHeight;
                queuePanel.setPreferredSize(new Dimension(520, queueHeight));
                queuePanel.setMinimumSize(new Dimension(420, queueHeight));
            }
        }

        queueBodyPanel.revalidate();
        queueBodyPanel.repaint();
        stagedAssetsListPanel.revalidate();
        stagedAssetsListPanel.repaint();
    }

    private JPanel buildEmptyQueueState() {
        if (queueEmptyStatePanel != null) {
            return queueEmptyStatePanel;
        }

        queueEmptyStatePanel = new JPanel(new BorderLayout());
        queueEmptyStatePanel.setOpaque(false);
        queueEmptyStatePanel.setBorder(new EmptyBorder(6, 8, 2, 8));

        JLabel label = new JLabel("nessun file in coda", SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(new Color(TEXT_MUTED.getRed(), TEXT_MUTED.getGreen(), TEXT_MUTED.getBlue(), 170));
        queueEmptyStatePanel.add(label, BorderLayout.CENTER);
        return queueEmptyStatePanel;
    }

    private JPanel createAssetRow(Path path, boolean selected, int index) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(true);
        Color baseRowColor = index % 2 == 0 ? ASSET_ROW_ODD : ASSET_ROW_EVEN;
        row.setBackground(selected ? new Color(225, 233, 233) : baseRowColor);
        row.setBorder(new EmptyBorder(16, 16, 20, 16));
        row.setPreferredSize(new Dimension(0, ASSET_ROW_HEIGHT));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ASSET_ROW_HEIGHT));

        JPanel tokenPanel = new JPanel(new BorderLayout());
        tokenPanel.setOpaque(true);
        tokenPanel.setBackground(CARD_BACKGROUND);
        tokenPanel.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        tokenPanel.setPreferredSize(new Dimension(42, 42));

        JLabel tokenLabel = new JLabel(extractFileToken(path), SwingConstants.CENTER);
        tokenLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tokenLabel.setForeground(ACCENT);
        tokenPanel.add(tokenLabel, BorderLayout.CENTER);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(path.getFileName().toString());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean incompatible = getSelectedSigningMode() == SigningModeOption.PDF && !isPdfFile(path);
        String outputName = resolveSignedOutputFileName(path, Path.of("."), getSelectedSigningMode());
        JLabel metaLabel = new JLabel(incompatible ? "Incompatibile con la firma PDF" : "Risultato: " + outputName);
        metaLabel.setToolTipText(buildFileMeta(path));
        metaLabel.setFont(new Font("Segoe UI", Font.BOLD, 9));
        metaLabel.setForeground(TEXT_MUTED);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(nameLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(metaLabel);
        if (incompatible) {
            metaLabel.setForeground(CERTIFICATE_BADGE_EMPTY);
        }

        JButton removeButton = new JButton();
        removeButton.setFocusPainted(false);
        removeButton.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        removeButton.setBackground(row.getBackground());
        removeButton.setContentAreaFilled(false);
        removeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeButton.setToolTipText("Rimuovi file dalla coda");
        removeButton.setPreferredSize(new Dimension(30, 30));
        removeButton.setIcon(createSvgIcon("/design/icon/delete.svg", new Dimension(18, 18)));
        if (incompatible) {
            removeButton.setIcon(null);
            removeButton.setText("Rimuovi dalla coda");
            removeButton.setPreferredSize(null);
        }
        removeButton.addActionListener(event -> {
            if (busy) return;
            fileQueueModel.removeElement(path);
            appendLog("Rimosso dalla coda: " + path.getFileName());
            refreshFileDetails();
            updateActionState();
        });

        JPanel rightPanel = new JPanel(new BorderLayout(8, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(removeButton, BorderLayout.WEST);

        JPanel glyph = new JPanel();
        glyph.setBackground(selected ? ACCENT_BRIGHT : ACCENT);
        glyph.setPreferredSize(new Dimension(4, 18));
        rightPanel.add(glyph, BorderLayout.EAST);

        row.add(tokenPanel, BorderLayout.WEST);
        row.add(textPanel, BorderLayout.CENTER);
        row.add(rightPanel, BorderLayout.EAST);

        attachSelectionHandler(row, path);
        return row;
    }

    private void attachSelectionHandler(Component component, Path path) {
        if (!(component instanceof JButton)) {
            component.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (!busy) {
                        fileQueueCombo.setSelectedItem(path);
                    }
                }
            });
        }

        if (component instanceof JPanel panel) {
            for (Component child : panel.getComponents()) {
                attachSelectionHandler(child, path);
            }
        }
    }

    private String extractFileToken(Path path) {
        String name = path.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "FILE";
        }
        String extension = name.substring(dotIndex + 1).toUpperCase();
        return extension.length() > 4 ? extension.substring(0, 4) : extension;
    }

    private String buildFileMeta(Path path) {
        String sizeText = "SIZE N/A";
        try {
            sizeText = formatSize(Files.size(path)).toUpperCase();
        } catch (IOException ignored) {
        }
        return readModifiedTimestamp(path).toUpperCase() + "  •  " + sizeText;
    }

    private long calculateTotalSize() {
        long totalSize = 0;
        for (int index = 0; index < fileQueueModel.getSize(); index++) {
            Path file = fileQueueModel.getElementAt(index);
            try {
                totalSize += Files.size(file);
            } catch (IOException ignored) {
            }
        }
        return totalSize;
    }

    private String readModifiedTimestamp(Path path) {
        try {
            return Files.getLastModifiedTime(path).toString().replace('T', ' ').replace("Z", "");
        } catch (IOException ex) {
            return "timestamp n/a";
        }
    }

    private void refreshCertificateInfoState() {
        SigningIdentity identity = (SigningIdentity) identityCombo.getSelectedItem();
        certificateInfoIcon.setEnabled(!busy && identity != null);
        certificateInfoIcon.setToolTipText(identity == null
                ? "Nessun certificato selezionato"
                : "Visualizza il dettaglio del certificato selezionato");
    }

    private void updateActionState() {
        boolean hasFiles = fileQueueModel.getSize() > 0;
        boolean hasCertificate = identityCombo.getItemCount() > 0 && identityCombo.getSelectedItem() != null;

        identityCombo.setEnabled(!busy);
        signingModeCombo.setEnabled(!busy);
        outputField.setEnabled(!busy);
        certificateInfoIcon.setEnabled(!busy && hasCertificate);
        loadCertificatesButton.setEnabled(!busy && !loadingCertificates);
        browseFilesButton.setEnabled(!busy);
        browseOutputButton.setEnabled(!busy);
        verifyButton.setEnabled(!busy);
        fileQueueCombo.setEnabled(!busy && hasFiles);
        fileQueueCombo.setVisible(hasFiles);
        activeAssetLabel.setVisible(hasFiles);
        removeFileButton.setEnabled(!busy && hasFiles && fileQueueCombo.getSelectedItem() != null);
        clearFilesButton.setEnabled(!busy && hasFiles);
        boolean incompatible = getSelectedSigningMode() == SigningModeOption.PDF
                && getQueuedFiles().stream().anyMatch(file -> !isPdfFile(file));
        useP7mButton.setVisible(incompatible);
        useP7mButton.setEnabled(!busy);
        signButton.setEnabled(!busy && !loadingCertificates && hasFiles && hasCertificate && !incompatible);
        signButton.setText(hasFiles ? "Firma " + fileQueueModel.getSize() + " file" : "Firma file");
    }

    private void setBusy(boolean value, String statusMessage) {
        busy = value;
        progressBar.setVisible(value);
        progressBar.setIndeterminate(value);
        statusLabel.setText(statusMessage);
        updateActionState();
    }

    private void appendLog(String message) {
        logArea.append("[" + LocalTime.now().format(TIME_FORMAT) + "] " + message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showError(String message) {
        appendLog("Errore: " + message);
        JOptionPane.showMessageDialog(this, message, "Errore", JOptionPane.ERROR_MESSAGE);
    }

    private void showLogPopup() {
        JDialog dialog = new JDialog(this, "Log", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(APP_BACKGROUND);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel headerPanel = new JPanel(new BorderLayout(0, 6));
        headerPanel.setOpaque(false);
        headerPanel.add(createSectionTitle("Log"), BorderLayout.NORTH);
        headerPanel.add(createSectionSubtitle("Riepilogo dei messaggi di sistema e delle operazioni eseguite."), BorderLayout.SOUTH);

        JTextArea popupLogArea = new JTextArea(logArea.getText().isBlank() ? "Nessun log disponibile." : logArea.getText());
        popupLogArea.setEditable(false);
        popupLogArea.setLineWrap(true);
        popupLogArea.setWrapStyleWord(true);
        popupLogArea.setFont(logArea.getFont());
        popupLogArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        popupLogArea.setBackground(logArea.getBackground());
        popupLogArea.setForeground(TEXT_PRIMARY);
        popupLogArea.setCaretPosition(popupLogArea.getDocument().getLength());

        JScrollPane scrollPane = new JScrollPane(popupLogArea);
        stylePopupScrollPane(scrollPane, logArea.getBackground(), BorderFactory.createLineBorder(CARD_BORDER));
        scrollPane.setPreferredSize(new Dimension(760, 360));

        JButton closeButton = new JButton("Chiudi");
        styleButton(closeButton, false);
        closeButton.addActionListener(event -> dialog.dispose());

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footerPanel.setOpaque(false);
        footerPanel.add(closeButton);

        root.add(headerPanel, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(footerPanel, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private record OutputVerification(boolean verified, String detail) {}

    private OutputVerification checkOutput(Path output) {
        try {
            boolean valid = signatureService.verifySignature(output);
            return new OutputVerification(valid, valid ? "Verifica locale OK" : "Verifica locale fallita");
        } catch (Exception ex) {
            return new OutputVerification(false, "Verifica locale fallita: " + resolveMessage(ex));
        }
    }

    private record SigningFileUpdate(Path inputFile, Path outputFile, int completedCount, OutputVerification verification) {}

    private void revealOutput(Path output) {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                desktop.browseFileDirectory(output.toAbsolutePath().toFile());
            } else if (System.getProperty("os.name", "").startsWith("Windows")) {
                new ProcessBuilder("explorer.exe", "/select,", output.toAbsolutePath().toString()).start();
            } else {
                desktop.open(output.toAbsolutePath().getParent().toFile());
            }
        } catch (Exception ex) {
            showError("Impossibile aprire la cartella di destinazione: " + resolveMessage(ex));
        }
    }

    private record SignedFileVerificationResult(
            Path signedFile,
            SignatureService.SignedFileInspection inspection,
            boolean verified,
            String errorMessage
    ) {
    }

    private record PdfSigningPlan(
            List<SignatureService.PdfVisibleSignaturePlacement> placements
    ) {
    }

    private enum OverwriteDialogChoice {
        OVERWRITE,
        CANCEL,
        INSPECT,
        KEEP_BOTH,
        RENAME
    }

    private final class SigningProgressDialog {
        private final JDialog dialog;
        private final JLabel phaseBadge = new JLabel();
        private final JLabel titleLabel = new JLabel("Preparazione firma");
        private final JLabel subtitleLabel = new JLabel("Controllo certificato e configurazione in corso.");
        private final JProgressBar progress = new JProgressBar();
        private final JButton closeButton = new JButton("Chiudi");
        private final java.util.Map<Path, JLabel> statusLabels = new java.util.LinkedHashMap<>();
        private final java.util.Map<Path, JLabel> metaLabels = new java.util.LinkedHashMap<>();
        private final Map<Path, JButton> retryButtons = new LinkedHashMap<>();
        private final Map<Path, JButton> revealButtons = new LinkedHashMap<>();
        private final Map<Path, SigningFileUpdate> results = new LinkedHashMap<>();
        private String signingFailure;
        private final int totalFiles;

        private SigningProgressDialog(SigningIdentity identity, Path outputDirectory, List<Path> files, Map<Path, Path> outputTargets) {
            this.totalFiles = files.size();
            this.dialog = new JDialog(SignatureFrame.this, "Firma file", false);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.setResizable(false);

            JPanel root = new JPanel(new BorderLayout(0, 16));
            root.setBackground(APP_BACKGROUND);
            root.setBorder(new EmptyBorder(18, 18, 18, 18));

            JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
            headerPanel.setOpaque(false);
            headerPanel.add(createSectionTitle("Firma file"), BorderLayout.NORTH);

            JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            badgePanel.setOpaque(false);
            setBadgeState(phaseBadge, "IN ATTESA", new Color(241, 244, 246), TEXT_MUTED);
            badgePanel.add(phaseBadge);
            badgePanel.add(createInfoBadge(totalFiles == 1 ? "1 file" : totalFiles + " file"));
            headerPanel.add(badgePanel, BorderLayout.SOUTH);

            JPanel summaryPanel = createQueueSurface();
            summaryPanel.setLayout(new GridBagLayout());
            GridBagConstraints summaryConstraints = new GridBagConstraints();
            summaryConstraints.gridx = 0;
            summaryConstraints.gridy = 0;
            summaryConstraints.anchor = GridBagConstraints.WEST;
            summaryConstraints.insets = new Insets(0, 0, 12, 18);
            summaryPanel.add(createFieldLabel("Certificato"), summaryConstraints);

            summaryConstraints.gridx = 1;
            summaryConstraints.weightx = 1;
            summaryConstraints.fill = GridBagConstraints.HORIZONTAL;
            summaryConstraints.insets = new Insets(0, 0, 12, 0);
            summaryPanel.add(createSummaryValue(identity.displayName()), summaryConstraints);

            summaryConstraints.gridx = 0;
            summaryConstraints.gridy = 1;
            summaryConstraints.weightx = 0;
            summaryConstraints.fill = GridBagConstraints.NONE;
            summaryConstraints.insets = new Insets(0, 0, 0, 18);
            summaryPanel.add(createFieldLabel("Destinazione"), summaryConstraints);

            summaryConstraints.gridx = 1;
            summaryConstraints.weightx = 1;
            summaryConstraints.fill = GridBagConstraints.HORIZONTAL;
            summaryConstraints.insets = new Insets(0, 0, 0, 0);
            summaryPanel.add(createSummaryValue(outputDirectory.toString()), summaryConstraints);

            JPanel progressPanel = createQueueSurface();
            progressPanel.setLayout(new BorderLayout(0, 10));
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            titleLabel.setForeground(TEXT_PRIMARY);
            subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            subtitleLabel.setForeground(TEXT_MUTED);

            JPanel progressTextPanel = new JPanel(new BorderLayout(0, 4));
            progressTextPanel.setOpaque(false);
            progressTextPanel.add(titleLabel, BorderLayout.NORTH);
            progressTextPanel.add(subtitleLabel, BorderLayout.SOUTH);

            progress.setMinimum(0);
            progress.setMaximum(Math.max(1, totalFiles));
            progress.setValue(0);
            progress.setStringPainted(true);
            progress.setString("0 / " + totalFiles);

            progressPanel.add(progressTextPanel, BorderLayout.NORTH);
            progressPanel.add(progress, BorderLayout.SOUTH);

            JPanel fileListPanel = createQueueSurface();
            fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
            for (Path file : files) {
                fileListPanel.add(createSigningFileRow(file, outputTargets.get(file)));
                fileListPanel.add(Box.createVerticalStrut(10));
            }

            JScrollPane listScrollPane = new JScrollPane(fileListPanel);
            stylePopupScrollPane(listScrollPane, APP_BACKGROUND, BorderFactory.createEmptyBorder());
            listScrollPane.setPreferredSize(new Dimension(610, Math.min(320, 98 + (files.size() * 92))));

            closeButton.setEnabled(false);
            styleButton(closeButton, false);
            closeButton.addActionListener(event -> dialog.dispose());

            JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            footerPanel.setOpaque(false);
            footerPanel.add(closeButton);

            JPanel centerStack = new JPanel();
            centerStack.setOpaque(false);
            centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
            centerStack.add(summaryPanel);
            centerStack.add(Box.createVerticalStrut(12));
            centerStack.add(progressPanel);
            centerStack.add(Box.createVerticalStrut(12));
            centerStack.add(listScrollPane);

            root.add(headerPanel, BorderLayout.NORTH);
            root.add(centerStack, BorderLayout.CENTER);
            root.add(footerPanel, BorderLayout.SOUTH);

            dialog.setContentPane(root);
        }

        private JPanel createSigningFileRow(Path file, Path outputFile) {
            JPanel row = createQueueSurface();
            row.setLayout(new BorderLayout(14, 0));
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(223, 227, 231)),
                    new EmptyBorder(14, 14, 14, 14)
            ));

            JPanel tokenPanel = new JPanel(new BorderLayout());
            tokenPanel.setOpaque(true);
            tokenPanel.setBackground(CARD_BACKGROUND);
            tokenPanel.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
            tokenPanel.setPreferredSize(new Dimension(44, 44));

            JLabel tokenLabel = new JLabel(extractFileToken(file), SwingConstants.CENTER);
            tokenLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            tokenLabel.setForeground(ACCENT);
            tokenPanel.add(tokenLabel, BorderLayout.CENTER);

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

            JLabel nameLabel = new JLabel(file.getFileName().toString());
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            nameLabel.setForeground(TEXT_PRIMARY);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel metaLabel = new JLabel("Output: " + outputFile);
            metaLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            metaLabel.setForeground(TEXT_MUTED);
            metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            metaLabel.setToolTipText(metaLabel.getText());
            metaLabels.put(file, metaLabel);

            textPanel.add(nameLabel);
            textPanel.add(Box.createVerticalStrut(4));
            textPanel.add(metaLabel);

            JLabel statusBadge = new JLabel();
            statusBadge.setHorizontalAlignment(SwingConstants.CENTER);
            setBadgeState(statusBadge, "IN CODA", new Color(241, 244, 246), TEXT_MUTED);
            statusLabels.put(file, statusBadge);

            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            rightPanel.setOpaque(false);
            JButton retry = new JButton("Verifica di nuovo");
            JButton reveal = new JButton("Apri cartella di destinazione");
            retryButtons.put(file, retry);
            revealButtons.put(file, reveal);
            retry.setVisible(false);
            reveal.setVisible(false);
            retry.addActionListener(event -> retryVerification(file));
            reveal.addActionListener(event -> revealOutput(results.get(file).outputFile()));
            rightPanel.setLayout(new javax.swing.BoxLayout(rightPanel, javax.swing.BoxLayout.Y_AXIS));
            rightPanel.add(statusBadge);
            rightPanel.add(retry);
            rightPanel.add(reveal);

            row.add(tokenPanel, BorderLayout.WEST);
            row.add(textPanel, BorderLayout.CENTER);
            row.add(rightPanel, BorderLayout.EAST);
            return row;
        }

        private void open() {
            dialog.pack();
            Dimension preferredSize = dialog.getPreferredSize();
            Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int height = Math.min(preferredSize.height, Math.max(420, bounds.height - 80));
            dialog.setSize(new Dimension(755, height));
            dialog.setMinimumSize(new Dimension(755, height));
            dialog.setLocationRelativeTo(SignatureFrame.this);
            dialog.setVisible(true);
        }

        private void markProcessing(Path file, int completedCount) {
            titleLabel.setText("Firma in corso");
            subtitleLabel.setText("Sto elaborando " + file.getFileName());
            setBadgeState(phaseBadge, "IN CORSO", new Color(231, 243, 238), new Color(18, 97, 79));
            progress.setValue(completedCount);
            progress.setString(completedCount + " / " + totalFiles);

            JLabel statusBadge = statusLabels.get(file);
            if (statusBadge != null) {
                setBadgeState(statusBadge, "IN FIRMA", ACCENT_SOFT, new Color(0, 65, 69));
            }
        }

        private void markCompleted(SigningFileUpdate update) {
            results.put(update.inputFile(), update);
            progress.setValue(update.completedCount());
            progress.setString(update.completedCount() + " / " + totalFiles);
            displayVerification(update);
        }

        private void displayVerification(SigningFileUpdate update) {
            Path file = update.inputFile();
            boolean verified = update.verification().verified();
            setBadgeState(statusLabels.get(file), verified ? "VERIFICATO" : "VERIFICA FALLITA",
                    verified ? ACCENT_SOFT : CERTIFICATE_BADGE_EMPTY, verified ? TEXT_PRIMARY : Color.WHITE);
            String detail = update.outputFile() + " | " + update.verification().detail();
            metaLabels.get(file).setText(detail);
            metaLabels.get(file).setToolTipText(detail);
        }

        private void finish(List<SigningFileUpdate> created, Path failedFile, String failure) {
            signingFailure = failure;
            for (SigningFileUpdate update : created) {
                markCompleted(update);
                retryButtons.get(update.inputFile()).setVisible(!update.verification().verified());
                revealButtons.get(update.inputFile()).setVisible(true);
            }
            if (failure != null) {
                for (Path file : statusLabels.keySet()) {
                    if (results.containsKey(file)) continue;
                    boolean failed = file.equals(failedFile);
                    setBadgeState(statusLabels.get(file), failed ? "FIRMA FALLITA" : "NON ELABORATO",
                            failed ? CERTIFICATE_BADGE_EMPTY : CARD_BACKGROUND, failed ? Color.WHITE : TEXT_PRIMARY);
                    if (failed) {
                        metaLabels.get(file).setText(failure);
                        metaLabels.get(file).setToolTipText(failure);
                    }
                }
                appendLog("Firma interrotta su " + failedFile + ": " + failure);
            }
            updateSummary();
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            closeButton.setEnabled(true);
        }

        private void updateSummary() {
            long verified = results.values().stream().filter(item -> item.verification().verified()).count();
            long failed = results.size() - verified;
            String summary = results.size() + (results.size() == 1 ? " file creato. " : " file creati. ")
                    + verified + (verified == 1 ? " verificato" : " verificati") + ", " + failed + " con verifica fallita.";
            if (signingFailure != null) summary += " Da firmare: " + (totalFiles - results.size()) + ".";
            subtitleLabel.setText(summary);
            boolean success = signingFailure == null && failed == 0;
            titleLabel.setText(signingFailure != null ? "Firma interrotta" : failed > 0 ? "Controlla i file evidenziati" : "Firma completata");
            setBadgeState(phaseBadge, success ? "COMPLETATO" : "ATTENZIONE",
                    success ? ACCENT_SOFT : CERTIFICATE_BADGE_EMPTY, success ? TEXT_PRIMARY : Color.WHITE);
        }

        private void retryVerification(Path file) {
            SigningFileUpdate original = results.get(file);
            JButton retry = retryButtons.get(file);
            retry.setEnabled(false);
            new SwingWorker<OutputVerification, Void>() {
                @Override
                protected OutputVerification doInBackground() {
                    return checkOutput(original.outputFile());
                }
                @Override
                protected void done() {
                    try {
                        OutputVerification verification = get();
                        SigningFileUpdate update = new SigningFileUpdate(file, original.outputFile(), original.completedCount(), verification);
                        results.put(file, update);
                        displayVerification(update);
                        retry.setVisible(!verification.verified());
                        appendLog(original.outputFile() + " | " + verification.detail());
                        updateSummary();
                    } catch (Exception ex) {
                        showError(resolveMessage(ex));
                    } finally {
                        retry.setEnabled(true);
                    }
                }
            }.execute();
        }

        private JLabel createSummaryValue(String text) {
            JLabel label = new JLabel(text);
            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            label.setForeground(TEXT_PRIMARY);
            label.setToolTipText(text);
            return label;
        }

        private void setBadgeState(JLabel label, String text, Color background, Color foreground) {
            label.setText(text);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            styleBadge(label, background, foreground);
        }
    }

    private final class PdfSigningDialog {
        private static final float PDF_ZOOM_FACTOR_MIN = 0.55f;
        private static final float PDF_ZOOM_FACTOR_MAX = 2.8f;
        private static final float PDF_ZOOM_FACTOR_STEP = 1.18f;

        private final SigningIdentity identity;
        private final Path outputDirectory;
        private final List<Path> files;
        private final JDialog dialog;
        private final JTabbedPane documentTabs = new JTabbedPane();
        private final JButton previousPageButton = new JButton("Precedente");
        private final JButton nextPageButton = new JButton("Successiva");
        private final JButton zoomOutButton = new JButton("-");
        private final JButton zoomInButton = new JButton("+");
        private final JButton fitWidthButton = new JButton("Adatta larghezza");
        private final JButton confirmPageButton = new JButton("Conferma posizione su questa pagina");
        private final JButton signAllPagesButton = new JButton("Applica a tutte le pagine");
        private final JButton undoButton = new JButton("Annulla ultima modifica");
        private final JButton goToPageButton = new JButton("Vai a pagina…");
        private final Map<Path, Runnable> undoByDocument = new LinkedHashMap<>();
        private final JButton clearCurrentPageButton = new JButton("Rimuovi ultima firma");
        private final JButton resetPlacementButton = new JButton("Ripristina posizione");
        private final JButton cancelButton = new JButton("Annulla");
        private final JButton startButton = new JButton("Avvia firma");
        private final JLabel currentPageLabel = new JLabel();
        private final JLabel signedPagesLabel = new JLabel("Firme: 0");
        private final JLabel zoomValueLabel = new JLabel("100%");
        private final PdfPreviewSurface previewSurface = new PdfPreviewSurface();
        private final JPanel previewWrapper = new JPanel(new GridBagLayout());
        private final JScrollPane previewScrollPane = new JScrollPane(previewWrapper);
        private final Map<Path, PdfDocumentContext> documentContexts = new LinkedHashMap<>();
        private final Map<Path, Integer> currentPageIndexes = new LinkedHashMap<>();
        private final Map<Path, Map<Integer, SignatureService.PdfVisibleSignaturePlacement>> previewPlacements = new LinkedHashMap<>();
        private final Map<Path, Map<Integer, List<SignatureService.PdfVisibleSignaturePlacement>>> confirmedPlacements = new LinkedHashMap<>();
        private final Map<Path, Float> zoomFactors = new LinkedHashMap<>();
        private PageRenderContext currentRenderContext;
        private Path currentFile;
        private boolean syncingState;
        private int lastViewportWidth;

        private PdfSigningDialog(SigningIdentity identity, Path outputDirectory, List<Path> files) throws Exception {
            this.identity = identity;
            this.outputDirectory = outputDirectory;
            this.files = List.copyOf(files);
            this.dialog = new JDialog(SignatureFrame.this, "Firma PDF", true);

            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setResizable(true);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    closeDocumentContexts();
                }
            });

            initializeDocumentContexts();

            styleButton(previousPageButton, false);
            styleButton(nextPageButton, false);
            styleButton(zoomOutButton, false);
            styleButton(zoomInButton, false);
            styleButton(fitWidthButton, false);
            styleButton(confirmPageButton, true);
            styleButton(undoButton, false);
            styleButton(goToPageButton, false);
            styleButton(signAllPagesButton, false);
            styleButton(clearCurrentPageButton, false);
            styleButton(resetPlacementButton, false);
            styleButton(cancelButton, false);
            styleButton(startButton, true);

            zoomOutButton.setPreferredSize(new Dimension(46, zoomOutButton.getPreferredSize().height));
            zoomInButton.setPreferredSize(new Dimension(46, zoomInButton.getPreferredSize().height));
            currentPageLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            currentPageLabel.setForeground(TEXT_PRIMARY);
            zoomValueLabel.setHorizontalAlignment(SwingConstants.CENTER);
            styleBadge(zoomValueLabel, new Color(241, 244, 246), TEXT_MUTED);
            styleBadge(signedPagesLabel, new Color(241, 244, 246), TEXT_MUTED);

            documentTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
            documentTabs.setBackground(HEADER_SURFACE);
            documentTabs.setForeground(TEXT_PRIMARY);
            documentTabs.setOpaque(false);
            documentTabs.setFocusable(true);
            documentTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

            previousPageButton.addActionListener(event -> shiftPage(-1));
            nextPageButton.addActionListener(event -> shiftPage(1));
            zoomOutButton.addActionListener(event -> adjustZoom(false));
            zoomInButton.addActionListener(event -> adjustZoom(true));
            fitWidthButton.addActionListener(event -> resetZoomToFitWidth());
            confirmPageButton.addActionListener(event -> confirmPlacementOnCurrentPage(null));
            undoButton.addActionListener(event -> {
                Runnable undo = undoByDocument.remove(currentFile);
                if (undo != null) {
                    undo.run();
                    refreshCurrentPreview();
                }
            });
            goToPageButton.addActionListener(event -> {
                int page = currentPageIndexes.get(currentFile) + 1;
                int total = documentContexts.get(currentFile).getPageCount();
                javax.swing.JSpinner spinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(page, 1, total, 1));
                if (JOptionPane.showConfirmDialog(dialog, spinner, "Vai a pagina (1–" + total + ")", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                    try {
                        spinner.commitEdit();
                        shiftPage((Integer) spinner.getValue() - page);
                    } catch (java.text.ParseException ex) {
                        JOptionPane.showMessageDialog(dialog, "Inserisci un numero da 1 a " + total + ".");
                    }
                }
            });
            signAllPagesButton.addActionListener(event -> confirmPlacementOnEveryPage());
            clearCurrentPageButton.addActionListener(event -> clearConfirmedPlacementOnCurrentPage());
            resetPlacementButton.addActionListener(event -> resetPlacement());
            cancelButton.addActionListener(event -> dialog.dispose());
            startButton.addActionListener(event -> confirmAndStartSigning());
            documentTabs.addChangeListener(event -> {
                if (syncingState) {
                    return;
                }
                int selectedIndex = documentTabs.getSelectedIndex();
                if (selectedIndex >= 0 && selectedIndex < files.size()) {
                    selectDocument(files.get(selectedIndex));
                }
            });
            previewScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    int viewportWidth = previewScrollPane.getViewport().getExtentSize().width;
                    if (viewportWidth <= 0 || viewportWidth == lastViewportWidth) {
                        return;
                    }
                    lastViewportWidth = viewportWidth;
                    if (currentFile != null) {
                        refreshCurrentPreview();
                    }
                }
            });

            previewWrapper.setOpaque(false);
            previewWrapper.setBorder(new EmptyBorder(18, 18, 18, 18));
            previewWrapper.add(previewSurface);

            stylePopupScrollPane(
                    previewScrollPane,
                    new Color(250, 252, 252),
                    BorderFactory.createLineBorder(CARD_BORDER)
            );
            previewScrollPane.setPreferredSize(new Dimension(760, 620));
            previewScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            previewScrollPane.getHorizontalScrollBar().setUnitIncrement(18);
            previewScrollPane.getHorizontalScrollBar().setOpaque(false);
            previewScrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 16));
            previewScrollPane.getHorizontalScrollBar().setUI(new AppScrollBarUI());

            JPanel root = new JPanel(new BorderLayout(0, 16));
            root.setBackground(APP_BACKGROUND);
            root.setBorder(new EmptyBorder(18, 18, 18, 18));

            JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
            headerPanel.setOpaque(false);
            headerPanel.add(createSectionTitle("Firma PDF"), BorderLayout.NORTH);
            headerPanel.add(
                    createSectionSubtitle("Posiziona il riquadro e confermalo su ciascuna pagina da firmare."),
                    BorderLayout.SOUTH
            );

            JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            badgePanel.setOpaque(false);
            badgePanel.add(createInfoBadge(this.files.size() == 1 ? "1 PDF" : this.files.size() + " PDF"));
            badgePanel.add(createInfoBadge("PAdES"));

            JPanel leftToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            leftToolbar.setOpaque(false);
            leftToolbar.add(previousPageButton);
            leftToolbar.add(currentPageLabel);
            leftToolbar.add(nextPageButton);
            leftToolbar.add(goToPageButton);

            JPanel positionToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            positionToolbar.setOpaque(false);
            positionToolbar.add(confirmPageButton);
            positionToolbar.add(signAllPagesButton);
            positionToolbar.add(undoButton);

            JPanel editToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            editToolbar.setOpaque(false);
            editToolbar.add(zoomOutButton);
            editToolbar.add(zoomValueLabel);
            editToolbar.add(zoomInButton);
            editToolbar.add(fitWidthButton);
            editToolbar.add(resetPlacementButton);
            editToolbar.add(clearCurrentPageButton);

            JPanel toolbarPanel = new JPanel(new java.awt.GridLayout(0, 1, 0, 3));
            toolbarPanel.setOpaque(false);
            toolbarPanel.add(leftToolbar);
            toolbarPanel.add(positionToolbar);
            toolbarPanel.add(editToolbar);
            toolbarPanel.add(signedPagesLabel);

            JPanel viewerPanel = createQueueSurface();
            viewerPanel.setLayout(new BorderLayout(0, 14));

            JPanel viewerHeader = new JPanel(new BorderLayout(0, 12));
            viewerHeader.setOpaque(false);
            viewerHeader.add(documentTabs, BorderLayout.NORTH);
            viewerHeader.add(toolbarPanel, BorderLayout.SOUTH);

            viewerPanel.add(viewerHeader, BorderLayout.NORTH);
            viewerPanel.add(previewScrollPane, BorderLayout.CENTER);

            JPanel footerPanel = new JPanel(new BorderLayout());
            footerPanel.setOpaque(false);
            footerPanel.add(badgePanel, BorderLayout.WEST);

            JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            footerActions.setOpaque(false);
            footerActions.add(cancelButton);
            footerActions.add(startButton);
            footerPanel.add(footerActions, BorderLayout.EAST);

            root.add(headerPanel, BorderLayout.NORTH);
            root.add(viewerPanel, BorderLayout.CENTER);
            root.add(footerPanel, BorderLayout.SOUTH);

            dialog.setContentPane(root);
            if (!this.files.isEmpty()) {
                syncingState = true;
                documentTabs.setSelectedIndex(0);
                syncingState = false;
                selectDocument(this.files.get(0));
            }
        }

        private void open() {
            dialog.pack();
            java.awt.GraphicsConfiguration display = SignatureFrame.this.getGraphicsConfiguration();
            Rectangle bounds = display.getBounds();
            Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(display);
            int width = bounds.width - insets.left - insets.right;
            int height = bounds.height - insets.top - insets.bottom;
            dialog.setMinimumSize(new Dimension(Math.min(650, width), Math.min(450, height)));
            dialog.setSize(new Dimension(Math.min(1080, width), Math.min(860, height)));
            dialog.setLocationRelativeTo(SignatureFrame.this);
            SwingUtilities.invokeLater(this::refreshCurrentPreview);
            dialog.setVisible(true);
        }

        private void initializeDocumentContexts() throws Exception {
            try {
                for (Path file : files) {
                    PdfDocumentContext context = new PdfDocumentContext(file, Loader.loadPDF(file.toFile()));
                    documentContexts.put(file, context);
                    currentPageIndexes.put(file, 0);
                    Map<Integer, SignatureService.PdfVisibleSignaturePlacement> previewByPage = new LinkedHashMap<>();
                    previewByPage.put(0, createDefaultPlacement(context, 0));
                    previewPlacements.put(file, previewByPage);
                    confirmedPlacements.put(file, new LinkedHashMap<>());
                    zoomFactors.put(file, 1f);
                    documentTabs.addTab(file.getFileName().toString(), createTabPlaceholder());
                    documentTabs.setToolTipTextAt(documentTabs.getTabCount() - 1, file.toAbsolutePath().toString());
                }
            } catch (Exception ex) {
                closeDocumentContexts();
                throw ex;
            }
        }

        private JPanel createTabPlaceholder() {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setPreferredSize(new Dimension(0, 0));
            panel.setMinimumSize(new Dimension(0, 0));
            panel.setMaximumSize(new Dimension(0, 0));
            return panel;
        }

        private void closeDocumentContexts() {
            for (PdfDocumentContext context : documentContexts.values()) {
                try {
                    context.close();
                } catch (IOException ignored) {
                    // Un eventuale errore in chiusura non deve bloccare il rilascio del popup.
                }
            }
            documentContexts.clear();
        }

        private void selectDocument(Path file) {
            if (file == null) {
                return;
            }

            currentFile = file;
            refreshCurrentPreview();
        }

        private void shiftPage(int delta) {
            if (currentFile == null) {
                return;
            }

            PdfDocumentContext context = documentContexts.get(currentFile);
            int currentPageIndex = currentPageIndexes.getOrDefault(currentFile, 0);
            int nextPageIndex = Math.max(0, Math.min(context.getPageCount() - 1, currentPageIndex + delta));
            if (nextPageIndex == currentPageIndex) {
                return;
            }

            currentPageIndexes.put(currentFile, nextPageIndex);
            ensurePreviewPlacement(context, currentFile, nextPageIndex);
            refreshCurrentPreview();
        }

        private void adjustZoom(boolean zoomIn) {
            if (currentFile == null) {
                return;
            }

            float currentZoom = zoomFactors.getOrDefault(currentFile, 1f);
            float nextZoom = zoomIn
                    ? Math.min(PDF_ZOOM_FACTOR_MAX, currentZoom * PDF_ZOOM_FACTOR_STEP)
                    : Math.max(PDF_ZOOM_FACTOR_MIN, currentZoom / PDF_ZOOM_FACTOR_STEP);
            if (Math.abs(nextZoom - currentZoom) < 0.001f) {
                return;
            }

            zoomFactors.put(currentFile, nextZoom);
            refreshCurrentPreview();
        }

        private void resetZoomToFitWidth() {
            if (currentFile == null) {
                return;
            }

            zoomFactors.put(currentFile, 1f);
            refreshCurrentPreview();
        }

        private void resetPlacement() {
            if (currentFile == null) {
                return;
            }

            PdfDocumentContext context = documentContexts.get(currentFile);
            int pageIndex = currentPageIndexes.getOrDefault(currentFile, 0);
            rememberPlacements();
            previewPlacements.get(currentFile).put(pageIndex, createDefaultPlacement(context, pageIndex));
            refreshCurrentPreview();
        }

        private SignatureService.PdfVisibleSignaturePlacement createDefaultPlacement(PdfDocumentContext context, int pageIndex) {
            PDRectangle pageBox = context.getPageBox(pageIndex);
            float maxWidth = Math.max(90f, pageBox.getWidth() - (PDF_SIGNATURE_MARGIN * 2f));
            float maxHeight = Math.max(42f, pageBox.getHeight() - (PDF_SIGNATURE_MARGIN * 2f));
            float width = Math.min(PDF_SIGNATURE_DEFAULT_WIDTH, maxWidth);
            float height = Math.min(PDF_SIGNATURE_DEFAULT_HEIGHT, maxHeight);
            float x = Math.max(PDF_SIGNATURE_MARGIN, pageBox.getWidth() - width - PDF_SIGNATURE_MARGIN);
            float y = Math.max(PDF_SIGNATURE_MARGIN, pageBox.getHeight() - height - PDF_SIGNATURE_MARGIN);
            return new SignatureService.PdfVisibleSignaturePlacement(pageIndex, x, y, width, height);
        }

        private SignatureService.PdfVisibleSignaturePlacement remapPlacement(
                PdfDocumentContext context,
                SignatureService.PdfVisibleSignaturePlacement placement,
                int targetPageIndex
        ) {
            PDRectangle sourcePageBox = context.getPageBox(placement.pageIndex());
            PDRectangle targetPageBox = context.getPageBox(targetPageIndex);

            float widthRatio = placement.width() / sourcePageBox.getWidth();
            float heightRatio = placement.height() / sourcePageBox.getHeight();
            float xRatio = placement.x() / sourcePageBox.getWidth();
            float yRatio = placement.y() / sourcePageBox.getHeight();

            float width = Math.max(72f, Math.min(targetPageBox.getWidth() - (PDF_SIGNATURE_MARGIN * 2f), targetPageBox.getWidth() * widthRatio));
            float height = Math.max(36f, Math.min(targetPageBox.getHeight() - (PDF_SIGNATURE_MARGIN * 2f), targetPageBox.getHeight() * heightRatio));
            float x = Math.max(PDF_SIGNATURE_MARGIN,
                    Math.min(targetPageBox.getWidth() - width - PDF_SIGNATURE_MARGIN, targetPageBox.getWidth() * xRatio));
            float y = Math.max(PDF_SIGNATURE_MARGIN,
                    Math.min(targetPageBox.getHeight() - height - PDF_SIGNATURE_MARGIN, targetPageBox.getHeight() * yRatio));
            return new SignatureService.PdfVisibleSignaturePlacement(targetPageIndex, x, y, width, height);
        }

        private SignatureService.PdfVisibleSignaturePlacement ensurePreviewPlacement(
                PdfDocumentContext context,
                Path file,
                int pageIndex
        ) {
            Map<Integer, SignatureService.PdfVisibleSignaturePlacement> previewByPage = previewPlacements.get(file);
            SignatureService.PdfVisibleSignaturePlacement previewPlacement = previewByPage.get(pageIndex);
            if (previewPlacement != null) {
                return previewPlacement;
            }

            List<SignatureService.PdfVisibleSignaturePlacement> confirmedOnPage = confirmedPlacements.get(file).get(pageIndex);
            if (confirmedOnPage != null && !confirmedOnPage.isEmpty()) {
                SignatureService.PdfVisibleSignaturePlacement lastConfirmedPlacement = confirmedOnPage.get(confirmedOnPage.size() - 1);
                previewByPage.put(pageIndex, lastConfirmedPlacement);
                return lastConfirmedPlacement;
            }

            SignatureService.PdfVisibleSignaturePlacement anchorPlacement = findAnchorPlacement(file, pageIndex);
            previewPlacement = anchorPlacement == null
                    ? createDefaultPlacement(context, pageIndex)
                    : remapPlacement(context, anchorPlacement, pageIndex);
            previewByPage.put(pageIndex, previewPlacement);
            return previewPlacement;
        }

        private SignatureService.PdfVisibleSignaturePlacement findAnchorPlacement(Path file, int pageIndex) {
            SignatureService.PdfVisibleSignaturePlacement bestPlacement = null;
            int bestDistance = Integer.MAX_VALUE;

            for (List<SignatureService.PdfVisibleSignaturePlacement> placements : confirmedPlacements.get(file).values()) {
                for (SignatureService.PdfVisibleSignaturePlacement placement : placements) {
                    int distance = Math.abs(placement.pageIndex() - pageIndex);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPlacement = placement;
                    }
                }
            }
            for (SignatureService.PdfVisibleSignaturePlacement placement : previewPlacements.get(file).values()) {
                int distance = Math.abs(placement.pageIndex() - pageIndex);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestPlacement = placement;
                }
            }
            return bestPlacement;
        }

        private void refreshCurrentPreview() {
            if (currentFile == null || !documentContexts.containsKey(currentFile)) {
                return;
            }

            PdfDocumentContext context = documentContexts.get(currentFile);
            int currentPageIndex = currentPageIndexes.getOrDefault(currentFile, 0);
            currentRenderContext = context.renderPage(currentPageIndex, resolvePreviewRenderScale(context, currentPageIndex));
            SignatureService.PdfVisibleSignaturePlacement previewPlacement = ensurePreviewPlacement(context, currentFile, currentPageIndex);
            List<SignatureService.PdfVisibleSignaturePlacement> confirmedPlacementsOnPage =
                    confirmedPlacements.get(currentFile).getOrDefault(currentPageIndex, List.of());
            List<Rectangle> confirmedRectangles = new ArrayList<>();
            for (SignatureService.PdfVisibleSignaturePlacement placement : confirmedPlacementsOnPage) {
                confirmedRectangles.add(convertPlacementToDisplayRectangle(placement, currentRenderContext));
            }
            previewSurface.setPreview(
                    currentRenderContext.image(),
                    confirmedRectangles,
                    convertPlacementToDisplayRectangle(previewPlacement, currentRenderContext)
            );
            previewSurface.revalidate();

            currentPageLabel.setText("Pagina " + (currentPageIndex + 1) + " / " + context.getPageCount());
            previousPageButton.setEnabled(currentPageIndex > 0);
            nextPageButton.setEnabled(currentPageIndex < context.getPageCount() - 1);
            clearCurrentPageButton.setEnabled(!confirmedPlacementsOnPage.isEmpty());
            signAllPagesButton.setEnabled(context.getPageCount() > 1);
            updateSignedPagesBadge();

            float zoomFactor = zoomFactors.getOrDefault(currentFile, 1f);
            zoomValueLabel.setText(Math.round(zoomFactor * 100f) + "%");
            zoomOutButton.setEnabled(zoomFactor > PDF_ZOOM_FACTOR_MIN + 0.01f);
            zoomInButton.setEnabled(zoomFactor < PDF_ZOOM_FACTOR_MAX - 0.01f);
            fitWidthButton.setEnabled(Math.abs(zoomFactor - 1f) > 0.01f);
        }

        private void updateSignedPagesBadge() {
            List<Integer> pages = confirmedPlacements.get(currentFile).keySet().stream().sorted().toList();
            String pageNumbers = pages.stream().map(index -> Integer.toString(index + 1))
                    .collect(java.util.stream.Collectors.joining(", "));
            String summary = pages.isEmpty() ? "Nessuna posizione confermata"
                    : "Posizioni confermate " + (pages.size() == 1 ? "a pagina " : "sulle pagine ") + pageNumbers;
            applySignedPagesBadge(summary, pages.isEmpty() ? CARD_BACKGROUND : ACCENT_SOFT, TEXT_PRIMARY);
            signedPagesLabel.setToolTipText(summary);
            undoButton.setEnabled(undoByDocument.containsKey(currentFile));
        }

        private void rememberPlacements() {
            Path file = currentFile;
            var preview = new LinkedHashMap<>(previewPlacements.get(file));
            Map<Integer, List<SignatureService.PdfVisibleSignaturePlacement>> confirmed = new LinkedHashMap<>();
            confirmedPlacements.get(file).forEach((page, marks) -> confirmed.put(page, new ArrayList<>(marks)));
            undoByDocument.put(file, () -> {
                previewPlacements.put(file, preview);
                confirmedPlacements.put(file, confirmed);
            });
            undoButton.setEnabled(true);
        }

        private void applySignedPagesBadge(String text, Color background, Color foreground) {
            signedPagesLabel.setText(text);
            signedPagesLabel.setHorizontalAlignment(SwingConstants.CENTER);
            styleBadge(signedPagesLabel, background, foreground);
        }

        private float resolvePreviewRenderScale(PdfDocumentContext context, int pageIndex) {
            PDRectangle pageBox = context.getPageBox(pageIndex);
            int viewportWidth = previewScrollPane.getViewport().getExtentSize().width;
            if (viewportWidth <= 0) {
                viewportWidth = previewScrollPane.getPreferredSize().width;
            }

            int usableWidth = Math.max(1, viewportWidth - 48);
            float fitWidthScale = usableWidth / pageBox.getWidth();
            float zoomFactor = zoomFactors.getOrDefault(currentFile, 1f);
            float renderScale = fitWidthScale * zoomFactor;
            return Math.max(0.01f, Math.min(renderScale, 4.5f));
        }

        private Rectangle convertPlacementToDisplayRectangle(
                SignatureService.PdfVisibleSignaturePlacement placement,
                PageRenderContext renderContext
        ) {
            double scaleX = renderContext.image().getWidth() / renderContext.pageWidth();
            double scaleY = renderContext.image().getHeight() / renderContext.pageHeight();

            int x = (int) Math.round(placement.x() * scaleX);
            int y = (int) Math.round(placement.y() * scaleY);
            int width = Math.max(1, (int) Math.round(placement.width() * scaleX));
            int height = Math.max(1, (int) Math.round(placement.height() * scaleY));
            return new Rectangle(x, y, width, height);
        }

        private void updateCurrentPlacementFromDisplayRectangle(Rectangle displayRectangle) {
            if (currentFile == null || currentRenderContext == null || displayRectangle == null) {
                return;
            }

            Rectangle boundedRectangle = clampRectangleToImage(displayRectangle, currentRenderContext.image());
            double scaleX = currentRenderContext.pageWidth() / currentRenderContext.image().getWidth();
            double scaleY = currentRenderContext.pageHeight() / currentRenderContext.image().getHeight();

            float x = (float) (boundedRectangle.x * scaleX);
            float y = (float) (boundedRectangle.y * scaleY);
            float width = (float) Math.max(1d, boundedRectangle.width * scaleX);
            float height = (float) Math.max(1d, boundedRectangle.height * scaleY);
            int pageIndex = currentPageIndexes.getOrDefault(currentFile, currentRenderContext.pageIndex());

            previewPlacements.get(currentFile).put(pageIndex, new SignatureService.PdfVisibleSignaturePlacement(
                    pageIndex,
                    x,
                    y,
                    width,
                    height
            ));
        }

        private void confirmPlacementOnCurrentPage(Point clickPoint) {
            if (currentFile == null || currentRenderContext == null || currentRenderContext.image() == null) {
                return;
            }

            int pageIndex = currentPageIndexes.getOrDefault(currentFile, currentRenderContext.pageIndex());
            SignatureService.PdfVisibleSignaturePlacement previewPlacement = previewPlacements.get(currentFile).get(pageIndex);
            if (previewPlacement == null) {
                previewPlacement = ensurePreviewPlacement(documentContexts.get(currentFile), currentFile, pageIndex);
            }

            rememberPlacements();
            if (clickPoint != null) {
                Rectangle displayRectangle = convertPlacementToDisplayRectangle(previewPlacement, currentRenderContext);
                Rectangle centeredRectangle = clampRectangleToImage(
                        new Rectangle(clickPoint.x - displayRectangle.width / 2, clickPoint.y - displayRectangle.height / 2,
                                displayRectangle.width, displayRectangle.height),
                        currentRenderContext.image()
                );
                updateCurrentPlacementFromDisplayRectangle(centeredRectangle);
            }
            SignatureService.PdfVisibleSignaturePlacement confirmedPlacement = previewPlacements.get(currentFile).get(pageIndex);
            confirmedPlacements.get(currentFile)
                    .computeIfAbsent(pageIndex, ignored -> new ArrayList<>())
                    .add(confirmedPlacement);
            refreshCurrentPreview();
        }

        private void clearConfirmedPlacementOnCurrentPage() {
            if (currentFile == null) {
                return;
            }

            int pageIndex = currentPageIndexes.getOrDefault(currentFile, 0);
            List<SignatureService.PdfVisibleSignaturePlacement> confirmedOnPage = confirmedPlacements.get(currentFile).get(pageIndex);
            if (confirmedOnPage == null || confirmedOnPage.isEmpty()) {
                return;
            }

            rememberPlacements();
            SignatureService.PdfVisibleSignaturePlacement removedPlacement =
                    confirmedOnPage.remove(confirmedOnPage.size() - 1);
            previewPlacements.get(currentFile).put(pageIndex, removedPlacement);
            if (confirmedOnPage.isEmpty()) {
                confirmedPlacements.get(currentFile).remove(pageIndex);
            }
            refreshCurrentPreview();
        }

        private void confirmPlacementOnEveryPage() {
            if (currentFile == null) {
                return;
            }

            PdfDocumentContext context = documentContexts.get(currentFile);
            int anchorPageIndex = currentPageIndexes.getOrDefault(currentFile, 0);
            SignatureService.PdfVisibleSignaturePlacement anchorPlacement = ensurePreviewPlacement(context, currentFile, anchorPageIndex);
            Map<Integer, SignatureService.PdfVisibleSignaturePlacement> previewByPage = previewPlacements.get(currentFile);
            Map<Integer, List<SignatureService.PdfVisibleSignaturePlacement>> confirmedByPage = confirmedPlacements.get(currentFile);

            rememberPlacements();
            confirmedByPage.clear();
            for (int pageIndex = 0; pageIndex < context.getPageCount(); pageIndex++) {
                SignatureService.PdfVisibleSignaturePlacement pagePlacement = pageIndex == anchorPageIndex
                        ? anchorPlacement
                        : remapPlacement(context, anchorPlacement, pageIndex);
                previewByPage.put(pageIndex, pagePlacement);
                confirmedByPage.put(pageIndex, new ArrayList<>(List.of(pagePlacement)));
            }
            refreshCurrentPreview();
        }

        private Rectangle clampRectangleToImage(Rectangle rectangle, BufferedImage image) {
            int maxX = Math.max(0, image.getWidth() - rectangle.width);
            int maxY = Math.max(0, image.getHeight() - rectangle.height);
            int x = Math.max(0, Math.min(rectangle.x, maxX));
            int y = Math.max(0, Math.min(rectangle.y, maxY));
            int width = Math.max(1, Math.min(rectangle.width, image.getWidth()));
            int height = Math.max(1, Math.min(rectangle.height, image.getHeight()));
            if (x + width > image.getWidth()) {
                width = image.getWidth() - x;
            }
            if (y + height > image.getHeight()) {
                height = image.getHeight() - y;
            }
            return new Rectangle(x, y, width, height);
        }

        private void confirmAndStartSigning() {
            Map<Path, PdfSigningPlan> pdfPlans = new LinkedHashMap<>();
            for (Path file : files) {
                Map<Integer, List<SignatureService.PdfVisibleSignaturePlacement>> confirmedByPage = confirmedPlacements.get(file);
                if (confirmedByPage == null || confirmedByPage.isEmpty()) {
                    documentTabs.setSelectedIndex(files.indexOf(file));
                    JOptionPane.showMessageDialog(dialog, "Conferma una posizione in " + file.getFileName() + " prima di avviare la firma.");
                    return;
                }

                List<SignatureService.PdfVisibleSignaturePlacement> placements = new ArrayList<>();
                List<Integer> pageIndexes = new ArrayList<>(confirmedByPage.keySet());
                Collections.sort(pageIndexes);
                for (Integer pageIndex : pageIndexes) {
                    List<SignatureService.PdfVisibleSignaturePlacement> placementsOnPage = confirmedByPage.get(pageIndex);
                    if (placementsOnPage != null && !placementsOnPage.isEmpty()) {
                        placements.addAll(placementsOnPage);
                    }
                }
                pdfPlans.put(file, new PdfSigningPlan(List.copyOf(placements)));
            }

            OutputPlan outputTargets = resolveOutputTargets(files, outputDirectory, SigningModeOption.PDF);
            if (outputTargets == null) return;
            dialog.dispose();
            startSigningProcess(
                    identity,
                    outputDirectory,
                    files,
                    outputTargets,
                    SigningModeOption.PDF,
                    Map.copyOf(pdfPlans)
            );
        }

        private final class PdfPreviewSurface extends JComponent {
            private static final int MIN_SELECTION_WIDTH = 40;
            private static final int MIN_SELECTION_HEIGHT = 20;

            private BufferedImage pageImage;
            private List<Rectangle> confirmedRectangles = List.of();
            private Rectangle selectionRectangle;
            private Rectangle previousRectangle;
            private Point anchorPoint;
            private Point dragOffset;
            private boolean draggingSelection;
            private boolean drawingSelection;

            private PdfPreviewSurface() {
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

                MouseAdapter mouseHandler = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent event) {
                        if (pageImage == null || event.getClickCount() >= 2) {
                            return;
                        }

                        Point boundedPoint = clampPoint(event.getPoint());
                        previousRectangle = selectionRectangle == null ? null : new Rectangle(selectionRectangle);
                        if (selectionRectangle != null && selectionRectangle.contains(boundedPoint)) {
                            draggingSelection = true;
                            dragOffset = new Point(
                                    boundedPoint.x - selectionRectangle.x,
                                    boundedPoint.y - selectionRectangle.y
                            );
                            return;
                        }

                        drawingSelection = true;
                        anchorPoint = boundedPoint;
                        selectionRectangle = new Rectangle(anchorPoint.x, anchorPoint.y, 1, 1);
                        repaint();
                    }

                    @Override
                    public void mouseDragged(MouseEvent event) {
                        if (pageImage == null) {
                            return;
                        }

                        Point boundedPoint = clampPoint(event.getPoint());
                        if (drawingSelection && anchorPoint != null) {
                            selectionRectangle = createSelectionRectangle(anchorPoint, boundedPoint);
                            repaint();
                            return;
                        }

                        if (draggingSelection && selectionRectangle != null && dragOffset != null) {
                            int x = boundedPoint.x - dragOffset.x;
                            int y = boundedPoint.y - dragOffset.y;
                            selectionRectangle = clampRectangleToImage(
                                    new Rectangle(x, y, selectionRectangle.width, selectionRectangle.height),
                                    pageImage
                            );
                            repaint();
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent event) {
                        if (pageImage == null || selectionRectangle == null) {
                            return;
                        }

                        if (selectionRectangle.width < MIN_SELECTION_WIDTH || selectionRectangle.height < MIN_SELECTION_HEIGHT) {
                            selectionRectangle = previousRectangle == null ? selectionRectangle : new Rectangle(previousRectangle);
                        }

                        selectionRectangle = clampRectangleToImage(selectionRectangle, pageImage);
                        if (!selectionRectangle.equals(previousRectangle)) {
                            rememberPlacements();
                            updateCurrentPlacementFromDisplayRectangle(selectionRectangle);
                        }
                        drawingSelection = false;
                        draggingSelection = false;
                        anchorPoint = null;
                        dragOffset = null;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(MouseEvent event) {
                        if (pageImage == null || event.getClickCount() < 2) {
                            return;
                        }
                        confirmPlacementOnCurrentPage(clampPoint(event.getPoint()));
                    }
                };

                addMouseListener(mouseHandler);
                addMouseMotionListener(mouseHandler);
            }

            private void setPreview(
                    BufferedImage pageImage,
                    List<Rectangle> confirmedRectangles,
                    Rectangle selectionRectangle
            ) {
                this.pageImage = pageImage;
                this.confirmedRectangles = confirmedRectangles == null ? List.of() : List.copyOf(confirmedRectangles);
                this.selectionRectangle = selectionRectangle == null ? null : new Rectangle(selectionRectangle);
                if (pageImage == null) {
                    setPreferredSize(new Dimension(0, 0));
                } else {
                    setPreferredSize(new Dimension(pageImage.getWidth(), pageImage.getHeight()));
                }
                revalidate();
                repaint();
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (pageImage == null) {
                    g2.dispose();
                    return;
                }

                g2.drawImage(pageImage, 0, 0, null);

                for (Rectangle confirmedRectangle : confirmedRectangles) {
                    paintPlacementRectangle(g2, confirmedRectangle, "Firma", new Color(0, 102, 108, 66), new Color(0, 86, 92));
                }

                if (selectionRectangle != null) {
                    paintPlacementRectangle(g2, selectionRectangle, "Nuova", new Color(0, 241, 254, 70), new Color(0, 102, 108));
                }
                g2.dispose();
            }

            private void paintPlacementRectangle(
                    Graphics2D graphics,
                    Rectangle rectangle,
                    String label,
                    Color fillColor,
                    Color strokeColor
            ) {
                graphics.setColor(fillColor);
                graphics.fill(rectangle);
                graphics.setColor(strokeColor);
                graphics.setStroke(new BasicStroke(2f));
                graphics.draw(rectangle);

                graphics.setFont(new Font("Segoe UI", Font.BOLD, 12));
                int labelWidth = graphics.getFontMetrics().stringWidth(label);
                int labelX = Math.max(8, Math.min(rectangle.x + 8, getWidth() - labelWidth - 8));
                int labelY = rectangle.y + Math.min(22, rectangle.height - 8);
                graphics.setColor(new Color(0, 65, 69));
                graphics.drawString(label, labelX, labelY);
            }

            private Point clampPoint(Point point) {
                if (pageImage == null) {
                    return point;
                }
                int x = Math.max(0, Math.min(point.x, pageImage.getWidth() - 1));
                int y = Math.max(0, Math.min(point.y, pageImage.getHeight() - 1));
                return new Point(x, y);
            }

            private Rectangle createSelectionRectangle(Point start, Point end) {
                int x = Math.min(start.x, end.x);
                int y = Math.min(start.y, end.y);
                int width = Math.abs(end.x - start.x);
                int height = Math.abs(end.y - start.y);
                return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
            }
        }

        private final class PdfDocumentContext implements AutoCloseable {
            private final Path file;
            private final PDDocument document;
            private final PDFRenderer renderer;
            private final Map<String, BufferedImage> renderedPages = new LinkedHashMap<>();

            private PdfDocumentContext(Path file, PDDocument document) {
                this.file = file;
                this.document = document;
                this.renderer = new PDFRenderer(document);
                this.renderer.setSubsamplingAllowed(true);
            }

            private int getPageCount() {
                return document.getNumberOfPages();
            }

            private PDRectangle getPageBox(int pageIndex) {
                PDPage page = document.getPage(pageIndex);
                return page.getCropBox();
            }

            private PageRenderContext renderPage(int pageIndex, float renderScale) {
                try {
                    float effectiveScale = Math.max(0.5f, Math.min(renderScale, 4.5f));
                    String cacheKey = pageIndex + "@" + Math.round(effectiveScale * 1000f);
                    BufferedImage image = renderedPages.get(cacheKey);
                    if (image == null) {
                        image = renderer.renderImage(pageIndex, effectiveScale);
                        renderedPages.put(cacheKey, image);
                    }
                    PDRectangle pageBox = getPageBox(pageIndex);
                    return new PageRenderContext(file, pageIndex, image, pageBox.getWidth(), pageBox.getHeight());
                } catch (IOException ex) {
                    throw new IllegalStateException("Impossibile renderizzare il PDF " + file.getFileName() + ": " + ex.getMessage(), ex);
                }
            }

            @Override
            public void close() throws IOException {
                document.close();
            }
        }

        private record PageRenderContext(
                Path file,
                int pageIndex,
                BufferedImage image,
                float pageWidth,
                float pageHeight
        ) {
        }
    }

    private String resolveMessage(Exception ex) {
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.toString() : ex.getMessage();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private Dimension computeInitialWindowSize() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.max(650, Math.min(980, bounds.width));
        int height = Math.min(Math.max((int) (bounds.height * 0.88), 820), bounds.height);
        return new Dimension(width, height);
    }

    private void styleContentScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.setBackground(APP_BACKGROUND);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(APP_BACKGROUND);
        applyAppScrollBarStyle(scrollPane);
    }

    private void stylePopupScrollPane(JScrollPane scrollPane, Color viewportBackground, Border border) {
        scrollPane.setBorder(border);
        scrollPane.setOpaque(false);
        scrollPane.setBackground(APP_BACKGROUND);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(viewportBackground);
        applyAppScrollBarStyle(scrollPane);
    }

    private void applyAppScrollBarStyle(JScrollPane scrollPane) {
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getVerticalScrollBar().setOpaque(false);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(18, 0));
        scrollPane.getVerticalScrollBar().setUI(new AppScrollBarUI());
    }

    private JLabel createSectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 19));
        label.setForeground(TEXT_PRIMARY);
        return label;
    }

    private JLabel createSectionSubtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(TEXT_MUTED);
        return label;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(TEXT_PRIMARY);
        return label;
    }

    private JPanel createCard() {
        JPanel card = new JPanel();
        card.setBackground(CARD_BACKGROUND);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
                new EmptyBorder(22, 22, 22, 22)
        ));
        return card;
    }

    private JPanel createInnerPanel(BorderLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(true);
        panel.setBackground(new Color(255, 250, 244));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 228)),
                new EmptyBorder(20, 20, 20, 20)
        ));
        return panel;
    }

    private JPanel createQueueSurface() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(QUEUE_SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(223, 227, 231)),
                new EmptyBorder(18, 18, 18, 18)
        ));
        return panel;
    }

    private void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 10));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 14, 10, 14));
        if (primary) {
            button.setBackground(ACCENT_BRIGHT);
            button.setForeground(new Color(0, 65, 69));
        } else {
            button.setBackground(Color.WHITE);
            button.setForeground(TEXT_PRIMARY);
        }
    }

    private void styleToggleControl(JCheckBox checkBox) {
        checkBox.setOpaque(false);
        checkBox.setFocusPainted(false);
        checkBox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        checkBox.setForeground(TEXT_PRIMARY);
    }

    private void styleInfoIcon(InfoIconLabel icon) {
        icon.setToolTipText("Visualizza il dettaglio del certificato selezionato");
        icon.setForeground(ACCENT);
    }

    private void styleInput(JComponent component) {
        component.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        component.setBackground(new Color(0, 0, 0, 0));
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(171, 173, 174, 130)),
                new EmptyBorder(8, 6, 8, 6)
        ));
    }

    private void styleOutputField(JTextField field) {
        field.setOpaque(true);
        field.setBackground(Color.WHITE);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setSelectionColor(new Color(207, 248, 251));
        field.setSelectedTextColor(TEXT_PRIMARY);
    }

    private void alignInputHeight(JComponent component, JButton referenceButton) {
        int height = referenceButton.getPreferredSize().height;
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(Math.max(preferred.width, 220), height));
        component.setMinimumSize(new Dimension(120, height));
    }

    private void alignSquareComponent(JComponent component, JButton referenceButton) {
        int size = referenceButton.getPreferredSize().height;
        component.setPreferredSize(new Dimension(size, size));
        component.setMinimumSize(new Dimension(size, size));
    }

    private void styleBadge(JLabel label, Color background, Color foreground) {
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setBorder(new EmptyBorder(7, 12, 7, 12));
    }

    private JLabel createInfoBadge(String text) {
        JLabel label = new JLabel(text);
        styleBadge(label, new Color(241, 244, 246), TEXT_MUTED);
        return label;
    }

    private void updateCertificateCountBadge(int count) {
        certificateCountLabel.setToolTipText(null);
        if (count <= 0) {
            certificateCountLabel.setText("Per firmare, inserisci la smart card e premi Aggiorna certificati.");
            certificateCountLabel.setBackground(CARD_BACKGROUND);
            certificateCountLabel.setForeground(TEXT_PRIMARY);
            return;
        }

        certificateCountLabel.setText(count + (count == 1 ? " certificato disponibile" : " certificati disponibili"));
        certificateCountLabel.setBackground(CERTIFICATE_BADGE_AVAILABLE);
        certificateCountLabel.setForeground(TEXT_PRIMARY);
    }

    private void showCertificateDetailsPopup() {
        SigningIdentity identity = (SigningIdentity) identityCombo.getSelectedItem();
        if (identity == null) {
            return;
        }

        JDialog dialog = new JDialog(this, "Dettaglio certificato", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(APP_BACKGROUND);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
        headerPanel.setOpaque(false);
        headerPanel.add(createSectionTitle("Dettaglio certificato"), BorderLayout.NORTH);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgePanel.setOpaque(false);
        badgePanel.add(createInfoBadge(identity.profileLabel()));
        badgePanel.add(createInfoBadge(identity.keyAlgorithm()));
        headerPanel.add(badgePanel, BorderLayout.SOUTH);

        JPanel contentPanel = createQueueSurface();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.add(createCertificateDetailRow("Intestatario", identity.displayName()));
        contentPanel.add(createCertificateDetailRow("Alias", identity.alias()));
        contentPanel.add(createCertificateDetailRow("Thumbprint", identity.thumbprint()));
        contentPanel.add(createCertificateDetailRow("Issuer", extractCommonName(identity.issuer())));
        contentPanel.add(createCertificateDetailRow("Subject completo", identity.subject()));

        JButton closeButton = new JButton("Chiudi");
        styleButton(closeButton, false);
        closeButton.addActionListener(event -> dialog.dispose());

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footerPanel.setOpaque(false);
        footerPanel.add(closeButton);

        root.add(headerPanel, BorderLayout.NORTH);
        root.add(contentPanel, BorderLayout.CENTER);
        root.add(footerPanel, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(dialog.getPreferredSize());
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel createCertificateDetailRow(String labelText, String value) {
        JPanel row = new JPanel(new BorderLayout(0, 6));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(0, 0, 8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText.toUpperCase());
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(TEXT_MUTED);
        row.add(label, BorderLayout.NORTH);

        JTextArea valueArea = new JTextArea(value == null || value.isBlank() ? "-" : value);
        valueArea.setEditable(false);
        valueArea.setLineWrap(true);
        valueArea.setWrapStyleWord(true);
        valueArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        valueArea.setForeground(TEXT_PRIMARY);
        valueArea.setBackground(Color.WHITE);
        valueArea.setBorder(new EmptyBorder(5, 8, 5, 8));
        valueArea.setSize(new Dimension(560, Short.MAX_VALUE));
        Dimension valueAreaSize = valueArea.getPreferredSize();
        valueArea.setPreferredSize(new Dimension(560, valueAreaSize.height));

        JPanel valuePanel = new JPanel(new BorderLayout());
        valuePanel.setOpaque(true);
        valuePanel.setBackground(Color.WHITE);
        valuePanel.setBorder(BorderFactory.createLineBorder(new Color(223, 227, 231)));
        valuePanel.add(valueArea, BorderLayout.CENTER);
        valuePanel.setPreferredSize(new Dimension(578, valueAreaSize.height + 10));

        row.add(valuePanel, BorderLayout.CENTER);
        return row;
    }

    private String extractCommonName(String subject) {
        Matcher matcher = COMMON_NAME_PATTERN.matcher(subject);
        if (matcher.find()) {
            return matcher.group(2).trim();
        }
        return subject;
    }

    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            boolean accepted = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            dropZonePanel.setDragActive(support.isDrop() && accepted);
            return accepted;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            try {
                List<File> droppedFiles = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths = new ArrayList<>();
                for (File file : droppedFiles) {
                    paths.add(file.toPath());
                }
                addFiles(paths);
                return true;
            } catch (Exception ex) {
                showError("Impossibile acquisire i file trascinati: " + ex.getMessage());
                return false;
            } finally {
                dropZonePanel.setDragActive(false);
            }
        }
    }

    private final class PathRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                javax.swing.JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Path path && component instanceof JLabel label) {
                String parent = path.getParent() == null ? "" : "  |  " + path.getParent();
                label.setText(path.getFileName() + parent);
                label.setToolTipText(path.toAbsolutePath().toString());
                label.setBorder(new EmptyBorder(8, 6, 8, 6));
            }
            return component;
        }
    }

    private final class IdentityRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SigningIdentity identity && component instanceof JLabel label) {
                label.setText(identity.displayName() + " - " + identity.profileLabel());
                label.setToolTipText(identity.subject() + " | issuer: " + identity.issuer() + " | alias: " + identity.alias());
            }
            return component;
        }
    }

    private final class SigningModeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SigningModeOption option && component instanceof JLabel label) {
                label.setText(option.label());
                label.setToolTipText(option.description());
                label.setBorder(new EmptyBorder(8, 6, 8, 6));
            }
            return component;
        }
    }

    private enum SigningModeOption {
        ENVELOPED("Busta P7M", "Firma CMS/P7M con contenuto allegato."),
        PDF("PDF", "Firma PAdES integrata nel documento PDF.");

        private final String label;
        private final String description;

        SigningModeOption(String label, String description) {
            this.label = label;
            this.description = description;
        }

        private String label() {
            return label;
        }

        private String description() {
            return description;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class InfoIconLabel extends JComponent {
        private InfoIconLabel() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int diameter = Math.min(getWidth(), getHeight()) - 8;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            Color stroke = isEnabled() ? new Color(0, 102, 108) : new Color(171, 173, 174);
            Color fill = isEnabled() ? Color.WHITE : new Color(242, 244, 244);
            Color text = isEnabled() ? new Color(0, 102, 108) : new Color(140, 143, 144);

            g2.setColor(fill);
            g2.fillOval(x, y, diameter, diameter);
            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawOval(x, y, diameter, diameter);

            g2.setColor(text);
            g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(13, diameter / 2)));
            String glyph = "i";
            int textWidth = g2.getFontMetrics().stringWidth(glyph);
            int textAscent = g2.getFontMetrics().getAscent();
            int textDescent = g2.getFontMetrics().getDescent();
            int textX = (getWidth() - textWidth) / 2;
            int textY = (getHeight() + textAscent - textDescent) / 2 - 1;
            g2.drawString(glyph, textX, textY);
            g2.dispose();
        }
    }

    private JComponent createDropZoneSvgLabel() {
        JLabel label = new JLabel();
        label.setOpaque(false);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        Dimension iconSize = new Dimension(56, 56);
        label.setPreferredSize(iconSize);
        label.setMinimumSize(iconSize);
        label.setMaximumSize(iconSize);

        label.setIcon(createSvgIcon("/design/icon/drop.svg", iconSize));
        return label;
    }

    private SVGIcon createSvgIcon(String resourcePath, Dimension iconSize) {
        SVGIcon icon = new SVGIcon();
        icon.setAntiAlias(true);
        icon.setAutosize(SVGIcon.AUTOSIZE_BESTFIT);
        icon.setPreferredSize(iconSize);
        icon.setSvgResourcePath(resourcePath);
        return icon;
    }

    private Image createSvgImage(String resourcePath, Dimension iconSize) {
        SVGIcon icon = createSvgIcon(resourcePath, iconSize);
        BufferedImage image = new BufferedImage(iconSize.width, iconSize.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        icon.paintIcon(null, graphics, 0, 0);
        graphics.dispose();
        return image;
    }

    private static final class AppScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            trackColor = new Color(214, 219, 220);
            thumbColor = new Color(0, 102, 108);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected void paintTrack(Graphics graphics, JComponent component, Rectangle trackBounds) {
            if (trackBounds.width <= 0 || trackBounds.height <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(214, 219, 220, 215));
            if (scrollbar.getOrientation() == SwingConstants.HORIZONTAL) {
                int trackHeight = Math.min(10, Math.max(8, trackBounds.height - 6));
                int trackY = trackBounds.y + (trackBounds.height - trackHeight) / 2;
                int trackX = trackBounds.x + 6;
                int trackWidth = Math.max(0, trackBounds.width - 12);
                g2.fillRoundRect(trackX, trackY, trackWidth, trackHeight, trackHeight, trackHeight);
            } else {
                int trackWidth = Math.min(10, Math.max(8, trackBounds.width - 8));
                int trackX = trackBounds.x + (trackBounds.width - trackWidth) / 2;
                int trackY = trackBounds.y + 6;
                int trackHeight = Math.max(0, trackBounds.height - 12);
                g2.fillRoundRect(trackX, trackY, trackWidth, trackHeight, trackWidth, trackWidth);
            }
            g2.dispose();
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle thumbBounds) {
            if (thumbBounds.width <= 0 || thumbBounds.height <= 0 || !scrollbar.isEnabled()) {
                return;
            }

            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (scrollbar.getOrientation() == SwingConstants.HORIZONTAL) {
                int thumbHeight = Math.min(10, Math.max(8, thumbBounds.height - 4));
                int thumbY = thumbBounds.y + (thumbBounds.height - thumbHeight) / 2;
                int thumbX = thumbBounds.x + 2;
                int thumbWidth = Math.max(thumbBounds.width - 4, 28);
                if (thumbWidth > thumbBounds.width) {
                    thumbWidth = thumbBounds.width;
                    thumbX = thumbBounds.x;
                }

                g2.setPaint(new GradientPaint(
                        thumbX,
                        thumbY,
                        new Color(0, 128, 136),
                        thumbX + thumbWidth,
                        thumbY,
                        new Color(0, 86, 92)
                ));
                g2.fillRoundRect(thumbX, thumbY, thumbWidth, thumbHeight, thumbHeight, thumbHeight);
                g2.setColor(new Color(255, 255, 255, 120));
                g2.drawRoundRect(thumbX, thumbY, thumbWidth - 1, thumbHeight - 1, thumbHeight, thumbHeight);
            } else {
                int thumbWidth = Math.min(10, Math.max(8, thumbBounds.width - 4));
                int thumbX = thumbBounds.x + (thumbBounds.width - thumbWidth) / 2;
                int thumbY = thumbBounds.y + 2;
                int thumbHeight = Math.max(thumbBounds.height - 4, 18);
                if (thumbHeight > thumbBounds.height) {
                    thumbHeight = thumbBounds.height;
                    thumbY = thumbBounds.y;
                }

                g2.setPaint(new GradientPaint(
                        thumbX,
                        thumbY,
                        new Color(0, 128, 136),
                        thumbX,
                        thumbY + thumbHeight,
                        new Color(0, 86, 92)
                ));
                g2.fillRoundRect(thumbX, thumbY, thumbWidth, thumbHeight, thumbWidth, thumbWidth);
                g2.setColor(new Color(255, 255, 255, 120));
                g2.drawRoundRect(thumbX, thumbY, thumbWidth - 1, thumbHeight - 1, thumbWidth, thumbWidth);
            }
            g2.dispose();
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return scrollbar != null && scrollbar.getOrientation() == SwingConstants.HORIZONTAL
                    ? new Dimension(54, 10)
                    : new Dimension(10, 54);
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setOpaque(false);
            button.setBorder(BorderFactory.createEmptyBorder());
            button.setFocusable(false);
            button.setContentAreaFilled(false);
            return button;
        }
    }


    private static final class BlueprintPanel extends JPanel {
        private BlueprintPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(APP_BACKGROUND);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(171, 173, 174, 28));
            for (int x = 0; x < getWidth(); x += 40) {
                g2.drawLine(x, 0, x, getHeight());
            }
            for (int y = 0; y < getHeight(); y += 40) {
                g2.drawLine(0, y, getWidth(), y);
            }
            g2.dispose();
        }
    }

    private static final class DropSurfacePanel extends JPanel {
        private boolean dragActive;

        private DropSurfacePanel() {
            setOpaque(false);
        }

        private void setDragActive(boolean dragActive) {
            if (this.dragActive != dragActive) {
                this.dragActive = dragActive;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(dragActive ? ACCENT_PALE : DROP_SURFACE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 26, 26);
            g2.dispose();
            super.paintComponent(graphics);
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(dragActive ? DROP_ACTIVE : DROP_BORDER);
            g2.setStroke(new BasicStroke(dragActive ? 2.4f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{10f, 8f}, 0f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 26, 26);
            g2.dispose();
        }
    }
}
