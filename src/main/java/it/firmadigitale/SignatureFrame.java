package it.firmadigitale;

import com.kitfox.svg.app.beans.SVGIcon;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
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
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;

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
    private final JButton loadCertificatesButton = new JButton("Aggiorna certificati");
    private final InfoIconLabel certificateInfoIcon = new InfoIconLabel();
    private final LinkLabel browseFilesLinkLabel = new LinkLabel("NAVIGA TRA LE CARTELLE");
    private final JButton browseOutputButton = new JButton("Scegli");
    private final JButton removeFileButton = new JButton("Rimuovi selezionato");
    private final JButton clearFilesButton = new JButton("Svuota coda");
    private final JButton signButton = new JButton("Firma file");
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
        setMinimumSize(new Dimension(650, 760));

        setContentPane(buildRootPanel());
        setSize(computeInitialWindowSize());
        setLocationRelativeTo(null);
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

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        badgePanel.setOpaque(false);

        panel.add(textPanel, BorderLayout.CENTER);
        badgePanel.add(signButton);
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
        dropZonePanel.removeAll();
        dropZonePanel.setLayout(new BorderLayout());
        dropZonePanel.setTransferHandler(new FileDropHandler());
        dropZonePanel.setMinimumSize(new Dimension(380, 228));
        dropZonePanel.setPreferredSize(new Dimension(420, 252));
        dropZonePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        for (var listener : dropZonePanel.getMouseListeners()) {
            dropZonePanel.removeMouseListener(listener);
        }
        dropZonePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!busy) {
                    addFilesFromChooser();
                }
            }
        });

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(14, 18, 20, 18));

        JComponent glyph = createDropZoneSvgLabel();
        glyph.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("FILE DA FIRMARE");
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

        browseFilesLinkLabel.setHorizontalAlignment(SwingConstants.CENTER);
        browseFilesLinkLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(Box.createVerticalGlue());
        content.add(glyph);
        content.add(Box.createVerticalStrut(6));
        content.add(title);
        content.add(Box.createVerticalStrut(2));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(2));
        content.add(orLabel);
        content.add(Box.createVerticalStrut(2));
        content.add(browseFilesLinkLabel);
        content.add(Box.createVerticalGlue());

        dropZonePanel.add(content, BorderLayout.CENTER);
        return dropZonePanel;
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

        JLabel queueTitle = new JLabel("Coda delle firme");
        queueTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        queueTitle.setForeground(TEXT_PRIMARY);
        titlePanel.add(queueTitle, BorderLayout.CENTER);

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
        identityCombo.setMaximumRowCount(12);
        fileQueueCombo.setMaximumRowCount(16);

        styleInput(identityCombo);
        styleInput(fileQueueCombo);
        styleInput(outputField);
        styleOutputField(outputField);
        styleInfoIcon(certificateInfoIcon);
        styleLinkLabel(browseFilesLinkLabel);
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
        signButton.setPreferredSize(new Dimension(164, 40));
        logButton.setPreferredSize(new Dimension(92, 40));
        progressBar.setPreferredSize(new Dimension(170, 18));
        updateCertificateCountBadge(0);

        appendLog("Applicazione avviata. Modalita GUI fissata su Windows-MY.");
    }

    private void bindActions() {
        browseOutputButton.addActionListener(event -> selectOutputDirectory());
        logButton.addActionListener(event -> showLogPopup());
        browseFilesLinkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (browseFilesLinkLabel.isEnabled()) {
                    addFilesFromChooser();
                }
            }
        });
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

    private void clearFilesAfterSuccessfulSigning() {
        if (fileQueueModel.getSize() == 0) {
            return;
        }

        fileQueueModel.removeAllElements();
        appendLog("Coda file svuotata automaticamente dopo la firma completata.");
        refreshFileDetails();
        updateActionState();
    }

    private void loadCertificates() {
        setBusy(true, "Lettura certificati da Windows-MY in corso...");
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
                    setBusy(false, identities.isEmpty() ? "Nessun certificato di firma trovato in Windows-MY." : "Certificati pronti.");

                    if (identities.isEmpty()) {
                        showError("Nessun certificato adatto alla firma trovato nello store Windows.");
                    }
                } catch (Exception ex) {
                    identityCombo.removeAllItems();
                    updateCertificateCountBadge(0);
                    refreshCertificateInfoState();
                    setBusy(false, "Errore nel caricamento certificati.");
                    showError(resolveMessage(ex));
                } finally {
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
        Map<Path, Path> outputTargets = resolveOutputTargets(files, outputDirectory);
        if (outputTargets == null) {
            return;
        }
        SigningProgressDialog signingDialog = new SigningProgressDialog(selectedIdentity, outputDirectory, files, outputTargets);

        setBusy(true, "Firma in corso su " + files.size() + " file...");
        appendLog("Avvio firma di " + files.size() + " file con certificato " + selectedIdentity.displayName() + ".");
        signingDialog.open();

        SwingWorker<List<Path>, SigningFileUpdate> worker = new SwingWorker<>() {
            @Override
            protected List<Path> doInBackground() throws Exception {
                List<Path> results = new ArrayList<>();
                for (int index = 0; index < files.size(); index++) {
                    Path file = files.get(index);
                    publish(new SigningFileUpdate(file, null, index, files.size(), false));
                    Path result = signatureService.signFile(buildConfig(), selectedIdentity.alias(), file, outputTargets.get(file));
                    boolean verified = signatureService.verifySignature(result);
                    results.add(result);
                    publish(new SigningFileUpdate(file, result, index + 1, files.size(), verified));
                }
                return results;
            }

            @Override
            protected void process(List<SigningFileUpdate> updates) {
                for (SigningFileUpdate update : updates) {
                    if (update.outputFile() == null) {
                        signingDialog.markProcessing(update.inputFile(), update.completedCount());
                        continue;
                    }

                    appendLog("Creato " + update.outputFile() + " | verifica locale: " + (update.verified() ? "OK" : "KO"));
                    signingDialog.markCompleted(update.inputFile(), update.outputFile(), update.verified(), update.completedCount());
                }
            }

            @Override
            protected void done() {
                try {
                    List<Path> results = get();
                    setBusy(false, "Firma completata: " + results.size() + " file prodotti.");
                    signingDialog.markSuccess(results.size());
                    clearFilesAfterSuccessfulSigning();
                } catch (Exception ex) {
                    setBusy(false, "Errore durante la firma.");
                    String message = resolveMessage(ex);
                    signingDialog.markFailure(message);
                    showError(message);
                } finally {
                    updateActionState();
                }
            }
        };

        worker.execute();
    }

    private SignatureConfig buildConfig() {
        return new SignatureConfig(KeyStoreMode.WINDOWS_MY, "", "");
    }

    private Map<Path, Path> resolveOutputTargets(List<Path> files, Path outputDirectory) {
        Map<Path, Path> outputTargets = new LinkedHashMap<>();
        List<Path> conflictingFiles = new ArrayList<>();
        for (Path file : files) {
            Path outputFile = resolveSignedOutputPath(file, outputDirectory);
            outputTargets.put(file, outputFile);
            if (Files.exists(outputFile)) {
                conflictingFiles.add(file);
            }
        }

        if (conflictingFiles.isEmpty()) {
            return outputTargets;
        }

        appendLog("Rilevati " + conflictingFiles.size() + " file gia presenti nella cartella di destinazione.");
        for (int index = 0; index < conflictingFiles.size(); index++) {
            Path inputFile = conflictingFiles.get(index);
            while (true) {
                Path targetOutput = outputTargets.get(inputFile);
                if (!Files.exists(targetOutput)) {
                    break;
                }

                OverwriteDialogChoice choice = showOverwriteConflictPopup(targetOutput, index + 1, conflictingFiles.size());
                if (choice == OverwriteDialogChoice.OVERWRITE) {
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

                Path renamedOutput = showRenameOutputPopup(inputFile, targetOutput, outputDirectory, outputTargets);
                if (renamedOutput == null) {
                    continue;
                }

                outputTargets.put(inputFile, renamedOutput);
                appendLog("Output rinominato per " + inputFile.getFileName() + ": " + renamedOutput.getFileName() + ".");
                break;
            }
        }
        return outputTargets;
    }

    private OverwriteDialogChoice showOverwriteConflictPopup(Path existingOutput, int currentConflict, int totalConflicts) {
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
        headerPanel.add(createSectionSubtitle("Esiste gia un output con lo stesso nome nella cartella di destinazione."), BorderLayout.SOUTH);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgePanel.setOpaque(false);
        badgePanel.add(createInfoBadge(totalConflicts == 1 ? "1 conflitto" : currentConflict + " di " + totalConflicts));
        badgePanel.add(createInfoBadge(existingOutput.getFileName().toString()));

        JPanel detailsPanel = createQueueSurface();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.add(createDialogDetailRow("Nome file", existingOutput.getFileName().toString()));
        detailsPanel.add(createDialogDetailRow("Percorso", existingOutput.toString()));

        JButton renameButton = new JButton("RINOMINA");
        JButton verifyButton = new JButton("VERIFICA FILE");
        JButton cancelButton = new JButton("ANNULLA");
        JButton overwriteButton = new JButton("SOVRASCRIVI");
        styleButton(renameButton, false);
        styleButton(verifyButton, false);
        styleButton(cancelButton, false);
        styleButton(overwriteButton, true);
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
        try {
            SignatureService.SignedFileInspection inspection = signatureService.inspectSignedFile(signedFile);
            boolean verified = signatureService.verifySignature(signedFile);

            JDialog dialog = new JDialog(this, "Verifica file", true);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setResizable(false);

            JPanel root = new JPanel(new BorderLayout(0, 16));
            root.setBackground(APP_BACKGROUND);
            root.setBorder(new EmptyBorder(18, 18, 18, 18));

            JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
            headerPanel.setOpaque(false);
            headerPanel.add(createSectionTitle("Verifica file"), BorderLayout.NORTH);
            headerPanel.add(createSectionSubtitle("Dettaglio del file gia presente nella cartella di output."), BorderLayout.SOUTH);

            JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            badgePanel.setOpaque(false);
            badgePanel.add(createInfoBadge(verified ? "Verifica OK" : "Verifica KO"));
            badgePanel.add(createInfoBadge(inspection.signers().size() == 1 ? "1 firmatario" : inspection.signers().size() + " firmatari"));

            JPanel summaryPanel = createQueueSurface();
            summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
            summaryPanel.add(createDialogDetailRow("Nome file", inspection.fileName()));
            summaryPanel.add(createDialogDetailRow("Percorso", inspection.absolutePath().toString()));
            summaryPanel.add(createDialogDetailRow("Data creazione", formatDateTime(inspection.creationTime())));
            summaryPanel.add(createDialogDetailRow("Data firma", summarizeSigningTimes(inspection.signers())));

            JPanel signersPanel = createQueueSurface();
            signersPanel.setLayout(new BoxLayout(signersPanel, BoxLayout.Y_AXIS));
            JLabel signersTitle = createFieldLabel("Firmatari");
            signersTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            signersPanel.add(signersTitle);
            signersPanel.add(Box.createVerticalStrut(12));

            if (inspection.signers().isEmpty()) {
                signersPanel.add(createDialogDetailRow("Esito", "Nessun firmatario rilevato nel file selezionato."));
            } else {
                for (int index = 0; index < inspection.signers().size(); index++) {
                    signersPanel.add(createSignerInfoRow(inspection.signers().get(index), index));
                    if (index < inspection.signers().size() - 1) {
                        signersPanel.add(Box.createVerticalStrut(10));
                    }
                }
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
            centerPanel.add(summaryPanel);
            centerPanel.add(Box.createVerticalStrut(12));
            centerPanel.add(signersPanel);

            root.add(headerPanel, BorderLayout.NORTH);
            root.add(centerPanel, BorderLayout.CENTER);
            root.add(footerPanel, BorderLayout.SOUTH);

            dialog.setContentPane(root);
            dialog.pack();
            dialog.setMinimumSize(dialog.getPreferredSize());
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        } catch (Exception ex) {
            showError("Impossibile verificare il file esistente: " + resolveMessage(ex));
        }
    }

    private Path showRenameOutputPopup(Path inputFile, Path currentOutput, Path outputDirectory, Map<Path, Path> outputTargets) {
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

        JTextField renameField = new JTextField(currentOutput.getFileName().toString());
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

            String normalizedName = rawName.endsWith(".p7m") ? rawName : rawName + ".p7m";
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

    private Path resolveSignedOutputPath(Path inputFile, Path outputDirectory) {
        return outputDirectory.resolve(inputFile.getFileName().toString() + ".p7m");
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
                .append(Path.of(outputField.getText().isBlank() ? "." : outputField.getText().trim()).resolve(selectedFile.getFileName().toString() + ".p7m"))
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

        JLabel metaLabel = new JLabel(buildFileMeta(path));
        metaLabel.setFont(new Font("Segoe UI", Font.BOLD, 9));
        metaLabel.setForeground(TEXT_MUTED);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(nameLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(metaLabel);

        JButton removeButton = new JButton();
        removeButton.setFocusPainted(false);
        removeButton.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        removeButton.setBackground(row.getBackground());
        removeButton.setContentAreaFilled(false);
        removeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeButton.setToolTipText("Rimuovi file dalla coda");
        removeButton.setPreferredSize(new Dimension(30, 30));
        removeButton.setIcon(createSvgIcon("/design/icon/delete.svg", new Dimension(18, 18)));
        removeButton.addActionListener(event -> {
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
        outputField.setEnabled(!busy);
        certificateInfoIcon.setEnabled(!busy && hasCertificate);
        loadCertificatesButton.setEnabled(!busy);
        browseFilesLinkLabel.setEnabled(!busy);
        fileQueueCombo.setEnabled(!busy && hasFiles);
        fileQueueCombo.setVisible(hasFiles);
        activeAssetLabel.setVisible(hasFiles);
        removeFileButton.setEnabled(!busy && hasFiles && fileQueueCombo.getSelectedItem() != null);
        clearFilesButton.setEnabled(!busy && hasFiles);
        signButton.setEnabled(!busy && hasFiles && hasCertificate);
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
        scrollPane.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        scrollPane.setPreferredSize(new Dimension(760, 360));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

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

    private record SigningFileUpdate(Path inputFile, Path outputFile, int completedCount, int totalCount, boolean verified) {
    }

    private enum OverwriteDialogChoice {
        OVERWRITE,
        CANCEL,
        INSPECT,
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
            listScrollPane.setBorder(BorderFactory.createEmptyBorder());
            listScrollPane.getViewport().setBackground(APP_BACKGROUND);
            listScrollPane.setPreferredSize(new Dimension(610, Math.min(320, 98 + (files.size() * 92))));
            listScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            listScrollPane.getVerticalScrollBar().setUnitIncrement(16);

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
            rightPanel.add(statusBadge);

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

        private void markCompleted(Path inputFile, Path outputFile, boolean verified, int completedCount) {
            titleLabel.setText("Firma in corso");
            subtitleLabel.setText("Creato " + outputFile.getFileName());
            progress.setValue(completedCount);
            progress.setString(completedCount + " / " + totalFiles);

            JLabel statusBadge = statusLabels.get(inputFile);
            if (statusBadge != null) {
                setBadgeState(statusBadge, verified ? "VERIFICATO" : "FIRMATO", new Color(231, 243, 238), new Color(18, 97, 79));
            }

            JLabel metaLabel = metaLabels.get(inputFile);
            if (metaLabel != null) {
                metaLabel.setText("Output: " + outputFile + (verified ? "  |  verifica OK" : "  |  verifica KO"));
                metaLabel.setToolTipText(metaLabel.getText());
            }
        }

        private void markSuccess(int resultCount) {
            titleLabel.setText("Firma completata");
            subtitleLabel.setText(resultCount + (resultCount == 1 ? " file firmato correttamente." : " file firmati correttamente."));
            setBadgeState(phaseBadge, "COMPLETATO", new Color(231, 243, 238), new Color(18, 97, 79));
            progress.setValue(totalFiles);
            progress.setString(totalFiles + " / " + totalFiles);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            closeButton.setEnabled(true);
        }

        private void markFailure(String message) {
            titleLabel.setText("Errore durante la firma");
            subtitleLabel.setText(message);
            setBadgeState(phaseBadge, "ERRORE", new Color(182, 39, 52), Color.WHITE);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            closeButton.setEnabled(true);
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
        int width = Math.min(650, bounds.width);
        int height = Math.min(Math.max((int) (bounds.height * 0.88), 820), bounds.height);
        return new Dimension(width, height);
    }

    private void styleContentScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.setBackground(APP_BACKGROUND);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(APP_BACKGROUND);
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

    private void styleInfoIcon(InfoIconLabel icon) {
        icon.setToolTipText("Visualizza il dettaglio del certificato selezionato");
        icon.setForeground(ACCENT);
    }

    private void styleLinkLabel(LinkLabel label) {
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        label.setForeground(ACCENT);
        label.setToolTipText("Apri il selettore file");
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
        if (count <= 0) {
            certificateCountLabel.setText("Nessun certificato disponibile");
            certificateCountLabel.setBackground(CERTIFICATE_BADGE_EMPTY);
            certificateCountLabel.setForeground(Color.WHITE);
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
                javax.swing.JList<?> list,
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

    private static final class LinkLabel extends JLabel {
        private final String plainText;

        private LinkLabel(String text) {
            this.plainText = text;
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setEnabled(true);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            setForeground(enabled ? ACCENT : new Color(140, 143, 144));
            setText(plainText);
        }
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

            int trackWidth = Math.min(10, Math.max(8, trackBounds.width - 8));
            int trackX = trackBounds.x + (trackBounds.width - trackWidth) / 2;
            int trackY = trackBounds.y + 6;
            int trackHeight = Math.max(0, trackBounds.height - 12);

            g2.setColor(new Color(214, 219, 220, 215));
            g2.fillRoundRect(trackX, trackY, trackWidth, trackHeight, trackWidth, trackWidth);
            g2.dispose();
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle thumbBounds) {
            if (thumbBounds.width <= 0 || thumbBounds.height <= 0 || !scrollbar.isEnabled()) {
                return;
            }

            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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
            g2.dispose();
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(10, 54);
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
