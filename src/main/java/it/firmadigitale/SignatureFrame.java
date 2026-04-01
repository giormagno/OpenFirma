package it.firmadigitale;

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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
    private static final Pattern COMMON_NAME_PATTERN = Pattern.compile("(^|,\\s*)CN=([^,]+)");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SignatureService signatureService;
    private final JComboBox<SigningIdentity> identityCombo = new JComboBox<>();
    private final JTextField outputField = new JTextField(Path.of(System.getProperty("user.dir"), "output").toString());
    private final DefaultComboBoxModel<Path> fileQueueModel = new DefaultComboBoxModel<>();
    private final JComboBox<Path> fileQueueCombo = new JComboBox<>(fileQueueModel);
    private final JTextArea certificateDetailsArea = new JTextArea();
    private final JTextArea fileDetailsArea = new JTextArea();
    private final JTextArea logArea = new JTextArea();
    private final DropSurfacePanel dropZonePanel = new DropSurfacePanel();
    private final JLabel certificateCountLabel = new JLabel("Nessun certificato QES disponibile");
    private final JLabel fileCountLabel = new JLabel("0 file");
    private final JLabel queueSummaryLabel = new JLabel("Nessun documento caricato");
    private final JLabel totalSizeLabel = new JLabel("0 B total");
    private final JLabel statusLabel = new JLabel("Pronto");
    private final JButton loadCertificatesButton = new JButton("Aggiorna certificati");
    private final JButton browseOutputButton = new JButton("Browse");
    private final JButton addFilesButton = new JButton("Seleziona file");
    private final JButton removeFileButton = new JButton("Rimuovi selezionato");
    private final JButton clearFilesButton = new JButton("Svuota coda");
    private final JButton signButton = new JButton("Firma file");
    private final JProgressBar progressBar = new JProgressBar();

    private JPanel filesWorkspacePanel;
    private JPanel leftColumnPanel;
    private JPanel rightColumnPanel;
    private JPanel queuePanel;
    private JPanel stagedAssetsListPanel;
    private boolean busy;
    private boolean wideFilesLayout;

    public SignatureFrame(SignatureService signatureService) {
        super("Firma digitale P7M");
        this.signatureService = signatureService;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 760));

        setContentPane(buildRootPanel());
        setSize(computeInitialWindowSize());
        setLocationRelativeTo(null);
        configureComponents();
        bindActions();
        refreshCertificateDetails();
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
                new EmptyBorder(14, 18, 14, 18)
        ));

        JPanel textPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        textPanel.setOpaque(false);

        JLabel markLabel = new JLabel("//");
        markLabel.setForeground(ACCENT_BRIGHT);
        markLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JLabel titleLabel = new JLabel("JavaSign");
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));

        textPanel.add(markLabel);
        textPanel.add(titleLabel);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        badgePanel.setOpaque(false);

        JLabel badgeLabel = new JLabel("Windows-MY", SwingConstants.CENTER);
        badgeLabel.setOpaque(true);
        badgeLabel.setBackground(CARD_BACKGROUND);
        badgeLabel.setForeground(TEXT_PRIMARY);
        badgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badgeLabel.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel qesBadgeLabel = new JLabel("QES ONLY", SwingConstants.CENTER);
        qesBadgeLabel.setOpaque(true);
        qesBadgeLabel.setBackground(CARD_BACKGROUND);
        qesBadgeLabel.setForeground(TEXT_PRIMARY);
        qesBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        qesBadgeLabel.setBorder(new EmptyBorder(8, 12, 8, 12));

        panel.add(textPanel, BorderLayout.CENTER);
        badgePanel.add(badgeLabel);
        badgePanel.add(qesBadgeLabel);
        panel.add(badgePanel, BorderLayout.EAST);
        return panel;
    }

    private JComponent buildCenterPanel() {
        BlueprintPanel mainPanel = new BlueprintPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(24, 22, 26, 22));

        JPanel stackPanel = new JPanel();
        stackPanel.setOpaque(false);
        stackPanel.setLayout(new BoxLayout(stackPanel, BoxLayout.Y_AXIS));

        filesWorkspacePanel = new JPanel(new GridBagLayout());
        filesWorkspacePanel.setOpaque(false);
        filesWorkspacePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                refreshFilesWorkspaceLayout();
            }
        });

        stackPanel.add(buildHeroPanel());
        stackPanel.add(Box.createVerticalStrut(22));
        stackPanel.add(filesWorkspacePanel);

        JScrollPane contentScrollPane = new JScrollPane(stackPanel);
        contentScrollPane.setBorder(BorderFactory.createEmptyBorder());
        contentScrollPane.setOpaque(false);
        contentScrollPane.getViewport().setOpaque(false);
        contentScrollPane.getVerticalScrollBar().setUnitIncrement(18);
        contentScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mainPanel.add(contentScrollPane, BorderLayout.CENTER);
        return mainPanel;
    }

    private JPanel buildHeroPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel statusBadge = new JLabel("SYSTEM READY");
        statusBadge.setForeground(ACCENT);
        statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        statusBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Initialize Payload");
        title.setForeground(TEXT_PRIMARY);
        title.setFont(new Font("Segoe UI", Font.BOLD, 34));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(statusBadge);
        panel.add(Box.createVerticalStrut(8));
        panel.add(title);
        return panel;
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
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 0);
        leftColumnPanel.add(buildFilesCard(), constraints);
        return leftColumnPanel;
    }

    private JPanel buildRightColumn() {
        rightColumnPanel = new JPanel(new GridBagLayout());
        rightColumnPanel.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 0.58;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 18, 0);
        rightColumnPanel.add(buildQueuePanel(), constraints);

        constraints.gridy = 1;
        constraints.weighty = 0.28;
        rightColumnPanel.add(buildFooterBar(), constraints);

        constraints.gridy = 2;
        constraints.weighty = 0.14;
        constraints.insets = new Insets(0, 0, 0, 0);
        rightColumnPanel.add(buildLogCard(), constraints);
        return rightColumnPanel;
    }

    private JPanel buildConfigurationCard() {
        JPanel card = createCard();
        card.setLayout(new GridBagLayout());

        JLabel cardTitle = createSectionTitle("Verification certificate");
        JLabel cardSubtitle = createSectionSubtitle("Windows-MY espone solo certificati SIG/QES conformi per la firma qualificata.");

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 4, 0);
        card.add(cardTitle, constraints);

        constraints.gridy = 1;
        constraints.insets = new Insets(0, 0, 6, 0);
        card.add(cardSubtitle, constraints);

        constraints.gridy = 2;
        constraints.insets = new Insets(0, 0, 18, 0);
        certificateCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        certificateCountLabel.setForeground(TEXT_MUTED);
        certificateCountLabel.setOpaque(true);
        certificateCountLabel.setBackground(ACCENT_SOFT);
        certificateCountLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
        card.add(certificateCountLabel, constraints);

        constraints.gridwidth = 1;
        constraints.gridy = 3;
        constraints.insets = new Insets(0, 0, 8, 0);
        card.add(createFieldLabel("Certificato"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 12, 8, 12);
        card.add(identityCombo, constraints);

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 8, 0);
        card.add(loadCertificatesButton, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 3;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 16, 0);
        JScrollPane certificateScrollPane = new JScrollPane(certificateDetailsArea);
        certificateScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CARD_BORDER));
        certificateScrollPane.setPreferredSize(new Dimension(280, 118));
        card.add(certificateScrollPane, constraints);

        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 0, 0);
        card.add(createFieldLabel("Output directory"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(0, 12, 0, 12);
        card.add(outputField, constraints);

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 0, 0);
        card.add(browseOutputButton, constraints);

        return card;
    }

    private JPanel buildFilesCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 16));

        card.add(createSectionTitle("Initialize payload"), BorderLayout.NORTH);
        card.add(buildDropZonePanel(), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildDropZonePanel() {
        dropZonePanel.removeAll();
        dropZonePanel.setLayout(new BorderLayout());
        dropZonePanel.setTransferHandler(new FileDropHandler());
        dropZonePanel.setMinimumSize(new Dimension(380, 360));
        dropZonePanel.setPreferredSize(new Dimension(420, 420));
        dropZonePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        content.setBorder(new EmptyBorder(30, 24, 30, 24));

        JLabel glyph = new JLabel("::");
        glyph.setFont(new Font("Segoe UI", Font.BOLD, 52));
        glyph.setForeground(new Color(142, 145, 146));
        glyph.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("INITIALIZE PAYLOAD");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("DROP FILES OR CLICK HERE");
        subtitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        actionRow.add(addFilesButton);

        JLabel helper = new JLabel("Selezione multipla e drag-and-drop attivi");
        helper.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        helper.setForeground(TEXT_MUTED);
        helper.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(Box.createVerticalGlue());
        content.add(glyph);
        content.add(Box.createVerticalStrut(18));
        content.add(title);
        content.add(Box.createVerticalStrut(8));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(26));
        content.add(actionRow);
        content.add(Box.createVerticalStrut(14));
        content.add(helper);
        content.add(Box.createVerticalGlue());

        dropZonePanel.add(content, BorderLayout.CENTER);
        return dropZonePanel;
    }

    private JPanel buildQueuePanel() {
        JPanel panel = createQueueSurface();
        panel.setLayout(new BorderLayout(0, 16));
        panel.setMinimumSize(new Dimension(420, 360));
        panel.setPreferredSize(new Dimension(520, 420));

        JPanel headerPanel = new JPanel(new BorderLayout(12, 0));
        headerPanel.setOpaque(false);

        JPanel titlePanel = new JPanel(new BorderLayout(0, 6));
        titlePanel.setOpaque(false);

        JLabel queueTitle = new JLabel("Staged assets");
        queueTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        queueTitle.setForeground(TEXT_PRIMARY);

        queueSummaryLabel.setForeground(TEXT_MUTED);
        queueSummaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        titlePanel.add(queueTitle, BorderLayout.NORTH);
        titlePanel.add(queueSummaryLabel, BorderLayout.SOUTH);

        JPanel rightHeader = new JPanel(new BorderLayout(10, 0));
        rightHeader.setOpaque(false);
        rightHeader.add(fileCountLabel, BorderLayout.WEST);
        rightHeader.add(totalSizeLabel, BorderLayout.EAST);

        headerPanel.add(titlePanel, BorderLayout.CENTER);
        headerPanel.add(rightHeader, BorderLayout.EAST);

        stagedAssetsListPanel = new JPanel();
        stagedAssetsListPanel.setOpaque(false);
        stagedAssetsListPanel.setLayout(new BoxLayout(stagedAssetsListPanel, BoxLayout.Y_AXIS));

        JScrollPane listScrollPane = new JScrollPane(stagedAssetsListPanel);
        listScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CARD_BORDER));
        listScrollPane.getViewport().setBackground(Color.WHITE);
        listScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel footerPanel = new JPanel(new BorderLayout(12, 0));
        footerPanel.setOpaque(false);
        footerPanel.add(clearFilesButton, BorderLayout.WEST);

        JPanel removePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        removePanel.setOpaque(false);
        removePanel.add(removeFileButton);
        footerPanel.add(removePanel, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(listScrollPane, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshFilesWorkspaceLayout() {
        if (filesWorkspacePanel == null) {
            return;
        }

        int availableWidth = filesWorkspacePanel.getWidth();
        if (availableWidth <= 0) {
            availableWidth = getWidth() - 120;
        }

        boolean nextWideLayout = availableWidth >= 1120;
        if (filesWorkspacePanel.getComponentCount() > 0 && wideFilesLayout == nextWideLayout) {
            return;
        }
        wideFilesLayout = nextWideLayout;

        filesWorkspacePanel.removeAll();
        leftColumnPanel = buildLeftColumn();
        rightColumnPanel = buildRightColumn();

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridy = 0;

        if (wideFilesLayout) {
            constraints.gridx = 0;
            constraints.weightx = 0.42;
            constraints.weighty = 1;
            constraints.insets = new Insets(0, 0, 0, 16);
            filesWorkspacePanel.add(leftColumnPanel, constraints);

            constraints.gridx = 1;
            constraints.weightx = 0.58;
            constraints.insets = new Insets(0, 0, 0, 0);
            filesWorkspacePanel.add(rightColumnPanel, constraints);
        } else {
            constraints.gridx = 0;
            constraints.weightx = 1;
            constraints.weighty = 0.46;
            constraints.insets = new Insets(0, 0, 16, 0);
            filesWorkspacePanel.add(leftColumnPanel, constraints);

            constraints.gridy = 1;
            constraints.weighty = 0.54;
            constraints.insets = new Insets(0, 0, 0, 0);
            filesWorkspacePanel.add(rightColumnPanel, constraints);
        }

        filesWorkspacePanel.revalidate();
        filesWorkspacePanel.repaint();
        refreshFileDetails();
    }

    private JPanel buildLogCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 12));
        card.setPreferredSize(new Dimension(300, 150));

        JPanel titlePanel = new JPanel(new BorderLayout(0, 6));
        titlePanel.setOpaque(false);
        titlePanel.add(createSectionTitle("System trace"), BorderLayout.NORTH);
        titlePanel.add(createSectionSubtitle("Messaggi di esito e diagnostica del flusso di firma."), BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        logArea.setBackground(new Color(248, 248, 244));

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createLineBorder(CARD_BORDER));

        card.add(titlePanel, BorderLayout.NORTH);
        card.add(logScrollPane, BorderLayout.CENTER);
        return card;
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
        JLabel activeLabel = new JLabel("Focused asset");
        activeLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        activeLabel.setForeground(TEXT_MUTED);
        rightMeta.add(activeLabel, BorderLayout.NORTH);
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
        fileQueueCombo.setMaximumRowCount(16);

        certificateDetailsArea.setEditable(false);
        certificateDetailsArea.setLineWrap(true);
        certificateDetailsArea.setWrapStyleWord(true);
        certificateDetailsArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        certificateDetailsArea.setForeground(TEXT_PRIMARY);
        certificateDetailsArea.setBackground(new Color(250, 249, 245));
        certificateDetailsArea.setBorder(new EmptyBorder(14, 14, 14, 14));

        styleInput(identityCombo);
        styleInput(fileQueueCombo);
        styleInput(outputField);
        styleBadge(fileCountLabel, new Color(231, 243, 238), new Color(18, 97, 79));
        totalSizeLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        totalSizeLabel.setForeground(ACCENT);

        styleButton(loadCertificatesButton, false);
        styleButton(addFilesButton, true);
        styleButton(removeFileButton, false);
        styleButton(clearFilesButton, false);
        styleButton(signButton, true);
        styleButton(browseOutputButton, false);

        signButton.setPreferredSize(new Dimension(190, 46));
        progressBar.setPreferredSize(new Dimension(170, 18));

        appendLog("Applicazione avviata. Modalita GUI fissata su Windows-MY.");
    }

    private void bindActions() {
        addFilesButton.addActionListener(event -> addFilesFromChooser());
        browseOutputButton.addActionListener(event -> selectOutputDirectory());
        loadCertificatesButton.addActionListener(event -> loadCertificates());
        removeFileButton.addActionListener(event -> removeSelectedFile());
        clearFilesButton.addActionListener(event -> clearFiles());
        signButton.addActionListener(event -> signFiles());
        fileQueueCombo.addActionListener(event -> {
            refreshFileDetails();
            updateActionState();
        });
        identityCombo.addActionListener(event -> {
            refreshCertificateDetails();
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
                    certificateCountLabel.setText(identities.size() + (identities.size() == 1 ? " certificato QES disponibile" : " certificati QES disponibili"));
                    refreshCertificateDetails();
                    setBusy(false, identities.isEmpty() ? "Nessun certificato di firma trovato in Windows-MY." : "Certificati pronti.");

                    if (identities.isEmpty()) {
                        showError("Nessun certificato adatto alla firma trovato nello store Windows.");
                    }
                } catch (Exception ex) {
                    identityCombo.removeAllItems();
                    certificateCountLabel.setText("Nessun certificato QES disponibile");
                    refreshCertificateDetails();
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

        setBusy(true, "Firma in corso su " + files.size() + " file...");
        appendLog("Avvio firma di " + files.size() + " file con certificato " + selectedIdentity.displayName() + ".");

        SwingWorker<List<Path>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Path> doInBackground() throws Exception {
                return signatureService.signFiles(buildConfig(), selectedIdentity.alias(), files, outputDirectory);
            }

            @Override
            protected void done() {
                try {
                    List<Path> results = get();
                    for (Path result : results) {
                        boolean verified = signatureService.verifySignature(result);
                        appendLog("Creato " + result + " | verifica locale: " + (verified ? "OK" : "KO"));
                    }
                    setBusy(false, "Firma completata: " + results.size() + " file prodotti.");
                } catch (Exception ex) {
                    setBusy(false, "Errore durante la firma.");
                    showError(resolveMessage(ex));
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
        if (stagedAssetsListPanel == null) {
            return;
        }

        stagedAssetsListPanel.removeAll();

        if (fileQueueModel.getSize() == 0) {
            JPanel emptyState = new JPanel(new BorderLayout());
            emptyState.setOpaque(false);
            emptyState.setBorder(new EmptyBorder(48, 12, 48, 12));

            JLabel label = new JLabel("SYSTEM.END_OF_LIST", SwingConstants.CENTER);
            label.setFont(new Font("Segoe UI", Font.BOLD, 9));
            label.setForeground(new Color(TEXT_MUTED.getRed(), TEXT_MUTED.getGreen(), TEXT_MUTED.getBlue(), 150));
            emptyState.add(label, BorderLayout.CENTER);

            stagedAssetsListPanel.add(emptyState);
        } else {
            Path selected = (Path) fileQueueCombo.getSelectedItem();
            for (int index = 0; index < fileQueueModel.getSize(); index++) {
                Path path = fileQueueModel.getElementAt(index);
                stagedAssetsListPanel.add(createAssetRow(path, path.equals(selected)));
            }
        }

        stagedAssetsListPanel.revalidate();
        stagedAssetsListPanel.repaint();
    }

    private JPanel createAssetRow(Path path, boolean selected) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(true);
        row.setBackground(selected ? new Color(239, 244, 244) : Color.WHITE);
        row.setBorder(new EmptyBorder(14, 16, 14, 16));

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

        JButton removeButton = new JButton("X");
        removeButton.setFocusPainted(false);
        removeButton.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        removeButton.setForeground(new Color(160, 35, 44));
        removeButton.setBackground(row.getBackground());
        removeButton.setContentAreaFilled(false);
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

    private void refreshCertificateDetails() {
        SigningIdentity identity = (SigningIdentity) identityCombo.getSelectedItem();
        if (identity == null) {
            certificateDetailsArea.setText("Nessun certificato selezionato.\n\nIl programma mostrera solo certificati SIG/QES idonei alla firma conforme.");
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("Certificato selezionato").append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Intestatario: ").append(identity.displayName()).append(System.lineSeparator())
                .append("Profilo: ").append(identity.profileLabel()).append(System.lineSeparator())
                .append("Algoritmo chiave: ").append(identity.keyAlgorithm()).append(System.lineSeparator())
                .append("Thumbprint: ").append(identity.thumbprint()).append(System.lineSeparator())
                .append("Issuer: ").append(extractCommonName(identity.issuer())).append(System.lineSeparator())
                .append("Subject completo: ").append(identity.subject());

        certificateDetailsArea.setText(details.toString());
        certificateDetailsArea.setCaretPosition(0);
    }

    private void updateActionState() {
        boolean hasFiles = fileQueueModel.getSize() > 0;
        boolean hasCertificate = identityCombo.getItemCount() > 0 && identityCombo.getSelectedItem() != null;

        identityCombo.setEnabled(!busy);
        outputField.setEnabled(!busy);
        loadCertificatesButton.setEnabled(!busy);
        addFilesButton.setEnabled(!busy);
        fileQueueCombo.setEnabled(!busy && hasFiles);
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
        int width = Math.min(Math.max((int) (bounds.width * 0.88), 1240), bounds.width);
        int height = Math.min(Math.max((int) (bounds.height * 0.9), 820), bounds.height);
        return new Dimension(width, height);
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

    private void styleInput(JComponent component) {
        component.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        component.setBackground(new Color(0, 0, 0, 0));
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(171, 173, 174, 130)),
                new EmptyBorder(8, 6, 8, 6)
        ));
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
