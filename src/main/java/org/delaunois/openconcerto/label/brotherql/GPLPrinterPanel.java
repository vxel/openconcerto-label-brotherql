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

import com.formdev.flatlaf.FlatDarculaLaf;
import org.delaunois.openconcerto.label.brotherql.graphicspl.Dither;
import org.delaunois.openconcerto.label.brotherql.graphicspl.GraphicsPL;
import org.delaunois.openconcerto.label.brotherql.usb.BrotherQLErrorType;
import org.delaunois.openconcerto.label.brotherql.usb.BrotherQLJob;
import org.delaunois.openconcerto.label.brotherql.usb.BrotherQLManager;
import org.delaunois.openconcerto.label.brotherql.usb.BrotherQLMedia;
import org.delaunois.openconcerto.label.brotherql.usb.BrotherQLMediaType;
import org.delaunois.openconcerto.label.brotherql.usb.BrotherQLPrinterId;
import org.delaunois.openconcerto.label.brotherql.usb.BrotherQLStatus;
import org.delaunois.openconcerto.label.brotherql.usb.BrotherQLStatusType;
import org.openconcerto.sql.model.SQLRowValues;
import org.openconcerto.utils.BaseDirs;
import org.openconcerto.utils.ExceptionHandler;
import org.openconcerto.utils.ProductInfo;
import org.usb4java.LibUsbException;
import org.w3c.dom.Element;
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
import java.io.InputStream;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
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
    private final TemplateManager templateManager = new TemplateManager();

    private PrintSwingWorker printSwingWorker = new PrintSwingWorker();
    private StatusSwingWorker statusWorker = new StatusSwingWorker();
    private PreviewLabelSwingWorker previewWorker = new PreviewLabelSwingWorker();

    private boolean printing = false;
    private boolean hasPrinted = false;
    private JPanel previewPanel = new JPanel();
    private JPanel varPanel = new JPanel();
    private JLabel printerIdTextField;
    private JLabel mediaIdTextField;
    private JLabel printerStatus;
    private JLabel templateLabelTitle;
    private JCheckBox autocutCheckBox;
    private JCheckBox compatibleCheckBox;
    private JComboBox<String> templateLabelComboBox;
    private JButton printButton;
    private JButton cancelPrintButton;
    private JButton closeButton;
    private JSpinner delay;
    private JSpinner cutEach;
    private String templateName;
    private List<String> variables = new ArrayList<>();
    private List<SQLRowValues> rowList;


    public static void main(String[] args) {
        InputStream stream = GPLPrinterPanel.class.getResourceAsStream("/logging.properties");
        try {
            LogManager.getLogManager().readConfiguration(stream);
        } catch (IOException e) {
            // Ignore
        }

        SwingUtilities.invokeLater(() -> {
            FlatDarculaLaf.setup();
            GPLPrinterPanel p = new GPLPrinterPanel();
            p.initUI(new ArrayList<>());
            JFrame f = new JFrame();
            f.setTitle("Impression d'étiquettes");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setMinimumSize(new Dimension(1000, 600));
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
    protected void initUI(List<SQLRowValues> rowList) {
        LOGGER.log(Level.INFO, "Starting GPLPrinterPanel");

        if (rowList == null) {
            throw new IllegalArgumentException("rowList is null");
        }
        
        loadProperties();
        loadVariables();
        
        this.rowList = rowList;
        this.removeAll();
        this.setLayout(new GridBagLayout());

        uiInitTitleSection(rowList.size());
        uiInitStatusPanel();
        uiInitPrintOptionsPanel();
        uiInitVarPanel();
        uiInitPreviewPanel();
        uiInitButtonSection();
        
        delay.setValue(Integer.parseInt(this.properties.getOrDefault("delay", "0").toString()));
        compatibleCheckBox.setSelected(Boolean.parseBoolean(this.properties.getOrDefault("compatible", "true").toString()));
        autocutCheckBox.setSelected(Boolean.parseBoolean(this.properties.getOrDefault("autocut", "true").toString()));
        cutEach.setValue(Integer.parseInt(this.properties.getOrDefault("cutEach", "1").toString()));
        cutEach.setEnabled(autocutCheckBox.isSelected());
        
        String template = this.properties.getProperty("template");
        if (template != null) {
            templateLabelComboBox.setSelectedItem(template);
            changeLabel(template);
        } else if (!templateManager.getNames().isEmpty()) {
            changeLabel(templateManager.getNames().iterator().next());
        }
        
        closeButton.addActionListener(this::closeButtonActionListener);
        printButton.addActionListener(this::printButtonActionListener);
        cancelPrintButton.addActionListener(this::cancelPrintActionListener);        

        statusWorker.execute();
        previewWorker.execute();
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

    private void uiInitTitleSection(int numLabels) {
        GridBagConstraints gbc;

        // Title Panel
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new GridBagLayout());
        titlePanel.setBackground(new JButton().getBackground());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.BOTH;
        this.add(titlePanel, gbc);

        // Main Title
        JLabel titleLabel = new JLabel();
        Font titleLabelFont = titleLabel.getFont().deriveFont(Font.BOLD, 15);
        if (titleLabelFont != null) titleLabel.setFont(titleLabelFont);
        titleLabel.setText(numLabels == 0 ? "Impression d'étiquettes (aucune donnée)" :
                "Impression de " + numLabels + " étiquette" + (numLabels > 1 ? "s" : ""));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(10, 10, 10, 0);
        titlePanel.add(titleLabel, gbc);

        // Sub Title
        JLabel subTitleLabel = new JLabel();
        subTitleLabel.setText("Sélectionnez le modèle et les options d'impression");
        subTitleLabel.setVerticalAlignment(1);
        subTitleLabel.setVerticalTextPosition(0);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 25, 15, 0);
        titlePanel.add(subTitleLabel, gbc);
    }

    private void uiInitStatusPanel() {
        // Status Panel
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.ipady = 30;
        gbc.insets = new Insets(10, 15, 10, 10);
        this.add(statusPanel, gbc);

        // Printer identification
        JLabel printerIdTitle = new JLabel();
        printerIdTitle.setText("Imprimante:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.ipadx = 5;
        gbc.insets = new Insets(0, 10, 0, 0);
        statusPanel.add(printerIdTitle, gbc);

        printerIdTextField = new JLabel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        statusPanel.add(printerIdTextField, gbc);

        // Label identification
        JLabel mediaIdTitle = new JLabel();
        mediaIdTitle.setText("Etiquette:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.ipadx = 5;
        gbc.insets = new Insets(0, 10, 0, 0);
        statusPanel.add(mediaIdTitle, gbc);

        mediaIdTextField = new JLabel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        statusPanel.add(mediaIdTextField, gbc);

        // Printer Status
        JLabel printerStatusTitle = new JLabel();
        printerStatusTitle.setText("Statut:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.ipadx = 5;
        gbc.insets = new Insets(0, 10, 0, 0);
        statusPanel.add(printerStatusTitle, gbc);

        printerStatus = new JLabel();
        printerStatus.setText("Imprimante non connectée");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        statusPanel.add(printerStatus, gbc);
    }

    private void uiInitPreviewPanel() {
        previewPanel.setLayout(new GridBagLayout());
        previewPanel.setMinimumSize(new Dimension(250, 0));
        previewPanel.setPreferredSize(new Dimension(250, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 1;
        gbc.gridheight = 5;
        gbc.weightx = 0.5;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 5, 5, 15);
        this.add(previewPanel, gbc);

        previewPanel.setBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createEtchedBorder(),
                        "Prévisualisation",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        null,
                        null));
    }

    private void uiInitPrintOptionsPanel() {
        GridBagConstraints gbc;

        // Title
        JPanel printOptionTitlePanel = new JPanel();
        printOptionTitlePanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        this.add(printOptionTitlePanel, gbc);

        JLabel printOptionTitle = new JLabel();
        Font printOptionTitleFont = printOptionTitle.getFont().deriveFont(Font.BOLD);
        if (printOptionTitleFont != null) printOptionTitle.setFont(printOptionTitleFont);
        printOptionTitle.setText("Options d'impression");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 20, 0, 5);
        printOptionTitlePanel.add(printOptionTitle, gbc);

        final JSeparator separator1 = new JSeparator();
        separator1.setEnabled(true);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 0, 10);
        printOptionTitlePanel.add(separator1, gbc);

        // Panel
        JPanel printOptionPanel = new JPanel();
        printOptionPanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.ipady = 30;
        gbc.insets = new Insets(5, 15, 10, 10);
        this.add(printOptionPanel, gbc);

        // Label Selection
        templateLabelTitle = new JLabel();
        templateLabelTitle.setText("Modèle d'étiquette:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.ipadx = 5;
        gbc.insets = new Insets(0, 10, 0, 5);
        printOptionPanel.add(templateLabelTitle, gbc);

        String[] templateNames = templateManager.getNames().toArray(new String[0]);
        templateLabelComboBox = new JComboBox<>(templateNames);
        templateLabelComboBox.addActionListener(this::onChangeLabel);
        templateLabelComboBox.setEditable(false);
        templateLabelComboBox.requestFocus();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        printOptionPanel.add(templateLabelComboBox, gbc);

        // Compatible Label option
        String tooltip;
        JLabel compatibleTitle = new JLabel();
        compatibleTitle.setText("Compatible:");
        tooltip = "Cocher l'option pour n'afficher que les modèles d'étiquette compatibles\n" +
                "avec le rouleau d'étiquette actuellement présent dans l'imprimante";
        compatibleTitle.setToolTipText(tooltip);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.ipadx = 5;
        gbc.insets = new Insets(0, 10, 0, 5);
        printOptionPanel.add(compatibleTitle, gbc);

        compatibleCheckBox = new JCheckBox();
        compatibleCheckBox.setSelected(true);
        compatibleCheckBox.setText("");
        compatibleCheckBox.setToolTipText(tooltip);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        printOptionPanel.add(compatibleCheckBox, gbc);

        // Autocut option
        JLabel autocutTitle = new JLabel();
        autocutTitle.setText("Couper:");
        tooltip = "Cocher l'option pour couper automatiquement les étiquettes";
        autocutTitle.setToolTipText(tooltip);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.ipadx = 5;
        gbc.insets = new Insets(0, 10, 0, 5);
        printOptionPanel.add(autocutTitle, gbc);

        autocutCheckBox = new JCheckBox();
        autocutCheckBox.setSelected(true);
        autocutCheckBox.setText("");
        autocutCheckBox.setToolTipText(tooltip);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        printOptionPanel.add(autocutCheckBox, gbc);
        autocutCheckBox.addActionListener(e -> cutEach.setEnabled(autocutCheckBox.isSelected()));

        // Cut each Option
        JLabel cutEachTitle = new JLabel();
        cutEachTitle.setText("Couper toutes les:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.ipadx = 5;
        gbc.insets = new Insets(0, 10, 0, 5);
        printOptionPanel.add(cutEachTitle, gbc);

        cutEach = new JSpinner(new SpinnerNumberModel(1, 1, 255, 1));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        cutEach.setPreferredSize(new Dimension(100, cutEach.getPreferredSize().height));
        JPanel cutEachPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cutEachPanel.add(cutEach);
        cutEachPanel.add(new JLabel(" étiquettes"));
        printOptionPanel.add(cutEachPanel, gbc);

        // Pause Option
        JLabel pauseTitle = new JLabel();
        pauseTitle.setText("Pause:");
        tooltip = "Pause entre chaque impression d'étiquette, en millisecondes";
        pauseTitle.setToolTipText(tooltip);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.ipadx = 5;
        gbc.insets = new Insets(0, 10, 0, 5);
        printOptionPanel.add(pauseTitle, gbc);

        delay = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 100));
        delay.setToolTipText(tooltip);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        delay.setPreferredSize(new Dimension(100, delay.getPreferredSize().height));
        JPanel delayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        delayPanel.add(delay);
        delayPanel.add(new JLabel(" ms"));
        printOptionPanel.add(delayPanel, gbc);
    }

    private void uiInitVarPanel() {
        // Variables Panel
        varPanel.setLayout(new GridBagLayout());
        varPanel.setVisible(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.ipady = 30;
        gbc.insets = new Insets(5, 15, 10, 10);
        this.add(varPanel, gbc);
        
        // Variable Panel Content

        // Variables Panel Title and separator
        JPanel varTitlePanel = new JPanel();
        varTitlePanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        varPanel.add(varTitlePanel, gbc);
        
        JLabel varTitle = new JLabel();
        Font varTitleFont = varTitle.getFont().deriveFont(Font.BOLD);
        if (varTitleFont != null) varTitle.setFont(varTitleFont);
        varTitle.setText("Données des étiquettes");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 10, 5);
        varTitlePanel.add(varTitle, gbc);

        final JSeparator separator2 = new JSeparator();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 2, 2);
        varTitlePanel.add(separator2, gbc);

        // Variables fields
        gbc = new GridBagConstraints();
        gbc.gridy = 1;
        for (String v : this.knownVariables) {
            String label = getName(v) + ":";
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(0, 10, 5, 0);
            JLabel varName = new JLabel(label, SwingConstants.LEFT);
            varName.setPreferredSize(templateLabelTitle.getPreferredSize());
            varName.setVisible(false);
            this.labelMap.put(v, varName);
            varPanel.add(varName, gbc);

            gbc.gridx++;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            JTextField txt = new JTextField(20);
            txt.setVisible(false);
            this.editorMap.put(v, txt);
            varPanel.add(txt, gbc);

            gbc.gridy++;
        }
    }

    private void onChangeLabel(ActionEvent e) {
        @SuppressWarnings("unchecked")
        JComboBox<String> cb = (JComboBox<String>)e.getSource();
        changeLabel((String)cb.getSelectedItem());
    }
    
    private void changeLabel(String templateName) {
        this.templateName = templateName;
        if (this.templateName == null) return;
        String template = templateManager.get(templateName);
        if (template != null) {
            variables = getVariables(template);
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
    
    private void uiInitButtonSection() {
        GridBagConstraints gbc;
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 4;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(15, 15, 15, 15);
        this.add(buttonPanel, gbc);

        closeButton = new JButton();
        closeButton.setText("Fermer");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 10, 0, 10);
        buttonPanel.add(closeButton, gbc);

        printButton = new JButton();
        printButton.setEnabled(false);
        printButton.setText("Imprimer");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 10, 0, 0);
        buttonPanel.add(printButton, gbc);

        cancelPrintButton = new JButton();
        cancelPrintButton.setVisible(false);
        cancelPrintButton.setText("Arrêter");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 10, 0, 0);
        buttonPanel.add(cancelPrintButton, gbc);
        
        printButton.setPreferredSize(new Dimension(100, printButton.getPreferredSize().height));
        closeButton.setPreferredSize(printButton.getPreferredSize());
        cancelPrintButton.setPreferredSize(printButton.getPreferredSize());
    }
    
    protected List<BufferedImage> renderLabels(String template) {
        Map<String, String> values = new HashMap<>();
        this.editorMap.forEach((key, value) -> values.put(key, value.getText()));
        
        List<BufferedImage> images = new ArrayList<>();
        try {
            if (rowList.isEmpty()) {
                // Etiquette de test
                images.add(renderSingleLabel(template));
            } else {
                for (SQLRowValues row : rowList) {
                    Map<String, Object> rowValues = row.getAbsolutelyAll();
                    rowValues.forEach((k, v) -> {
                        if (v != null) {
                            values.put(k, v.toString());
                        }
                    });
                    final String code = createFilledCode(template, values);
                    GraphicsPL graphicsPL = new GraphicsPL();
                    graphicsPL.load(code, null);
                    BufferedImage image = graphicsPL.createImage(1);
                    images.add(Dither.floydSteinbergDithering(image));
                }
            }
        } catch (Exception ex) {
            printerStatus.setText("Erreur de génération des étiquettes : " + ex.getMessage());
        }
        return images;
    }
    
    private String formatPrinterId(BrotherQLStatus status) {
        if (status == null || status.getPrinterId() == null) {
            return "Imprimante éteinte ou non reconnue";
        }
        return status.getPrinterId().name;
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
            tooltip += status.getPrinterId().clMinLengthPx;
            tooltip += "px et ";
            tooltip += status.getPrinterId().clMaxLengthPx;
            tooltip += "px.";
        } else {
            tooltip += "et une hauteur de " + media.bodyLengthPx + "px";
        }

        return tooltip;
    }

    private BrotherQLStatus getPrinterStatus() {
        BrotherQLManager manager = new BrotherQLManager();
        try {
            manager.open();
        } catch (Exception e) {
            return new BrotherQLStatus(null, null, e.getMessage());
        }
        
        try {
            return manager.requestDeviceStatus();
        } catch (Exception e) {
            return new BrotherQLStatus(null, null, e.getMessage());
        } finally {
            manager.close();
        }
    }

    protected BufferedImage renderSingleLabel(String template) {
        if (template == null) {
            return null;
        }
        
        Map<String, String> values = new HashMap<>();
        this.editorMap.forEach((key, value) -> values.put(key, value.getText()));
        String code = createFilledCode(template, values);
        GraphicsPL g = new GraphicsPL();
        try {
            g.load(code, null);
            return Dither.floydSteinbergDithering(g.createImage(1));

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
        if (this.templateLabelComboBox.getSelectedItem() != null) {
            this.properties.put("template", this.templateLabelComboBox.getSelectedItem());
        } else {
            this.properties.remove("template");
        }
        this.properties.put("comptatible", String.valueOf(this.compatibleCheckBox.isSelected()));

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
    
    /**
     * Swing worker responsable de la récupération et de la mise à jour du statut de l'imprimante.
     */
    private class StatusSwingWorker extends SwingWorker<Boolean, BrotherQLStatus> {
        
        private BrotherQLMedia currentMedia = null;
        private Boolean previousCompatible = null;
        
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
            printerIdTextField.setText(formatPrinterId(status));
            boolean ready = BrotherQLStatusType.READY.equals(status.getStatusType());
            if (!ready) {
                formatPrinterStatus(status, printerStatus);
                hasPrinted = false;
            } else if (!hasPrinted) {
                formatPrinterStatus(status, printerStatus);
            }
            
            mediaIdTextField.setText(formatMediaStatus(status));
            mediaIdTextField.setToolTipText(formatMediaStatusTootlip(status));
            boolean compatibleHasChanged = previousCompatible == null || !previousCompatible.equals(compatibleCheckBox.isSelected());
            if (compatibleHasChanged) {
                if (compatibleCheckBox.isSelected()) {
                    currentMedia = BrotherQLMedia.identify(status);
                    updateLabelComboBox(currentMedia);
                } else {
                    String currentTemplate = (String) templateLabelComboBox.getSelectedItem();
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(templateManager.getNames().toArray(new String[0]));
                    templateLabelComboBox.setModel(model);
                    templateLabelComboBox.setSelectedItem(currentTemplate);
                }
                previousCompatible = compatibleCheckBox.isSelected();
            } else {
                BrotherQLMedia newMedia = BrotherQLMedia.identify(status);
                if (newMedia != null && compatibleCheckBox.isSelected() && !newMedia.equals(currentMedia)) {
                    currentMedia = newMedia;
                    updateLabelComboBox(currentMedia);
                }
            }
            changeLabel((String) templateLabelComboBox.getSelectedItem());
            printButton.setEnabled(BrotherQLStatusType.READY.equals(status.getStatusType()));
        }
        
        private void updateLabelComboBox(BrotherQLMedia media) {
            if (media == null) {
                return;
            }
            
            String currentTemplate = (String) templateLabelComboBox.getSelectedItem();
            List<String> compatibleTemplates = new ArrayList<>();
            templateManager.browseTemplates(f -> {
                GraphicsPL g = new GraphicsPL();
                try {
                    g.load(f);
                    final Element root = g.getDocument().getDocumentElement();
                    final int width = Integer.parseInt(root.getAttribute("width"));
                    final int height = Integer.parseInt(root.getAttribute("height"));
                    if (isCompatible(width, height, media)) {
                        compatibleTemplates.add(templateManager.getName(f));
                    }
                } catch (ParserConfigurationException | SAXException | IOException e1) {
                    // Ignore
                }
                return true;
            });
            
            compatibleTemplates.sort(String.CASE_INSENSITIVE_ORDER);
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(compatibleTemplates.toArray(new String[0]));
            templateLabelComboBox.setModel(model);

            if (compatibleTemplates.contains(currentTemplate)) {
                String firstTemplate = compatibleTemplates.iterator().next();
                templateLabelComboBox.setSelectedItem(firstTemplate);
            } else {
                templateLabelComboBox.setSelectedItem(currentTemplate);    
            }
        }
    }
    
    private boolean isCompatible(int templateWidth, int templateHeight, BrotherQLMedia media) {
        if (media.mediaType.equals(BrotherQLMediaType.CONTINUOUS_LENGTH_TAPE)) {
            return templateWidth == media.bodyWidthPx;
        } else {
            return templateWidth == media.bodyWidthPx
                    && templateHeight == media.bodyLengthPx;
        }
    }    

    /**
     * Swing Worker responsable de la mise à jour de la prévisualisation de l'étiquette
     */
    private class PreviewLabelSwingWorker extends SwingWorker<Boolean, Void> {
        @Override
        protected Boolean doInBackground() throws Exception {
            while (!isCancelled()) {
                if (!printing) {
                    publish();
                }
                TimeUnit.MILLISECONDS.sleep(200);
            }
            return true;
        }

        @Override
        protected void process(List<Void> chunks) {
            if (templateName == null) {
                return;
            }
            BufferedImage previewImage = renderSingleLabel(templateManager.get(templateName));
            if (previewImage != null) {
                JLabel preview = new JLabel();
                ImageIcon ii = new ImageIcon(previewImage);
                int maxHeight = previewPanel.getHeight() - 20;
                int maxWidth = previewPanel.getWidth() - 20;
                
                ImageIcon iiSized;
                if (previewImage.getHeight() > previewImage.getWidth()) {
                    iiSized = new ImageIcon(ii.getImage().getScaledInstance(-1, maxHeight, java.awt.Image.SCALE_SMOOTH));
                } else {
                    iiSized = new ImageIcon(ii.getImage().getScaledInstance(maxWidth, -1, java.awt.Image.SCALE_SMOOTH));
                }
                preview.setIcon(iiSized);
                previewPanel.setToolTipText("Taille du modèle : " + previewImage.getWidth() + "x" + previewImage.getHeight());
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
            printing = true;
            hasPrinted = true;
            publish("Impression en cours...");
            savePrefs();
            List<BufferedImage> labels = renderLabels(templateManager.get(templateName));
                
            try {
                BrotherQLJob job = new BrotherQLJob()
                        .setAutocut(autocutCheckBox.isSelected())
                        .setCutEach((Integer) cutEach.getValue())
                        .setDelay((Integer) delay.getValue())
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

        private void print(BrotherQLJob job) throws IOException {
            BrotherQLManager manager = new BrotherQLManager();
            try {
                manager.open();
                BrotherQLStatus status = manager.requestDeviceStatus();
                if (status.getStatusType() != BrotherQLStatusType.READY) {
                    publish(formatPrinterStatus(status));
                    return;
                }
                    
                BrotherQLPrinterId printer = manager.getPrinterId();
                if (printer != null) {
                    job.setPrinterId(printer);
                    job.setMedia(manager.getMediaDefinition(status));
                    manager.printJob(job, (page, s) -> {
                        publish(formatPrintingStatus(page, s, job));
                        return !isCancelled();
                    });
                }
            } catch (IllegalStateException | LibUsbException e) {
                publish(formatPrinterStatus(new BrotherQLStatus(null, manager.getPrinterId(), e.getMessage())));
                
            } finally {
                manager.close();
            }
        }
        
    }

}
