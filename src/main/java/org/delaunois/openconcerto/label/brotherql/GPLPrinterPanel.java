/*
 * Openconcerto Module for printing labels with BrotherQL printers.
 *
 * Copyright (C) 2024 Cédric de Launois
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.delaunois.openconcerto.label.brotherql;

import com.formdev.flatlaf.FlatDarkLaf;
import org.delaunois.brotherql.BrotherQLConnection;
import org.delaunois.brotherql.BrotherQLErrorType;
import org.delaunois.brotherql.BrotherQLException;
import org.delaunois.brotherql.BrotherQLJob;
import org.delaunois.brotherql.BrotherQLMedia;
import org.delaunois.brotherql.BrotherQLMediaType;
import org.delaunois.brotherql.BrotherQLModel;
import org.delaunois.brotherql.BrotherQLStatus;
import org.delaunois.brotherql.BrotherQLStatusType;
import org.delaunois.brotherql.util.Converter;
import org.delaunois.openconcerto.label.brotherql.graphicspl.GraphicsPL;
import org.openconcerto.sql.model.SQLRowValues;
import org.openconcerto.utils.BaseDirs;
import org.openconcerto.utils.ExceptionHandler;
import org.openconcerto.utils.ProductInfo;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Interface principale d'impression d'étiquettes sur imprimante Brother QL
 *
 * @author Cedric de Launois
 */
public class GPLPrinterPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(GPLPrinterPanel.class.getName());

    private static final int SECOND = 1000;
    private static final int REFRESH_STATUS_DELAY = 2 * SECOND;

    private final HashMap<String, String> mapName = new HashMap<>();
    private final List<String> knownVariables = new ArrayList<>();
    private final Map<String, JTextField> editorMap = new HashMap<>();
    private final Map<String, JLabel> labelMap = new HashMap<>();
    private final Properties properties = new Properties();

    private PrintSwingWorker printSwingWorker = new PrintSwingWorker();
    private StatusSwingWorker statusWorker = new StatusSwingWorker();
    private PreviewLabelSwingWorker previewWorker = new PreviewLabelSwingWorker();

    private boolean printing = false;
    private boolean hasPrinted = false;
    private JLabel topLeftPreviewLabel;
    private JLabel topRightPreviewLabel;
    
    private JPanel previewPanel = new JPanel();
    private JPanel varPanel = new JPanel();
    private JComboBox<Printer> printerComboBox = new JComboBox<>();
    private JLabel mediaIdTextField;
    private JLabel printerStatus;
    private JLabel templateLabelTitle;
    private JCheckBox autocutCheckBox;
    private JComboBox<Template> templateComboBox;
    private JComboBox<String> ditheringComboBox;
    private JComboBox<String> rotationCombobox;
    private JButton printButton;
    private JButton cancelPrintButton;
    private JButton closeButton;
    private JSpinner delay;
    private JSpinner cutEach;
    private JSpinner brightness;
    private JSpinner threshold;
    private List<String> variables = new ArrayList<>();
    private List<SQLRowValues> rowList;
    private List<Template> templates;
    private boolean dithering = true;


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();
            GPLPrinterPanel p = new GPLPrinterPanel();
            p.uiInit(new ArrayList<>());
            JFrame f = new JFrame();
            f.setTitle("Impression d'étiquettes");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setMinimumSize(new Dimension(1000, 480));
            f.setPreferredSize(new Dimension(1000, 650));
            f.setContentPane(p);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    /**
     * Initialise le panneau d'impression
     *
     * @param rowList la liste des articles à imprimer
     */
    protected void uiInit(List<SQLRowValues> rowList) {
        LOGGER.log(Level.INFO, "Starting GPLPrinterPanel");

        if (rowList == null) {
            throw new IllegalArgumentException("rowList is null");
        }

        loadProperties();
        loadVariables();
        templates = new TemplateManager().getAll();

        this.rowList = rowList;
        this.removeAll();

        this.setLayout(new BorderLayout(0, 5));

        JPanel titlePanel = new JPanel();
        JPanel leftPanel = new JPanel();
        JPanel rightPanel = new JPanel();
        JPanel buttonPanel = new JPanel();

        this.add(titlePanel, BorderLayout.PAGE_START);
        this.add(leftPanel, BorderLayout.LINE_START);
        this.add(rightPanel, BorderLayout.CENTER);
        this.add(buttonPanel, BorderLayout.PAGE_END);

        uiInitTitlePanel(titlePanel, rowList);
        uiInitLeftPanel(leftPanel);
        uiInitPreviewPanel(rightPanel);
        uiInitButtonPanel(buttonPanel);

        delay.setValue(Integer.parseInt(this.properties.getOrDefault("delay", "0").toString()));
        autocutCheckBox.setSelected(Boolean.parseBoolean(this.properties.getOrDefault("autocut", "true").toString()));
        cutEach.setValue(Integer.parseInt(this.properties.getOrDefault("cutEach", "1").toString()));
        cutEach.setEnabled(autocutCheckBox.isSelected());
        brightness.setValue(Integer.parseInt(this.properties.getOrDefault("brightness", "100").toString()));
        threshold.setValue(Integer.parseInt(this.properties.getOrDefault("threshold", "50").toString()));
        ditheringComboBox.setSelectedItem(this.properties.getOrDefault("dithering", "Tramage"));
        rotationCombobox.setSelectedItem(this.properties.getOrDefault("rotation", "0"));

        String templateName = this.properties.getProperty("template");
        Template template = templates.stream()
                .filter(t -> Objects.equals(templateName, t.getName()))
                .findAny()
                .orElse(null);

        if (template != null) {
            templateComboBox.setSelectedItem(template);
            changeLabel(template);
        } else if (!templates.isEmpty()) {
            changeLabel(templates.iterator().next());
        }

        closeButton.addActionListener(this::closeButtonActionListener);
        printButton.addActionListener(this::printButtonActionListener);
        cancelPrintButton.addActionListener(this::cancelPrintActionListener);

        statusWorker.execute();
        previewWorker.execute();
    }

    private void uiInitTitlePanel(JPanel titlePanel, List<SQLRowValues> rowList) {
        int numLabels = rowList.size();

        // Title Panel
        titlePanel.setLayout(new GridBagLayout());
        titlePanel.setBackground(new JButton().getBackground());

        // Main Title
        JLabel titleLabel = new JLabel();
        Font titleLabelFont = titleLabel.getFont().deriveFont(Font.BOLD, 15);
        if (titleLabelFont != null) titleLabel.setFont(titleLabelFont);
        titleLabel.setText(numLabels == 0 ? "Impression d'étiquettes (aucune donnée)" :
                "Impression de " + numLabels + " étiquette" + (numLabels > 1 ? "s" : ""));
        titlePanel.add(titleLabel, gbc(0, 0, 1.0, 1.0, new Insets(10, 10, 10, 0)));

        // Sub Title
        JLabel subTitleLabel = new JLabel();
        subTitleLabel.setText("Sélectionnez le modèle et les options d'impression");
        titlePanel.add(subTitleLabel, gbc(0, 1, 1.0, 1.0, new Insets(0, 25, 15, 0)));
    }

    private void uiInitLeftPanel(JPanel leftPanel) {
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.add(Box.createRigidArea(new Dimension(500, 0)));
        
        JPanel statusPanel = new JPanel();
        uiInitStatusPanel(statusPanel);
        statusPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, statusPanel.getPreferredSize().height));

        JPanel printOptionPanel = new JPanel();
        uiInitPrintOptionsPanel(printOptionPanel);
        printOptionPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, printOptionPanel.getPreferredSize().height));
        
        uiInitVarPanel(varPanel);
        varPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, varPanel.getPreferredSize().height));
        
        JPanel scrollPanel = new JPanel();
        scrollPanel.setLayout(new BoxLayout(scrollPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(
                scrollPanel, 
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPanel.add(printOptionPanel);
        scrollPanel.add(varPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPanel.add(Box.createVerticalGlue());
        
        leftPanel.add(statusPanel);
        leftPanel.add(scrollPane);
    }
    
    private void uiInitStatusPanel(JPanel statusPanel) {
        int gridy = 0;
        statusPanel.setLayout(new GridBagLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5,15,0,5));

        // Printer identification
        gridy++;
        addStatusOption(gridy, "Imprimante:", printerComboBox, statusPanel);

        // Label identification
        gridy++;
        mediaIdTextField = new JLabel();
        addStatusOption(gridy, "Etiquette:", mediaIdTextField, statusPanel);

        // Printer Status
        gridy++;
        printerStatus = new JLabel();
        addStatusOption(gridy, "Statut:", printerStatus, statusPanel);
    }
    
    private void addStatusOption(int gridy, String title, JComponent component, JPanel panel) {
        GridBagConstraints gbc;
        
        JLabel printerStatusTitle = new JLabel();
        printerStatusTitle.setText(title);
        gbc = gbc(0, gridy, 0, 1.0, new Insets(5, 10, 5, 10));
        panel.add(printerStatusTitle, gbc);

        gbc = gbc(1, gridy, 1.0, 1.0, null);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, gbc);
    }
    
    private void uiInitPrintOptionsPanel(JPanel printOptionPanel) {
        int gridy = 0;
        GridBagConstraints gbc;
        printOptionPanel.setLayout(new GridBagLayout());
        
        // Title
        JPanel titlePanel = createTitledSeparator("Options d'impression");

        gbc = gbc(0, gridy, 1.0, 0.0, null);
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        printOptionPanel.add(titlePanel, gbc);

        // Label Selection
        gridy++;
        templateLabelTitle = new JLabel();
        templateLabelTitle.setText("Modèle d'étiquette:");
        gbc = gbc(0, gridy, 0.0, 1.0, new Insets(0, 25, 10, 5));
        printOptionPanel.add(templateLabelTitle, gbc);

        templateComboBox = new JComboBox<>(templates.toArray(new Template[0]));
        templateComboBox.addActionListener(this::onChangeLabel);
        templateComboBox.setEditable(false);
        templateComboBox.requestFocus();
        templateComboBox.setToolTipText("Veuillez sélectionner le modèle d'impression.\n" +
                "Seuls les modèles compatibles avec les étiquettes actuellement présentes dans l'imprimante sont affichés.\n" +
                "Tous les modèles sont affichés si l'imprimante est éteinte ou si aucun rouleau d'étiquette n'est présent.");
        gbc = gbc(1, gridy, 1.0, 1.0, new Insets(0, 0, 10, 10));
        gbc.fill = GridBagConstraints.HORIZONTAL;
        printOptionPanel.add(templateComboBox, gbc);

        // Autocut option
        gridy++;
        JPanel cutOptionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        autocutCheckBox = new JCheckBox();
        autocutCheckBox.setSelected(true);
        autocutCheckBox.addActionListener((e) -> cutEach.setEnabled(autocutCheckBox.isSelected()));
        JLabel cutEachLabel = new JLabel("  toutes les: ");
        cutEach = new JSpinner(new SpinnerNumberModel(1, 1, 255, 1));
        cutEach.setPreferredSize(new Dimension(60, cutEach.getPreferredSize().height));
        JLabel cutEachUnit = new JLabel(" étiquettes");
        
        cutOptionPanel.add(autocutCheckBox);
        cutOptionPanel.add(cutEachLabel);
        cutOptionPanel.add(cutEach);
        cutOptionPanel.add(cutEachUnit);
        
        addOption(gridy,
                450, "Couper:",
                "Cocher l'option pour couper automatiquement les étiquettes",
                null, cutOptionPanel, printOptionPanel);

        // Pause Option
        gridy++;
        delay = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 100));
        addOption(gridy,
                152, "Pause:",
                "Pause entre chaque impression d'étiquette, en millisecondes",
                "ms", delay, printOptionPanel);

        // Dithering option
        gridy++;
        brightness = new JSpinner(new SpinnerNumberModel(100, 0, 300, 10));
        threshold = new JSpinner(new SpinnerNumberModel(35, 0, 100, 1));
        
        JPanel brightnessPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        addOption(gridy,
                100, "Luminosité:",
                "Ajuster la luminosité",
                "%", brightness, brightnessPanel);

        JPanel thresholdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        addOption(gridy,
                100, "Seuil:",
                "Seuil de discrimination de luminance entre une couleur blanche et une couleur noire",
                "%", threshold, thresholdPanel);

        ditheringComboBox = new JComboBox<>(new String[] {"Tramage", "Seuil"});
        ditheringComboBox.setPreferredSize(new Dimension(152, ditheringComboBox.getPreferredSize().height));
        ditheringComboBox.addActionListener((e) -> {
            if ("Tramage".equals(ditheringComboBox.getSelectedItem())) {
                thresholdPanel.setVisible(false);    
                brightnessPanel.setVisible(true);
            } else {
                thresholdPanel.setVisible(true);    
                brightnessPanel.setVisible(false);
            }
            onChangeDithering(e);
        });

        JPanel optionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        optionPanel.add(ditheringComboBox);
        optionPanel.add(brightnessPanel);
        optionPanel.add(thresholdPanel);

        gbc = gbc(0, gridy, 1.0, 1.0, null);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        printOptionPanel.add(optionPanel, gbc);

        addOption(gridy,
                400, "Conversion:",
                "Sélectionnez une méthode de conversion des couleurs vers le noir et blanc.\n" +
                        "Si l'image d'origine est déjà en noir et blanc, préférez un \"Seuil\" à 50%.\n" +
                        "Si l'image est en couleur, préférez du \"Tramage\" à 100% de luminance ou plus.",
                null, optionPanel, printOptionPanel);
        
        // Rotation
        gridy++;
        rotationCombobox = new JComboBox<>(new String[] {"-90", "0", "90", "180"});
        rotationCombobox.setSelectedItem("0°");
        rotationCombobox.setPreferredSize(new Dimension(152, rotationCombobox.getPreferredSize().height));
        rotationCombobox.addActionListener((e) -> {
        });
        
        addOption(gridy,
                152, "Rotation:",
                "Rotation (dans le sens des aiguilles d'une montre).\n" +
                        "Ne sont affichées que les rotations respectant le format de l'étiquette actuelle.",
                "degrés", rotationCombobox, printOptionPanel);
    }
    
    private void uiInitVarPanel(JPanel varPanel) {
        // Variables Panel
        varPanel.setLayout(new GridBagLayout());
        varPanel.setVisible(false);

        // Variable Panel Content

        // Variables Panel Title and separator
        JPanel varTitlePanel = createTitledSeparator("Données des étiquettes");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        varPanel.add(varTitlePanel, gbc);

        // Variables fields
        gbc = new GridBagConstraints();
        gbc.gridy = 1;
        for (String v : this.knownVariables) {
            String label = getName(v) + ":";
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(0, 25, 5, 0);
            JLabel varName = new JLabel(label, SwingConstants.LEFT);
            varName.setPreferredSize(templateLabelTitle.getPreferredSize());
            varName.setVisible(false);
            this.labelMap.put(v, varName);
            varPanel.add(varName, gbc);

            gbc.gridx++;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 5, 5, 10);
            JTextField txt = new JTextField(20);
            txt.setVisible(false);
            this.editorMap.put(v, txt);
            varPanel.add(txt, gbc);

            gbc.gridy++;
        }
    }
    
    private void uiInitPreviewPanel(JPanel rightPanel) {
        rightPanel.setLayout(new BorderLayout(0, 0));
        rightPanel.setMinimumSize(new Dimension(250, 0));
        rightPanel.setPreferredSize(new Dimension(250, 0));

        rightPanel.setBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createEtchedBorder(),
                        "Prévisualisation",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        null,
                        null));
        
        previewPanel.setLayout(new GridBagLayout());
        rightPanel.add(previewPanel, BorderLayout.CENTER);
        
        JPanel topPreviewPanel = new JPanel();
        topPreviewPanel.setLayout(new GridBagLayout());
        
        JLabel topCenterPreviewLabel = new JLabel(UIManager.getIcon("Table.ascendingSortIcon"));
        topCenterPreviewLabel.setToolTipText("Direction de l'impression");
        topLeftPreviewLabel = new JLabel("");
        topLeftPreviewLabel.setToolTipText("Taille du modèle en pixels");
        topLeftPreviewLabel.setPreferredSize(new Dimension(150, 20));

        topRightPreviewLabel = new JLabel("");
        topRightPreviewLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        topRightPreviewLabel.setForeground(Color.GREEN);                                            
        topRightPreviewLabel.setPreferredSize(new Dimension(150, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 0.0;
        c.insets = new Insets(0, 10, 0, 10);
        topPreviewPanel.add(topLeftPreviewLabel, c);
        c.gridx++;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 1.0;
        topPreviewPanel.add(topCenterPreviewLabel, c);
        c.gridx++;
        c.anchor = GridBagConstraints.LINE_END;
        c.weightx = 0.0;
        topPreviewPanel.add(topRightPreviewLabel, c);
        rightPanel.add(topPreviewPanel, BorderLayout.NORTH);
    }
    
    private void uiInitButtonPanel(JPanel buttonPanel) {
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.setPreferredSize(new Dimension(buttonPanel.getPreferredSize().width, 50));

        closeButton = new JButton();
        closeButton.setText("Fermer");
        Dimension buttonDimension = new Dimension(120, closeButton.getPreferredSize().height);
        closeButton.setPreferredSize(buttonDimension);
        buttonPanel.add(closeButton);

        printButton = new JButton();
        printButton.setEnabled(false);
        printButton.setText("Imprimer");
        printButton.setPreferredSize(buttonDimension);
        buttonPanel.add(printButton);

        cancelPrintButton = new JButton();
        cancelPrintButton.setVisible(false);
        cancelPrintButton.setText("Arrêter");
        cancelPrintButton.setPreferredSize(buttonDimension);
        buttonPanel.add(cancelPrintButton);
    }

    private void closeButtonActionListener(ActionEvent e) {
        savePrefs();
        statusWorker.cancel(false);
        previewWorker.cancel(false);
        Window w = SwingUtilities.getWindowAncestor((Component) e.getSource());
        w.setVisible(false);
        if (printSwingWorker.getState().equals(SwingWorker.StateValue.STARTED)) {
            try {
                printSwingWorker.get();
            } catch (InterruptedException | ExecutionException ex) {
                // Ignore
            }
        }
        SwingUtilities.getWindowAncestor((Component) e.getSource()).dispose();
    }

    private void printButtonActionListener(ActionEvent e) {
        printButton.setVisible(false);
        printButton.setEnabled(false);
        cancelPrintButton.setVisible(true);
        cancelPrintButton.setEnabled(true);
        printSwingWorker = new PrintSwingWorker();
        printSwingWorker.execute();
    }

    private void cancelPrintActionListener(ActionEvent e) {
        cancelPrintButton.setEnabled(false);
        printSwingWorker.cancel(false);
    }

    private void addOption(int gridy, int width, String title, String tooltip, String unit, JComponent component, JPanel panel) {
        JLabel labelTitle = new JLabel();
        labelTitle.setText(title);
        GridBagConstraints gbc = gbc(0, gridy, 0.0, 1.0,
                new Insets(5, 25, 10, 5));
        panel.add(labelTitle, gbc);

        component.setPreferredSize(new Dimension(width, component.getPreferredSize().height));
        JPanel optionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        optionPanel.add(component);

        if (unit != null && !unit.isEmpty()) {
            optionPanel.add(new JLabel(" " + unit));
        }

        if (tooltip != null && !tooltip.isEmpty()) {
            labelTitle.setToolTipText(tooltip);
            component.setToolTipText(tooltip);
        }

        gbc = gbc(1, gridy, 1.0, 1.0, null);
        panel.add(optionPanel, gbc);
    }

    private JPanel createTitledSeparator(String title) {
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new GridBagLayout());
        JLabel titleLabel = new JLabel();
        Font titleFont = titleLabel.getFont().deriveFont(Font.BOLD);
        if (titleFont != null) {
            titleLabel.setFont(titleFont);
        }
        titleLabel.setText(title);
        
        GridBagConstraints gbc = gbc(0, 0, 0.0, 0.0, new Insets(20, 10, 10, 5));
        titlePanel.add(titleLabel, gbc);

        final JSeparator separator = new JSeparator();
        separator.setEnabled(true);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(23, 0, 10, 10);
        titlePanel.add(separator, gbc);

        return titlePanel;
    }

    private GridBagConstraints gbc(int gridx, int gridy, double weightx, double weighty, Insets insets) {
        return new GridBagConstraints(gridx, gridy, 1, 1, weightx, weighty,
                GridBagConstraints.WEST,
                GridBagConstraints.NONE,
                insets != null ? insets : new Insets(0, 0, 0, 0), 0, 0);
    }

    private void onChangeLabel(ActionEvent e) {
        @SuppressWarnings("unchecked")
        JComboBox<String> cb = (JComboBox<String>) e.getSource();
        changeLabel((Template) cb.getSelectedItem());
    }

    private void onChangeDithering(ActionEvent e) {
        @SuppressWarnings("unchecked")
        JComboBox<String> cb = (JComboBox<String>) e.getSource();
        dithering = "Tramage".equals(cb.getSelectedItem());
    }

    private void changeLabel(Template template) {
        if (template != null) {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(template
                    .getRotations().stream()
                    .sorted()
                    .map(Object::toString)
                    .toArray(String[]::new));
            rotationCombobox.setModel(model);
            variables = getVariables(template.getCode());
            uiRefreshVarPanel(rowList);
            revalidate();
        }
    }
    
    private void uiRefreshVarPanel(List<SQLRowValues> rows) {
        if (variables.isEmpty()) {
            varPanel.setVisible(false);
            return;
        }

        varPanel.setVisible(true);

        // Variables fields
        for (String v : this.knownVariables) {
            JTextField txt = this.editorMap.get(v);
            JLabel varName = this.labelMap.get(v);
            if (this.variables.contains(v)) {
                if (rows != null && !rows.isEmpty()) {
                    String value = getValueAsString(rows.get(0), v);
                    txt.setText(value);
                    txt.setEditable(value == null);
                }
                varName.setVisible(true);
                txt.setVisible(true);
            } else {
                varName.setVisible(false);
                txt.setVisible(false);
            }
        }
    }

    protected List<BufferedImage> renderLabels(Template template) {
        Map<String, String> values = new HashMap<>();
        this.editorMap.forEach((key, value) -> values.put(key, value.getText()));

        List<BufferedImage> images = new ArrayList<>();
        try {
            if (rowList.isEmpty()) {
                // Etiquette sans données
                final String code = createFilledCode(template.getCode(), values);
                GraphicsPL graphicsPL = new GraphicsPL();
                graphicsPL.load(code, null);
                BufferedImage image = graphicsPL.createImage(1);
                images.add(image);

            } else {
                for (SQLRowValues row : rowList) {
                    Map<String, Object> rowValues = row.getAbsolutelyAll();
                    rowValues.forEach((k, v) -> {
                        if (v != null) {
                            values.put(k, v.toString());
                        }
                    });
                    final String code = createFilledCode(template.getCode(), values);
                    GraphicsPL graphicsPL = new GraphicsPL();
                    graphicsPL.load(code, null);
                    BufferedImage image = graphicsPL.createImage(1);
                    images.add(image);
                }
            }
        } catch (Exception ex) {
            printerStatus.setText("Erreur de génération des étiquettes : " + ex.getMessage());
        }
        return images;
    }

    private String formatPrinterStatus(BrotherQLStatus status) {
        return formatPrinterStatus(status, null);
    }

    private String formatPrinterStatus(BrotherQLStatus status, JLabel label) {
        String labelText = "";
        String tooltipText = "";
        if (status != null) {
            if (BrotherQLStatusType.ERROR_OCCURRED.equals(status.getStatusType())) {
                EnumSet<BrotherQLErrorType> errors = status.getErrors();
                labelText = errors.stream()
                        .map(BrotherQLErrorType::getMessage)
                        .collect(Collectors.joining(", "));
            } else {
                labelText = status.getStatusType().message;
                tooltipText = status.getExceptionMessage();
            }
        }
        if (label != null) {
            label.setText(labelText);
            label.setToolTipText(tooltipText);
        }
        return labelText;
    }

    private String formatPrintingStatus(Integer currentPage, BrotherQLStatus status, BrotherQLJob job) {
        if (status == null) {
            return "Aucun statut d'impression reçu. Imprimante déconnectée ?";
        }
        if (BrotherQLStatusType.ERROR_OCCURRED.equals(status.getStatusType())) {
            EnumSet<BrotherQLErrorType> errors = status.getErrors();
            return "Erreur durant l'impression: " + errors.stream()
                    .map(BrotherQLErrorType::getMessage)
                    .collect(Collectors.joining(", "));
        } else if (currentPage != null && job != null) {
            return "Impression étiquette " + (currentPage + 1) + " / " + job.getImages().size();
        }
        return status.getStatusType().message;
    }

    private String formatMediaStatus(BrotherQLStatus status) {
        String media = status.getMediaType().name;
        if (BrotherQLMediaType.NO_MEDIA.equals(status.getMediaType())
                || BrotherQLMediaType.UNKNOWN.equals(status.getMediaType())) {
            return media;
        }

        media += " " + status.getMediaWidth();
        if (BrotherQLMediaType.DIE_CUT_LABEL.equals(status.getMediaType())) {
            media += " x " + status.getMediaLength();
        }
        media += " mm";

        return media;
    }

    private String formatMediaStatusTootlip(BrotherQLStatus status) {
        String mediaName = status.getMediaType().name;
        if (BrotherQLMediaType.NO_MEDIA.equals(status.getMediaType())
                || BrotherQLMediaType.UNKNOWN.equals(status.getMediaType())) {
            return mediaName;
        }

        BrotherQLMedia media = BrotherQLMedia.identify(status);
        String tooltip = "Un modèle compatible avec ce type d'étiquette et l'imprimante actuelle\ndoit avoir une largeur de ";
        tooltip += media.bodyWidthPx + "px ";
        if (BrotherQLMediaType.CONTINUOUS_LENGTH_TAPE.equals(status.getMediaType())) {
            tooltip += "pour une hauteur variable comprise\nentre ";
            tooltip += status.getModel().clMinLengthPx;
            tooltip += "px et ";
            tooltip += status.getModel().clMaxLengthPx;
            tooltip += "px.";
        } else {
            tooltip += "et une hauteur de " + media.bodyLengthPx + "px";
        }

        return tooltip;
    }

    private BrotherQLStatus getPrinterStatus() {
        updatePrinterList();
        Printer printer = (Printer) printerComboBox.getSelectedItem();
        BrotherQLConnection connection = new BrotherQLConnection(printer == null ? null : printer.getUri());

        try (connection) {
            try {
                connection.open();
            } catch (Exception e) {
                return new BrotherQLStatus(null, null, e.getMessage());
            }
            return connection.requestDeviceStatus();
        } catch (Exception e) {
            return new BrotherQLStatus(null, null, e.getMessage());
        }
    }

    protected BufferedImage renderSingleLabel(Template template) {
        if (template == null) {
            return null;
        }

        Map<String, String> values = new HashMap<>();
        this.editorMap.forEach((key, value) -> values.put(key, value.getText()));
        String code = createFilledCode(template.getCode(), values);
        GraphicsPL g = new GraphicsPL();
        try {
            g.load(code, null);             
            BrotherQLJob job = new BrotherQLJob()
                    .setBrightness(((Integer) brightness.getValue()) / 100.0f)
                    .setThreshold(Float.parseFloat(threshold.getValue().toString()) / 100.0f)
                    .setRotate(rotationCombobox.getSelectedItem() == null ? 0 : Integer.parseInt(String.valueOf(rotationCombobox.getSelectedItem())))
                    .setDpi600(template.isHighdpi())
                    .setDither(dithering)
                    .setImages(List.of(g.createImage(1)));
            List<BufferedImage> previews = BrotherQLConnection.raster(job);
            return previews.iterator().next();

        } catch (ParserConfigurationException | SAXException | IOException e1) {
            return null;
        }
    }

    private String createFilledCode(String template, Map<String, String> values) {
        final BufferedReader reader = new BufferedReader(new StringReader(template));
        final StringBuilder builder = new StringBuilder();

        try {
            String line = reader.readLine();
            while (line != null) {
                if (line.contains("${")) {

                    for (String v : values.keySet()) {
                        if (line.contains("${" + v + "}")) {
                            final String value = values.get(v);
                            line = line.replace("${" + v + "}", value);
                        }
                    }

                }
                builder.append(line);
                builder.append("\n");
                line = reader.readLine();
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error while generating label code", e);
        }

        return builder.toString();
    }

    public String getValueAsString(SQLRowValues row, String variableName) {
        if (row == null) {
            return null;
        }
        switch (variableName) {
            case "product.code":
                return row.getString("CODE");
            case "product.name":
                return row.getString("NOM");
            case "product.sku":
                return row.getString("SKU");
            case "product.ean13":
                return row.getString("CODE_BARRE");
            case "product.price":
                return new DecimalFormat("#0.00").format(row.getBigDecimal("PV_HT"));
            case "product.pricewithtax":
                return new DecimalFormat("#0.00").format(row.getBigDecimal("PV_TTC"));
            case "product.material":
                return row.getString("MATIERE");
            case "product.color":
                if (row.getTable().contains("ID_ARTICLE_DECLINAISON_COULEUR")) {
                    if (row.getObject("ID_ARTICLE_DECLINAISON_COULEUR") != null && !row.isForeignEmpty("ID_ARTICLE_DECLINAISON_COULEUR")) {
                        return row.getForeign("ID_ARTICLE_DECLINAISON_COULEUR").getString("NOM");
                    }
                }
                break;
            case "product.size":
                if (row.getTable().contains("ID_ARTICLE_DECLINAISON_TAILLE")) {
                    if (row.getObject("ID_ARTICLE_DECLINAISON_TAILLE") != null && !row.isForeignEmpty("ID_ARTICLE_DECLINAISON_TAILLE")) {
                        return row.getForeign("ID_ARTICLE_DECLINAISON_TAILLE").getString("NOM");
                    }
                }
                break;
        }
        return "";
    }

    private String getName(String variableName) {
        String n = this.mapName.get(variableName);
        if (n == null) {
            return variableName;
        }
        return n;
    }

    private void loadVariables() {
        this.knownVariables.clear();
        this.knownVariables.add("product.code");
        this.knownVariables.add("product.name");
        this.knownVariables.add("product.material");
        this.knownVariables.add("product.sku");
        this.knownVariables.add("product.color");
        this.knownVariables.add("product.size");
        this.knownVariables.add("product.ean13");
        this.knownVariables.add("product.price");
        this.knownVariables.add("product.pricewithtax");

        this.mapName.clear();
        this.mapName.put("product.name", "Nom");
        this.mapName.put("product.code", "Code");
        this.mapName.put("product.ean13", "Code à barres");
        this.mapName.put("product.price", "Prix HT");
        this.mapName.put("product.pricewithtax", "Prix TTC");
        this.mapName.put("product.treatment", "Traitement");
        this.mapName.put("product.origin", "Origine");
        this.mapName.put("product.batch", "Lot");
        this.mapName.put("product.size", "Taille");
        this.mapName.put("product.color", "Couleur");
        this.mapName.put("product.material", "Matière");
    }

    private List<String> getVariables(String str) {
        final List<String> result = new ArrayList<>();
        if (str == null || str.length() < 4) {
            return result;
        }
        final int l = str.length() - 1;
        int start = 0;
        boolean inName = false;
        for (int i = 0; i < l; i++) {
            char c1 = str.charAt(i);
            char c2 = str.charAt(i + 1);
            if (!inName) {
                if (c1 == '$' && c2 == '{') {
                    start = i + 2;
                    inName = true;
                }
            } else if (c2 == '}') {
                final int stop = i + 1;
                String v = str.substring(start, stop);
                result.add(v);
                inName = false;
            }
        }
        return result;
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(1000, 600);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1000, 600);
    }

    public void savePrefs() {
        this.properties.put("autocut", String.valueOf(this.autocutCheckBox.isSelected()));
        this.properties.put("delay", this.delay.getValue().toString());
        this.properties.put("cutEach", this.cutEach.getValue().toString());
        this.properties.put("brightness", this.brightness.getValue().toString());
        this.properties.put("threshold", this.threshold.getValue().toString());
        this.properties.put("dithering", this.ditheringComboBox.getSelectedItem());
        this.properties.put("rotation", this.rotationCombobox.getSelectedItem());
        if (this.templateComboBox.getSelectedItem() != null) {
            this.properties.put("template", ((Template)this.templateComboBox.getSelectedItem()).getName());
        } else {
            this.properties.remove("template");
        }

        try {
            // Save Prefs
            try (FileOutputStream out = new FileOutputStream(getPrefFile())) {
                this.properties.store(out, "");
            }
        } catch (Exception e1) {
            ExceptionHandler.handle(null, "Erreur de sauvegarde de " + getPrefFile().getAbsolutePath(), e1);
        }
    }

    private void loadProperties() {
        final File file = getPrefFile();
        LOGGER.log(Level.FINE, "Loading properties from {0}", file.getAbsolutePath());
        if (file.exists()) {
            try {
                this.properties.load(new FileInputStream(file));
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error loading properties", e);
            }
        }
    }

    private File getPrefFile() {
        final File prefsFolder = BaseDirs.create(ProductInfo.getInstance()).getPreferencesFolder();
        if (!prefsFolder.exists()) {
            if (!prefsFolder.mkdirs()) {
                ExceptionHandler.handle(null, "Impossible de créer le répertoire " + prefsFolder.getAbsolutePath(), null);
            }
        }
        return new File(prefsFolder, "labels.brotherql.properties");
    }

    private void updatePrinterList() {
        Printer selected = (Printer) printerComboBox.getSelectedItem();
        Set<Printer> currentPrinters = new HashSet<>();
        for (int i = 0; i < printerComboBox.getItemCount(); i++) {
            currentPrinters.add(printerComboBox.getItemAt(i));
        }
        
        List<String> deviceUriList;
        List<Printer> printerList;
        try {
            deviceUriList = BrotherQLConnection.listDevices();
        } catch (BrotherQLException e) {
            // Ignore
            return;
        }
        
        if (deviceUriList.isEmpty()) {
            printerComboBox.removeAllItems();
            return;
        } else {
            printerList = deviceUriList.stream().map(Printer::new).collect(Collectors.toList());
        }
        
        printerList.stream()
                .filter(printer -> !currentPrinters.contains(printer))
                .findAny()
                .ifPresent((uri) -> {
                    Printer[] deviceUriArray = printerList.toArray(new Printer[0]);
                    DefaultComboBoxModel<Printer> model = new DefaultComboBoxModel<>(deviceUriArray);
                    printerComboBox.setModel(model);
                });
        
        if (selected != null) {
            printerComboBox.setSelectedItem(selected);
        } else {
            printerComboBox.setSelectedIndex(0);
        }
    }
    
    /**
     * Swing worker responsable de la récupération et de la mise à jour du statut de l'imprimante.
     */
    private class StatusSwingWorker extends SwingWorker<Boolean, BrotherQLStatus> {
        
        private BrotherQLMedia currentMedia = null;
        
        @Override
        protected Boolean doInBackground() throws Exception {
            while (!isCancelled()) {
                if (!printing) {
                    publish(getPrinterStatus());
                }
                TimeUnit.MILLISECONDS.sleep(REFRESH_STATUS_DELAY);
            }

            return true;
        }

        @Override
        protected void process(List<BrotherQLStatus> chunks) {
            BrotherQLStatus status = chunks.get(chunks.size() - 1);
            boolean ready = BrotherQLStatusType.READY.equals(status.getStatusType());
            if (!ready) {
                formatPrinterStatus(status, printerStatus);
                hasPrinted = false;
            } else if (!hasPrinted) {
                formatPrinterStatus(status, printerStatus);
            }

            mediaIdTextField.setText(formatMediaStatus(status));
            mediaIdTextField.setToolTipText(formatMediaStatusTootlip(status));
            BrotherQLMedia newMedia = BrotherQLMedia.identify(status);
            if (!Objects.equals(newMedia, currentMedia)) {
                currentMedia = newMedia;
                updateLabelComboBox(currentMedia);
                changeLabel((Template) templateComboBox.getSelectedItem());
            }
            printButton.setEnabled(BrotherQLStatusType.READY.equals(status.getStatusType()));
        }

        private void updateLabelComboBox(BrotherQLMedia media) {
            Template currentTemplate = (Template) templateComboBox.getSelectedItem();
            List<Template> compatibleTemplates = templates
                    .stream()
                    .filter(template -> fillCompatibility(template, media))
                    .sorted(Comparator.comparing(Template::getName))
                    .collect(Collectors.toList());

            DefaultComboBoxModel<Template> model = new DefaultComboBoxModel<>(compatibleTemplates.toArray(new Template[0]));
            templateComboBox.setModel(model);

            if (!compatibleTemplates.contains(currentTemplate)) {
                Template firstTemplate = compatibleTemplates.iterator().next();
                templateComboBox.setSelectedItem(firstTemplate);
            } else {
                templateComboBox.setSelectedItem(currentTemplate);
            }
        }
    }

    private boolean fillCompatibility(Template template, BrotherQLMedia media) {
        int templateWidth = template.getWidth();
        int templateHeight = template.getHeight();
        boolean compatible = false;
        template.getRotations().clear();
        
        if (media == null) {
            template.addRotation(0).addRotation(90).addRotation(180).addRotation(270);
            return true;
        }
        
        if (media.mediaType.equals(BrotherQLMediaType.CONTINUOUS_LENGTH_TAPE)) {
            if (templateWidth == media.bodyWidthPx ) {
                template.addRotation(0).addRotation(180);
                compatible = true;
                
            } else if (templateWidth == media.bodyWidthPx * 2) {
                template.addRotation(0).addRotation(180);
                template.setHighdpi(true);
                compatible = true;
            }
            
            if (templateHeight == media.bodyWidthPx) {
                template.addRotation(90).addRotation(270);
                compatible = true;
            } else if (templateHeight == media.bodyWidthPx * 2) {
                template.setHighdpi(true);
                template.addRotation(90).addRotation(270);
                compatible = true;
            }
            
        } else {
            if (templateWidth == media.bodyWidthPx && templateHeight == media.bodyLengthPx) {
                template.addRotation(0).addRotation(180);
                compatible = true;
            } else if (templateWidth == media.bodyWidthPx * 2 && templateHeight == media.bodyLengthPx * 2) {
                template.addRotation(0).addRotation(180);
                template.setHighdpi(true);
                compatible = true;
            } 
            
            if (templateHeight == media.bodyWidthPx && templateWidth == media.bodyLengthPx) {
                template.addRotation(90).addRotation(270);
                compatible = true;
            } else if (templateHeight == media.bodyWidthPx * 2 && templateWidth == media.bodyLengthPx * 2) {
                template.setHighdpi(true);
                template.addRotation(90).addRotation(270);
                compatible = true;
            }
        }
        
        return compatible;
    }

    /**
     * Swing Worker responsable de la mise à jour de la prévisualisation de l'étiquette
     */
    private class PreviewLabelSwingWorker extends SwingWorker<Boolean, Void> {
        
        private long lastupdate = 0;
        private int previousMaxWidth = 0;
        private int previousMaxHeight = 0;
        private Template previousTemplate = null;
        
        @Override
        protected Boolean doInBackground() throws Exception {
            while (!isCancelled()) {
                if (!printing) {
                    publish();
                }
                TimeUnit.MILLISECONDS.sleep(50);
            }
            return true;
        }

        @Override
        protected void process(List<Void> chunks) {
            Template template = (Template) templateComboBox.getSelectedItem();
            if (template == null) {
                return;
            }

            int maxHeight = previewPanel.getHeight() - 20;
            int maxWidth = previewPanel.getWidth() - 20;
            if (System.currentTimeMillis() - lastupdate < 200
                    && maxHeight == previousMaxHeight && maxWidth == previousMaxWidth && template.equals(previousTemplate)) {
                return;
            }
            lastupdate = System.currentTimeMillis();
            previousMaxHeight = maxHeight;
            previousMaxWidth = maxWidth;
            previousTemplate = template;

            BufferedImage previewImage = renderSingleLabel(template);
            if (previewImage != null) {
                if (template.isHighdpi()) {
                    previewImage = Converter.scale(previewImage, previewImage.getWidth() * 2, previewImage.getHeight());
                }
                JLabel preview = new JLabel();
                ImageIcon ii = new ImageIcon(previewImage);
                ImageIcon iiSized;
                if (previewImage.getHeight() > previewImage.getWidth()) {
                    iiSized = new ImageIcon(ii.getImage().getScaledInstance(-1, maxHeight, Image.SCALE_REPLICATE));
                } else {
                    iiSized = new ImageIcon(ii.getImage().getScaledInstance(maxWidth, -1, java.awt.Image.SCALE_SMOOTH));
                }
                preview.setIcon(iiSized);
                topLeftPreviewLabel.setText(previewImage.getWidth() + " x " + previewImage.getHeight());
                if (template.isHighdpi()) {
                    topRightPreviewLabel.setText("High DPI");
                    topRightPreviewLabel.setToolTipText("Cette étiquette sera imprimée\nen haute résolution\n(600 dpi dans le sens de l'impression");
                } else {
                    topRightPreviewLabel.setText("");
                    topRightPreviewLabel.setToolTipText(null);
                }
                previewPanel.removeAll();
                previewPanel.add(preview);
                revalidate();
                repaint();
            }
        }
    }

    /**
     * Swing Worker responsable de l'impression
     */
    private class PrintSwingWorker extends SwingWorker<Boolean, String> {

        @Override
        protected Boolean doInBackground() {
            Template template = (Template) templateComboBox.getSelectedItem();
            if (template == null) {
                return true;
            }

            printing = true;
            hasPrinted = true;
            publish("Impression en cours...");
            savePrefs();
            
            List<BufferedImage> labels = renderLabels(template);

            try {
                BrotherQLJob job = new BrotherQLJob()
                        .setAutocut(autocutCheckBox.isSelected())
                        .setCutEach((Integer) cutEach.getValue())
                        .setDelay((Integer) delay.getValue())
                        .setDpi600(template.isHighdpi())
                        .setBrightness(((Integer) brightness.getValue()) / 100.0f)
                        .setImages(labels);

                print(job);
                if (isCancelled()) {
                    publish("Impression annulée");
                } else {
                    publish("Impression terminée");
                }
            } catch (Exception ex) {
                publish("Erreur d'impression locale : " + ex.getMessage());
            } finally {
                printing = false;
            }

            return true;
        }

        @Override
        protected void process(List<String> chunks) {
            String status = chunks.get(chunks.size() - 1);
            printerStatus.setText(status);
            repaint();
        }

        @Override
        protected void done() {
            printButton.setVisible(true);
            cancelPrintButton.setVisible(false);
        }

        private void print(BrotherQLJob job) {
            Printer printer = (Printer) printerComboBox.getSelectedItem();
            BrotherQLConnection connection = new BrotherQLConnection(printer == null ? null : printer.getUri());
            try {
                connection.open();
                BrotherQLStatus status = connection.requestDeviceStatus();
                if (status.getStatusType() != BrotherQLStatusType.READY) {
                    publish(formatPrinterStatus(status));
                    return;
                }

                BrotherQLModel model = connection.getModel();
                if (model != null) {
                    connection.sendJob(job, (page, s) -> {
                        publish(formatPrintingStatus(page, s, job));
                        return !isCancelled();
                    });
                }
            } catch (BrotherQLException e) {
                publish(formatPrinterStatus(new BrotherQLStatus(null, connection.getModel(), e.getMessage())));

            } finally {
                connection.close();
            }
        }

    }

}
