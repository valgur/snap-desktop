/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.rcp.actions.vector;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.dataio.geometry.VectorDataNodeIO;
import org.esa.snap.dataio.geometry.VectorDataNodeReader;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductNode;
import org.esa.snap.framework.datamodel.VectorDataNode;
import org.esa.snap.jai.ImageManager;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.util.io.SnapFileFilter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;

import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@ActionID(
        category = "File",
        id = "ImportVectorDataNodeFromCsvAction"
)
@ActionRegistration(
        displayName = "#CTL_ImportVectorDataNodeFromCsvActionName",
        lazy=true
)
@ActionReference(path = "Menu/File/Import/Vector Data", position = 10)
@NbBundle.Messages({
        "CTL_ImportVectorDataNodeFromCsvActionText=CSV",
        "CTL_ImportVectorDataNodeFromCsvActionName=Import Vector Data Node From CSV",
        "CTL_ImportVectorDataNodeFromCsvActionHelp=importCSV"
})
public class ImportVectorDataNodeFromCsvAction extends AbstractImportVectorDataNodeAction
        implements ContextAwareAction, LookupListener {

    private Lookup lookup;
    private final Lookup.Result<Product> result;
    private VectorDataNodeImporter importer;
    private static final String vector_data_type = "CSV";

    public ImportVectorDataNodeFromCsvAction() {
        this(Utilities.actionsGlobalContext());
    }

    public ImportVectorDataNodeFromCsvAction(Lookup lookup) {
        this.lookup = lookup;
        result = lookup.lookupResult(Product.class);
        result.addLookupListener(
                WeakListeners.create(LookupListener.class, this, result));
        setEnableState();
        setHelpId(Bundle.CTL_ImportVectorDataNodeFromCsvActionHelp());
        putValue(Action.NAME, Bundle.CTL_ImportVectorDataNodeFromCsvActionText());
        putValue(Action.SHORT_DESCRIPTION, Bundle.CTL_ImportVectorDataNodeFromCsvActionName());
    }

    @Override
    public Action createContextAwareInstance(Lookup lookup) {
        return new ImportVectorDataNodeFromCsvAction(lookup);
    }

    @Override
    public void resultChanged(LookupEvent lookupEvent) {
        setEnableState();
    }

    private void setEnableState() {
        boolean state = false;
        ProductNode productNode = lookup.lookup(ProductNode.class);
        if (productNode != null) {
            Product product = productNode.getProduct();
            state = product != null && product.getGeoCoding() != null;
        }
        setEnabled(state);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final SnapFileFilter filter = new SnapFileFilter(getVectorDataType(),
                                                         new String[]{".txt", ".dat", ".csv"},
                                                         "Plain text");
        importer = new VectorDataNodeImporter(getHelpId(), filter, new DefaultVectorDataNodeReader(), "Import CSV file", "csv.io.dir");
        importer.importGeometry(SnapApp.getDefault());
    }

    @Override
    protected String getDialogTitle() {
        return importer.getDialogTitle();
    }

    @Override
    protected String getVectorDataType() {
        return vector_data_type;
    }

    private class DefaultVectorDataNodeReader implements VectorDataNodeImporter.VectorDataNodeReader {

        @Override
        public VectorDataNode readVectorDataNode(File file, Product product, ProgressMonitor pm) throws IOException {
            FileReader reader = null;
            try {
                final CoordinateReferenceSystem modelCrs = ImageManager.getModelCrs(product.getGeoCoding());
                reader = new FileReader(file);
                return VectorDataNodeReader.read(file.getName(), reader, product, crsProvider, placemarkDescriptorProvider,
                                                 modelCrs, VectorDataNodeIO.DEFAULT_DELIMITER_CHAR, pm);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
    }
}
