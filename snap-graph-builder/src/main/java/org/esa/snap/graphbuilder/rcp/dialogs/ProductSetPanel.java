/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.graphbuilder.rcp.dialogs;

import org.esa.snap.db.DBSearch;
import org.esa.snap.db.ProductEntry;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.snap.framework.ui.AppContext;
import org.esa.snap.graphbuilder.rcp.dialogs.support.FileTable;
import org.esa.snap.graphbuilder.rcp.dialogs.support.FileTableModel;
import org.esa.snap.graphbuilder.rcp.dialogs.support.TargetFolderSelector;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.actions.file.OpenProductAction;
import org.esa.snap.rcp.actions.file.SaveProductAsAction;
import org.esa.snap.rcp.util.ProgressHandleMonitor;
import org.esa.snap.tango.TangoIcons;
import org.esa.snap.util.DialogUtils;
import org.esa.snap.util.ProductFunctions;
import org.esa.snap.util.SystemUtils;
import org.esa.snap.util.io.FileChooserFactory;
import org.netbeans.api.progress.ProgressUtils;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;

/**
 * NEST IO Panel to handle source and target selection
 * User: lveci
 * Date: Feb 5, 2009
 */
public class ProductSetPanel extends JPanel implements TableModelListener {

    private final FileTable productSetTable;
    private final TargetFolderSelector targetProductSelector;
    private final AppContext appContext;
    private String targetProductNameSuffix = "";
    private JPanel buttonPanel = null;

    private JButton addButton = null, addAllOpenButton = null, dbQueryButton = null, removeButton = null;
    private JButton moveTopButton = null, moveUpButton = null, moveDownButton = null, moveBottomButton = null;
    private JButton clearButton = null;

    final JLabel countLabel = new JLabel();

    private static final ImageIcon addIcon = TangoIcons.actions_list_add(TangoIcons.Res.R22);
    private static final ImageIcon addOpenedIcon = new ImageIcon(ProductSetPanel.class.getClassLoader().
            getResource("org/esa/snap/graphbuilder/icons/add-opened22.png"));
    private static final ImageIcon removeIcon = TangoIcons.actions_list_remove(TangoIcons.Res.R22);
    private static final ImageIcon searchIcon = TangoIcons.actions_system_search(TangoIcons.Res.R22);
    private static final ImageIcon moveTopIcon = TangoIcons.actions_go_top(TangoIcons.Res.R22);
    private static final ImageIcon moveUpIcon = TangoIcons.actions_go_up(TangoIcons.Res.R22);
    private static final ImageIcon moveDownIcon = TangoIcons.actions_go_down(TangoIcons.Res.R22);
    private static final ImageIcon moveBottomIcon = TangoIcons.actions_go_bottom(TangoIcons.Res.R22);
    private static final ImageIcon clearIcon = TangoIcons.actions_edit_clear(TangoIcons.Res.R22);

    public ProductSetPanel(final AppContext theAppContext, final String title) {
        this(theAppContext, title, new FileTable(), false, false);
    }

    public ProductSetPanel(final AppContext theAppContext, final String title, final FileTableModel fileModel) {
        this(theAppContext, title, new FileTable(fileModel), false, false);
    }

    public ProductSetPanel(final AppContext theAppContext, final String title, final FileTable fileTable,
                           final boolean incTrgProduct, final boolean incButtonPanel) {
        super(new BorderLayout());
        this.appContext = theAppContext;
        this.productSetTable = fileTable;
        setBorderTitle(title);

        final JPanel productSetContent = createComponent(productSetTable, false);
        if (incButtonPanel) {
            buttonPanel = createButtonPanel(productSetTable);
            productSetContent.add(buttonPanel, BorderLayout.EAST);
        }
        this.add(productSetContent, BorderLayout.CENTER);

        if (incTrgProduct) {
            targetProductSelector = new TargetFolderSelector();
            final String homeDirPath = SystemUtils.getUserHomeDir().getPath();
            final String saveDir = SnapApp.getDefault().getPreferences().get(SaveProductAsAction.PREFERENCES_KEY_LAST_PRODUCT_DIR, homeDirPath);
            targetProductSelector.getModel().setProductDir(new File(saveDir));
            targetProductSelector.getOpenInAppCheckBox().setText("Open in " + theAppContext.getApplicationName());
            targetProductSelector.getOpenInAppCheckBox().setVisible(false);

            this.add(targetProductSelector.createPanel(), BorderLayout.SOUTH);
        } else {
            targetProductSelector = null;
        }
        fileTable.getModel().addTableModelListener(this);

        updateComponents();
    }

    public void setBorderTitle(final String title) {
        if (title != null)
            setBorder(BorderFactory.createTitledBorder(title));
    }

    public JPanel getButtonPanel() {
        return buttonPanel;
    }

    private JPanel createComponent(final FileTable table, final boolean incButtonPanel) {

        final JPanel fileListPanel = new JPanel(new BorderLayout(4, 4));

        final JScrollPane scrollPane = new JScrollPane(table);
        fileListPanel.add(scrollPane, BorderLayout.CENTER);

        if (incButtonPanel) {
            final JPanel buttonPanel = createButtonPanel(table);
            fileListPanel.add(buttonPanel, BorderLayout.EAST);
        }
        return fileListPanel;
    }

    private void updateComponents() {

        final int rowCount = productSetTable.getFileCount();

        final boolean enableButtons = (rowCount > 0);
        if (dbQueryButton != null)
            dbQueryButton.setEnabled(enableButtons);
        if (removeButton != null)
            removeButton.setEnabled(enableButtons);
        if (moveTopButton != null)
            moveTopButton.setEnabled(rowCount > 1);
        if (moveUpButton != null)
            moveUpButton.setEnabled(rowCount > 1);
        if (moveDownButton != null)
            moveDownButton.setEnabled(rowCount > 1);
        if (moveBottomButton != null)
            moveBottomButton.setEnabled(rowCount > 1);
        if (clearButton != null)
            clearButton.setEnabled(enableButtons);

        if (addAllOpenButton != null) {
            addAllOpenButton.setEnabled(SnapApp.getDefault().getProductManager().getProducts().length > 0);
        }

        String cntMsg;
        if (rowCount == 1) {
            cntMsg = rowCount + " Product";
        } else {
            cntMsg = rowCount + " Products";
        }
        countLabel.setText(cntMsg);
    }

    public JPanel createButtonPanel(final FileTable table) {
        final FileTableModel tableModel = table.getModel();

        final JPanel panel = new JPanel(new GridLayout(10, 1));

        addButton = DialogUtils.createButton("addButton", "Add", addIcon, panel, DialogUtils.ButtonStyle.Icon);
        addButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final File[] files = GetFilePath(addButton, "Add Product");
                if (files != null) {
                    addProducts(tableModel, files);
                }
            }
        });

        addAllOpenButton = DialogUtils.createButton("addAllOpenButton", "Add Opened", addOpenedIcon, panel, DialogUtils.ButtonStyle.Icon);
        addAllOpenButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                addAllOpenProducts(tableModel);
            }
        });

        dbQueryButton = DialogUtils.createButton("dbQueryButton", "DB Query", searchIcon, panel, DialogUtils.ButtonStyle.Icon);
        dbQueryButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                try {
                    final File mstFile = tableModel.getFileAt(0);
                    if (mstFile.exists()) {
                        final ProductEntry[] entryList = DBSearch.search(mstFile);
                        for (ProductEntry entry : entryList) {
                            if (tableModel.getIndexOf(entry.getFile()) < 0)
                                tableModel.addFile(entry);
                        }
                    }
                } catch (Exception ex) {
                    appContext.handleError("Unable to query DB", ex);
                }
            }
        });

        removeButton = DialogUtils.createButton("removeButton", "Remove", removeIcon, panel, DialogUtils.ButtonStyle.Icon);
        removeButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int rowCount = productSetTable.getFileCount();
                if (rowCount == 1) {
                    tableModel.clear();
                    return;
                }
                final int[] selRows = table.getSelectedRows();
                final java.util.List<File> filesToRemove = new ArrayList<>(selRows.length);
                for (int row : selRows) {
                    filesToRemove.add(tableModel.getFileAt(row));
                }
                for (File file : filesToRemove) {
                    int index = tableModel.getIndexOf(file);
                    tableModel.removeFile(index);
                }
            }
        });

        moveTopButton = DialogUtils.createButton("moveTopButton", "Move Top", moveTopIcon, panel, DialogUtils.ButtonStyle.Icon);
        moveTopButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int[] selRows = table.getSelectedRows();
                final java.util.List<File> filesToMove = new ArrayList<>(selRows.length);
                for (int row : selRows) {
                    filesToMove.add(tableModel.getFileAt(row));
                }
                int pos = 0;
                for (File file : filesToMove) {
                    int index = tableModel.getIndexOf(file);
                    if (index > 0) {
                        tableModel.move(index, pos++);
                    }
                }
            }
        });

        moveUpButton = DialogUtils.createButton("moveUpButton", "Move Up", moveUpIcon, panel, DialogUtils.ButtonStyle.Icon);
        moveUpButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int[] selRows = table.getSelectedRows();
                final java.util.List<File> filesToMove = new ArrayList<>(selRows.length);
                for (int row : selRows) {
                    filesToMove.add(tableModel.getFileAt(row));
                }
                for (File file : filesToMove) {
                    int index = tableModel.getIndexOf(file);
                    if (index > 0) {
                        tableModel.move(index, index - 1);
                    }
                }
            }
        });

        moveDownButton = DialogUtils.createButton("moveDownButton", "Move Down", moveDownIcon, panel, DialogUtils.ButtonStyle.Icon);
        moveDownButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int[] selRows = table.getSelectedRows();
                final java.util.List<File> filesToMove = new ArrayList<>(selRows.length);
                for (int row : selRows) {
                    filesToMove.add(tableModel.getFileAt(row));
                }
                for (File file : filesToMove) {
                    int index = tableModel.getIndexOf(file);
                    if (index < tableModel.getRowCount()) {
                        tableModel.move(index, index + 1);
                    }
                }
            }
        });

        moveBottomButton = DialogUtils.createButton("moveBottomButton", "Move Bottom", moveBottomIcon, panel, DialogUtils.ButtonStyle.Icon);
        moveBottomButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int[] selRows = table.getSelectedRows();
                final java.util.List<File> filesToMove = new ArrayList<>(selRows.length);
                for (int row : selRows) {
                    filesToMove.add(tableModel.getFileAt(row));
                }
                for (File file : filesToMove) {
                    int index = tableModel.getIndexOf(file);
                    if (index < tableModel.getRowCount()) {
                        tableModel.move(index, tableModel.getRowCount()-1);
                    }
                }
            }
        });

        clearButton = DialogUtils.createButton("clearButton", "Clear", clearIcon, panel, DialogUtils.ButtonStyle.Icon);
        clearButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                tableModel.clear();
            }
        });

        panel.add(addButton);
        panel.add(addAllOpenButton);
        panel.add(removeButton);
        //panel.add(dbQueryButton); //todo
        panel.add(moveTopButton);
        panel.add(moveUpButton);
        panel.add(moveDownButton);
        panel.add(moveBottomButton);
        panel.add(clearButton);
        panel.add(countLabel);

        return panel;
    }

    private void addProducts(final FileTableModel tableModel, final File[] files) {
        final ProgressHandleMonitor pm = ProgressHandleMonitor.create("Populating table");
        Runnable operation = () -> {
            pm.beginTask("Populating table...", files.length);
            for (File file : files) {
                if (ProductFunctions.isValidProduct(file)) {
                    tableModel.addFile(file);
                }
                pm.worked(1);
            }
            pm.done();
        };

        ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Adding Products", pm.getProgressHandle(), true, 50, 1000);
    }

    private void addAllOpenProducts(final FileTableModel tableModel) {
        final ProgressHandleMonitor pm = ProgressHandleMonitor.create("Populating table");
        Runnable operation = () -> {
            final Product[] products = SnapApp.getDefault().getProductManager().getProducts();
            pm.beginTask("Populating table...", products.length);
            for (Product prod : products) {
                final File file = prod.getFileLocation();
                if (file != null && file.exists()) {
                    tableModel.addFile(file);
                }
                pm.worked(1);
            }
            pm.done();
        };

        ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Adding Products", pm.getProgressHandle(), true, 50, 1000);
    }

    /**
     * This fine grain notification tells listeners the exact range
     * of cells, rows, or columns that changed.
     */
    public void tableChanged(TableModelEvent e) {
        updateComponents();
    }

    private static File[] GetFilePath(Component component, String title) {

        File[] files = null;
        final File openDir = new File(SnapApp.getDefault().getPreferences().
                get(OpenProductAction.PREFERENCES_KEY_LAST_PRODUCT_DIR, "."));
        final JFileChooser chooser = FileChooserFactory.getInstance().createFileChooser(openDir);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle(title);
        if (chooser.showDialog(component, "ok") == JFileChooser.APPROVE_OPTION) {
            files = chooser.getSelectedFiles();

            SnapApp.getDefault().getPreferences().
                    put(OpenProductAction.PREFERENCES_KEY_LAST_PRODUCT_DIR, chooser.getCurrentDirectory().getAbsolutePath());
        }
        return files;
    }

    public void setTargetProductName(final String name) {
        if (targetProductSelector != null) {
            final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
            targetProductSelectorModel.setProductName(name + getTargetProductNameSuffix());
        }
    }

    public void onApply() {
        if (targetProductSelector != null) {
            final String productDir = targetProductSelector.getModel().getProductDir().getAbsolutePath();
            SnapApp.getDefault().getPreferences().put(SaveProductAsAction.PREFERENCES_KEY_LAST_PRODUCT_DIR, productDir);
        }
    }

    String getTargetProductNameSuffix() {
        return targetProductNameSuffix;
    }

    public void setTargetProductNameSuffix(final String suffix) {
        targetProductNameSuffix = suffix;
    }

    public File getTargetFolder() {
        if (targetProductSelector != null) {
            final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
            return targetProductSelectorModel.getProductDir();
        }
        return null;
    }

    public String getTargetFormat() {
        if (targetProductSelector != null) {
            final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
            return targetProductSelectorModel.getFormatName();
        }
        return null;
    }

    public void setTargetFolder(final File path) {
        if (targetProductSelector != null) {
            final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
            targetProductSelectorModel.setProductDir(path);
        }
    }

    public File[] getFileList() {
        return productSetTable.getFileList();
    }

    public File[] getSelectedFiles() {
        return productSetTable.getModel().getFilesAt(productSetTable.getSelectedRows());
    }

    public Object getValueAt(final int r, final int c) {
        return productSetTable.getModel().getValueAt(r, c);
    }

    public void setProductFileList(final File[] productFileList) {
        productSetTable.setFiles(productFileList);
    }

    public void setProductEntryList(final ProductEntry[] productEntryList) {
        productSetTable.setProductEntries(productEntryList);
    }
}
