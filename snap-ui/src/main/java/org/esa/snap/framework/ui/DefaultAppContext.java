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

package org.esa.snap.framework.ui;

import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductManager;
import org.esa.snap.framework.ui.application.ApplicationPage;
import org.esa.snap.framework.ui.product.ProductSceneView;
import org.esa.snap.util.DefaultPropertyMap;
import org.esa.snap.util.PropertyMap;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.awt.Window;

/**
 * This trivial implementation of the {@link org.esa.snap.framework.ui.AppContext} class
 * is only for testing.
 */
public class DefaultAppContext implements AppContext {

    private Window applicationWindow;
    private String applicationName;
    private ProductManager productManager;
    private Product selectedProduct;
    private PropertyMap preferences;
    private ProductSceneView selectedSceneView;

    public DefaultAppContext(String applicationName) {
        this(applicationName,
             new JFrame(applicationName),
             new ProductManager(),
             new DefaultPropertyMap());
    }


    public DefaultAppContext(String applicationName,
                             Window applicationWindow,
                             ProductManager productManager,
                             PropertyMap preferences) {
        this.applicationWindow = applicationWindow;
        this.applicationName = applicationName;
        this.productManager = productManager;
        this.preferences = preferences;
    }

    @Override
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    public Window getApplicationWindow() {
        return applicationWindow;
    }

    @Override
    public ApplicationPage getApplicationPage() {
        return null;
    }

    public void setApplicationWindow(Window applicationWindow) {
        this.applicationWindow = applicationWindow;
    }

    @Override
    public PropertyMap getPreferences() {
        return preferences;
    }

    public void setPreferences(PropertyMap preferences) {
        this.preferences = preferences;
    }

    @Override
    public ProductManager getProductManager() {
        return productManager;
    }

    public void setProductManager(ProductManager productManager) {
        this.productManager = productManager;
    }

    @Override
    public Product getSelectedProduct() {
        return selectedProduct;
    }

    public void setSelectedProduct(Product selectedProduct) {
        this.selectedProduct = selectedProduct;
    }

    @Override
    public void handleError(String message, Throwable t) {
        if (t != null) {
            t.printStackTrace();
        }
        JOptionPane.showMessageDialog(getApplicationWindow(), message);
    }

    @Override
    public ProductSceneView getSelectedProductSceneView() {
        return selectedSceneView;
    }

    public void setSelectedSceneView(ProductSceneView selectedView) {
        this.selectedSceneView = selectedView;
    }
}
