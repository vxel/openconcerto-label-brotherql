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

import org.openconcerto.erp.modules.AbstractModule;
import org.openconcerto.erp.modules.ComponentsContext;
import org.openconcerto.erp.modules.DBContext;
import org.openconcerto.erp.modules.ModuleFactory;
import org.openconcerto.sql.model.SQLRowValues;
import org.openconcerto.sql.model.SQLRowValuesListFetcher;
import org.openconcerto.sql.model.SQLTable;
import org.openconcerto.sql.model.Where;
import org.openconcerto.sql.view.list.IListe;
import org.openconcerto.sql.view.list.IListeAction.IListeEvent;
import org.openconcerto.sql.view.list.RowAction.PredicateRowAction;
import org.openconcerto.utils.ExceptionHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Module Openconcerto pour l'impression d'étiquette d'articles sur imprimantes Brother QL via port USB.
 * Ce module s'interface directement avec l'imprimante via USB, sans passer par un driver,
 * afin d'exploiter plus finement les capacités spécifiques de ces imprimantes
 * (et éviter les problèmes et limitations de l'impression via l'api Java Printing).
 *
 * @author Cédric de Launois
 */
@SuppressWarnings("unused")
public final class Module extends AbstractModule {

    private static final Logger LOGGER = Logger.getLogger(Module.class.getName());

    public Module(ModuleFactory f) throws IOException {
        super(f);
    }

    @Override
    protected void install(DBContext ctxt) {
        try {
            TemplateManager.installTemplate();
        } catch (IOException | URISyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to install templates", e);
        }
    }
    
    @Override
    protected void setupComponents(ComponentsContext ctxt) {
        final PredicateRowAction printAction = new PredicateRowAction(
                new ArticlePrintAction("Imprimer les étiquettes"), true, false);
        printAction.setPredicate(IListeEvent.createSelectionCountPredicate(1, Integer.MAX_VALUE));
        ctxt.getElement("ARTICLE").getRowActions().add(printAction);
    }

    @Override
    protected void start() {
        // Nothing
    }

    @Override
    protected void stop() {
        // Nothing
    }

    private static class ArticlePrintAction extends AbstractAction {

        private final String actionName;

        public ArticlePrintAction(String actionName) {
            super(actionName);
            this.actionName = actionName;
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            final IListe list = IListe.get(arg0);
            final List<Integer> selectedIDs = list.getSelection().getSelectedIDs();
            final SQLTable tArticle = list.getSelectedRowAccessors().get(0).getTable();
            final ArticleFetchSwingWorker wworker = new ArticleFetchSwingWorker(tArticle, selectedIDs);
            wworker.execute();
        }

        private class ArticleFetchSwingWorker extends SwingWorker<List<SQLRowValues>, String> {
            private final SQLTable tArticle;
            private final List<Integer> selectedIDs;

            public ArticleFetchSwingWorker(SQLTable tArticle, List<Integer> selectedIDs) {
                this.tArticle = tArticle;
                this.selectedIDs = selectedIDs;
            }

            @Override
            protected List<SQLRowValues> doInBackground() {
                final SQLRowValues graph = new SQLRowValues(tArticle);
                graph.putNulls("NOM", "PV_HT", "PV_TTC", "CODE", "SKU", "CODE_BARRE", "MATIERE");

                final SQLRowValues graphPromo = new SQLRowValues(tArticle.getTable("ARTICLE_TARIF_PROMOTION"));
                graphPromo.setAllToNull();
                graphPromo.putRowValues("ID_TARIF_PROMOTION").setAllToNull();
                graphPromo.put("ID_ARTICLE", graph);
                final SQLRowValuesListFetcher fetcher = SQLRowValuesListFetcher.create(graph);

                final List<SQLRowValues> rows = fetcher.fetch(Where.inValues(tArticle.getKey(), selectedIDs));
                final List<SQLRowValues> list = new ArrayList<>(rows.size());
                list.addAll(rows);
                return list;
            }

            @Override
            protected void done() {
                try {
                    final List<SQLRowValues> values = get();

                    final GPLPrinterPanel p = new GPLPrinterPanel();
                    final JFrame f = new JFrame();
                    p.uiInit(values);
                    f.setTitle(actionName);
                    f.setMinimumSize(new Dimension(1000, 480));
                    f.setPreferredSize(new Dimension(1000, 650));
                    f.setLocationRelativeTo(null);
                    f.setContentPane(p);
                    f.pack();
                    f.setVisible(true);
                } catch (Exception e) {
                    ExceptionHandler.handle(null, "Erreur d'impression", e);
                }
            }
        }
    }
}
