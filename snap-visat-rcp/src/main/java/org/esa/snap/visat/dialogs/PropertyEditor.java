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
package org.esa.snap.visat.dialogs;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.DialogProgressMonitor;
import com.bc.jexp.ParseException;
import com.bc.jexp.Term;
import com.bc.jexp.WritableNamespace;
import com.bc.jexp.impl.ParserImpl;
import com.bc.jexp.impl.SymbolFactory;
import org.esa.snap.framework.datamodel.AbstractBand;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.FlagCoding;
import org.esa.snap.framework.datamodel.MetadataAttribute;
import org.esa.snap.framework.datamodel.MetadataElement;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import org.esa.snap.framework.datamodel.ProductNode;
import org.esa.snap.framework.datamodel.ProductNodeEvent;
import org.esa.snap.framework.datamodel.ProductNodeListenerAdapter;
import org.esa.snap.framework.datamodel.ProductNodeNameValidator;
import org.esa.snap.framework.datamodel.ProductVisitorAdapter;
import org.esa.snap.framework.datamodel.RasterDataNode;
import org.esa.snap.framework.datamodel.TiePointGrid;
import org.esa.snap.framework.datamodel.VirtualBand;
import org.esa.snap.framework.dataop.barithm.BandArithmetic;
import org.esa.snap.framework.param.ParamChangeEvent;
import org.esa.snap.framework.param.ParamChangeListener;
import org.esa.snap.framework.param.ParamProperties;
import org.esa.snap.framework.param.Parameter;
import org.esa.snap.framework.param.editors.BooleanExpressionEditor;
import org.esa.snap.framework.param.editors.GeneralExpressionEditor;
import org.esa.snap.framework.ui.GridBagUtils;
import org.esa.snap.framework.ui.ModalDialog;
import org.esa.snap.framework.ui.product.ProductSceneView;
import org.esa.snap.util.Debug;
import org.esa.snap.util.StringUtils;
import org.esa.snap.visat.VisatApp;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class PropertyEditor {

    private static final String UNCERTAINTY_ROLE_NAME = "uncertainty";
    private final VisatApp visatApp;
    private ModalDialog dialog;

    public PropertyEditor(final VisatApp visatApp) {
        this.visatApp = visatApp;
    }

    public void show(final ProductNode selectedProductNode) {
        if (isValidNode(selectedProductNode)) {
            final EditorContent editorContent = new EditorContent(selectedProductNode);
            show(editorContent);
        }
    }

    private void show(final EditorContent editorContent) {
        dialog = new PropertyEditorDialog(editorContent);
        if (dialog.show() == ModalDialog.ID_OK) {
            editorContent.changeProperties();
        }
        dialog = null;
    }

    public ModalDialog getDialog() {
        return dialog;
    }

    public static boolean isValidNode(final ProductNode node) {
        return node instanceof RasterDataNode || node instanceof Product;
    }

    private static String getTitleText(final EditorContent editorContent) {
        final ProductNode productNode = editorContent.getProductNode();
        final String type = getProductNodeTypeText(productNode);
        final String pattern = "{0} Properties - {1}"; /*I18N*/
        return new MessageFormat(pattern).format(new Object[]{type, productNode.getName()});
    }

    // @todo nf/nf make this a general utility function (e.g. Information pane should also use it)
    private static String getProductNodeTypeText(final ProductNode productNode) {
        final String type;
        if (productNode instanceof Product) {
            type = "Product";  /*I18N*/
        } else if (productNode instanceof TiePointGrid) {
            type = "Tie Point Grid"; /*I18N*/
        } else if (productNode instanceof VirtualBand) {
            type = "Virtual Band"; /*I18N*/
        } else if (productNode instanceof AbstractBand) {
            type = "Band";   /*I18N*/
        } else if (productNode instanceof FlagCoding) {
            type = "Flag Coding";  /*I18N*/
        } else if (productNode instanceof MetadataAttribute) {
            type = "Metadata Attribute";  /*I18N*/
        } else if (productNode instanceof MetadataElement) {
            type = "Metadata Element";  /*I18N*/
        } else {
            type = "Product Node";  /*I18N*/
        }
        return type;
    }

    private class EditorContent extends JPanel {

        private static final int GROUP_GAP = 10;

        private final ProductNode node;
        private Product product;
        private RasterDataNode rasterDataNode;
        private Band band;
        private VirtualBand virtualBand;

        private GridBagConstraints gbc;

        private Parameter paramName;
        private Parameter paramDescription;

        private Parameter paramSpectralBandwidth;
        private Parameter paramSpectralWavelength;

        private Parameter paramProductType;
        private Parameter paramStartTime;
        private Parameter paramEndTime;
        private Parameter paramBandSubGroupPaths;
        private Parameter paramNoDataValueUsed;
        private Parameter paramNoDataValue;
        private Parameter paramAncillaryRoleName;
        private Parameter paramAncillaryBandName;
        private Parameter paramGeophysUnit;
        private Parameter paramValidPixelExpr;
        private Parameter paramVBExpression;
        private boolean virtualBandPropertyChanged;
        private boolean validMaskPropertyChanged;

        private EditorContent(final ProductNode node) {
            this.node = node;
            initParameters(node);
            initUi(node);
        }

        public ProductNode getProductNode() {
            return node;
        }

        private void initParameters(final ProductNode node) {
            initProductNodeParameters();
            node.acceptVisitor(createParamVisitor());
        }

        private ProductVisitorAdapter createParamVisitor() {
            return new ProductVisitorAdapter() {
                @Override
                public void visit(final Band band) {
                    if (!ignoreVisit()) {
                        initParamsForRasterDataNode(band);
                        initParamsForBand(band);
                    }
                }

                @Override
                public void visit(final TiePointGrid grid) {
                    if (!ignoreVisit()) {
                        initParamsForRasterDataNode(grid);
                    }
                }

                @Override
                public void visit(final Product product) {
                    EditorContent.this.product = product;
                    initProductTypeParam();
                    initProductStartStopParams();
                    initProductBandGroupingParam();
                }

                @Override
                public void visit(final VirtualBand virtualBand) {
                    if (!ignoreVisit()) {
                        initParamsForRasterDataNode(virtualBand);
                        initParamsForBand(virtualBand);
                        initParamsForVirtualBand(virtualBand);
                    }
                }
            };
        }

        private void initUi(final ProductNode node) {
            initProductNodeUI();
            node.acceptVisitor(creatUiVisitor());
        }

        private ProductVisitorAdapter creatUiVisitor() {
            return new ProductVisitorAdapter() {
                @Override
                public void visit(final Band band) {
                    if (!ignoreVisit()) {
                        initRasterDataNodeUI();
                        initBandUI();
                    }
                }

                @Override
                public void visit(final TiePointGrid grid) {
                    if (!ignoreVisit()) {
                        initRasterDataNodeUI();
                    }
                }

                @Override
                public void visit(final Product product) {
                    initProductUI();
                }

                @Override
                public void visit(final VirtualBand virtualBand) {
                    if (!ignoreVisit()) {
                        initRasterDataNodeUI();
                        initBandUI();
                        initVirtualBandUI();
                    }
                }
            };
        }

        public boolean validateProperties() {
            if (rasterDataNode != null) {
                final String expression = paramValidPixelExpr.getValueAsText();
                if (expression != null && expression.trim().length() != 0) {
                    final Product product = rasterDataNode.getProduct();
                    try {
                        Product[] products = getCompatibleProducts(rasterDataNode);
                        int defaultProductIndex = Arrays.asList(products).indexOf(product);
                        final WritableNamespace namespace = BandArithmetic.createDefaultNamespace(products,
                                                                                                  defaultProductIndex);
                        namespace.registerSymbol(SymbolFactory.createConstant(paramName.getValueAsText(), 0));
                        final Term term = new ParserImpl(namespace, false).parse(expression);
                        if (!term.isB()) {
                            JOptionPane.showMessageDialog(dialog.getJDialog(),
                                                          "The expression must be of boolean type."); /*I18N*/
                            return false;
                        }
                    } catch (ParseException e) {
                        JOptionPane.showMessageDialog(dialog.getJDialog(),
                                                      "Invalid expression syntax:\n" + e.getMessage()); /*I18N*/
                        return false;
                    }
                }
            }

            if (virtualBand != null) {
                final String expression = paramVBExpression.getValueAsText();
                if (expression != null && expression.trim().length() != 0) {
                    final Product product = virtualBand.getProduct();
                    try {
                        Product[] products = getCompatibleProducts(virtualBand);
                        int defaultProductIndex = Arrays.asList(products).indexOf(product);
                        BandArithmetic.getValidMaskExpression(expression, products, defaultProductIndex, null);
                    } catch (ParseException e) {
                        JOptionPane.showMessageDialog(dialog.getJDialog(),
                                                      "Invalid expression syntax:\n" + e.getMessage()); /*I18N*/
                        return false;
                    }
                }
            }
            return true;
        }

        private Product[] getCompatibleProducts(RasterDataNode rasterDataNode) {
            List<Product> compatibleProducts = new ArrayList<Product>(12);
            Product vbProduct = rasterDataNode.getProduct();
            compatibleProducts.add(vbProduct);
            Product[] products = vbProduct.getProductManager().getProducts();
            final float geolocationEps = getGeolocationEps();
            for (Product product : products) {
                if (vbProduct != product) {
                    if (vbProduct.isCompatibleProduct(product, geolocationEps)) {
                        compatibleProducts.add(product);
                    }
                }
            }
            return compatibleProducts.toArray(new Product[compatibleProducts.size()]);
        }

        private float getGeolocationEps() {
            return (float) VisatApp.getApp().getPreferences().getPropertyDouble(VisatApp.PROPERTY_KEY_GEOLOCATION_EPS,
                                                                                VisatApp.PROPERTY_DEFAULT_GEOLOCATION_EPS);
        }

        public void changeProperties() {
            virtualBandPropertyChanged = false;
            validMaskPropertyChanged = false;

            final ProductNodeHandler listener = new ProductNodeHandler();

            try {
                node.getProduct().addProductNodeListener(listener);
                node.setName(paramName.getValueAsText());
                node.setDescription(paramDescription.getValueAsText());
                if (product != null) {
                    product.setProductType(paramProductType.getValueAsText());
                    if (paramStartTime.getValue() != null) {
                        final Date startDate = (Date) paramStartTime.getValue();
                        final int micros = getMicrosecondFraction(startDate);
                        product.setStartTime(ProductData.UTC.create(startDate, micros));
                    }
                    if (paramEndTime.getValue() != null) {
                        final Date endDate = (Date) paramEndTime.getValue();
                        final int micros = getMicrosecondFraction(endDate);
                        product.setEndTime(ProductData.UTC.create(endDate, micros));
                    }
                    product.setAutoGrouping(paramBandSubGroupPaths.getValueAsText());
                }
                if (rasterDataNode != null) {
                    final boolean noDataValueUsed = (Boolean) paramNoDataValueUsed.getValue();
                    rasterDataNode.setNoDataValueUsed(noDataValueUsed);
                    if (noDataValueUsed) {
                        rasterDataNode.setGeophysicalNoDataValue((Double) paramNoDataValue.getValue());
                    }
                    rasterDataNode.setUnit(paramGeophysUnit.getValueAsText());
                    rasterDataNode.setValidPixelExpression(paramValidPixelExpr.getValueAsText());

                    String uncertaintyRoleName = paramAncillaryRoleName.getValueAsText();
                    String uncertaintyBandName = paramAncillaryBandName.getValueAsText();
                    if (!uncertaintyRoleName.isEmpty()) {
                        RasterDataNode uncertaintyBand = !uncertaintyBandName.isEmpty() ? rasterDataNode.getProduct().getRasterDataNode(uncertaintyBandName) : null;
                        rasterDataNode.setAncillaryBand(uncertaintyRoleName, uncertaintyBand);
                    }
                }
                if (band != null) {
                    band.setSpectralWavelength((Float) paramSpectralWavelength.getValue());
                    band.setSpectralBandwidth((Float) paramSpectralBandwidth.getValue());
                }
                if (virtualBand != null) {
                    virtualBand.setExpression(paramVBExpression.getValueAsText());
                }
            } finally {
                node.getProduct().removeProductNodeListener(listener);
            }

            if (rasterDataNode != null && (virtualBandPropertyChanged || validMaskPropertyChanged)) {
                updateImages();
            }
        }

        private int getMicrosecondFraction(Date startDate) {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            return (int) (cal.get(Calendar.MILLISECOND) * 10.0e3);
        }

        private String formatBandSubGroupPaths() {
            final Product.AutoGrouping autoGrouping = product.getAutoGrouping();
            if (autoGrouping != null) {
                return autoGrouping.toString();
            } else {
                return "";
            }
        }

        private void updateImages() {
            final SwingWorker<Exception, Object> worker = new SwingWorker<Exception, Object>() {

                @Override
                protected Exception doInBackground() throws Exception {
                    final ProgressMonitor pm = new DialogProgressMonitor(visatApp.getMainFrame(), "Applying changes",
                                                                         Dialog.ModalityType.APPLICATION_MODAL);

                    pm.beginTask("Recomputing image(s)...", 3);
                    try {
                        if (virtualBandPropertyChanged && virtualBand != null) {
                            if (virtualBand.hasRasterData()) {
                                virtualBand.readRasterDataFully(ProgressMonitor.NULL);
                            }
                        }
                        pm.worked(1);
                        if (validMaskPropertyChanged) {
                            final JInternalFrame internalFrame = visatApp.findInternalFrame(rasterDataNode);
                            if (internalFrame != null) {
                                final ProductSceneView psv = getProductSceneView(internalFrame);
                                psv.updateNoDataImage();
                                pm.worked(1);
                            } else {
                                pm.worked(1);
                            }
                        }
                        visatApp.updateImages(new RasterDataNode[]{rasterDataNode});
                        pm.worked(1);
                    } catch (IOException e) {
                        return e;
                    } finally {
                        pm.done();
                    }
                    return null;
                }

                @Override
                public void done() {
                    Exception exception;
                    try {
                        exception = get();
                    } catch (Exception e) {
                        exception = e;
                    }
                    if (exception != null) {
                        Debug.trace(exception);
                        visatApp.showErrorDialog("Failed to compute band '" + node.getDisplayName() + "':\n"
                                                 + exception.getMessage()); /*I18N*/
                    }
                }
            };
            worker.execute();
        }

        private void initParamsForRasterDataNode(final RasterDataNode rasterDataNode) {
            this.rasterDataNode = rasterDataNode;
            initNoDataValueUsedParam();
            initNoDataValueParam();
            initUnitParam();
            initValidPixelExpressionParam();
            initUncertaintyBandNameParam();
        }

        private void initParamsForBand(Band band) {
            this.band = band;

            paramSpectralWavelength = new Parameter("SpectralWavelength", this.band.getSpectralWavelength());
            paramSpectralWavelength.getProperties().setLabel("Spectral wavelength");
            paramSpectralWavelength.getProperties().setPhysicalUnit("nm");
            paramSpectralWavelength.getProperties().setDescription("Spectral wavelength in nanometers");
            paramSpectralWavelength.getProperties().setNumCols(13);

            paramSpectralBandwidth = new Parameter("SpectralBandwidth", this.band.getSpectralBandwidth());
            paramSpectralBandwidth.getProperties().setLabel("Spectral bandwidth");
            paramSpectralBandwidth.getProperties().setPhysicalUnit("nm");
            paramSpectralBandwidth.getProperties().setDescription("Spectral bandwidth in nanometers");
            paramSpectralBandwidth.getProperties().setNumCols(13);
        }

        private void initParamsForVirtualBand(final VirtualBand virtualBand) {
            this.virtualBand = virtualBand;
            initVirtualBandExpressionParam();
        }

        private boolean ignoreVisit() {
            return product != null;
        }

        private void initProductTypeParam() {
            final ParamProperties properties = new ParamProperties(String.class);
            properties.setNullValueAllowed(false);
            properties.setEmptyValuesNotAllowed(true);
            properties.setLabel("Product type"); /*I18N*/
            paramProductType = new Parameter("productType", product.getProductType(), properties);
        }

        private void initProductStartStopParams() {
            final ParamProperties startProperties = createStartStopProperty("Start time", "Product start time (UTC)");
            final ProductData.UTC startTime = product.getStartTime();
            Date startDate = startTime != null ? startTime.getAsDate() : null;
            paramStartTime = new Parameter("startTime", startDate, startProperties);

            final ParamProperties endProperties = createStartStopProperty("End time", "Product end time (UTC)");
            final ProductData.UTC endTime = product.getEndTime();
            Date endDate = endTime != null ? endTime.getAsDate() : null;
            paramEndTime = new Parameter("endTime", endDate, endProperties);
        }

        private ParamProperties createStartStopProperty(String label, String description) {
            final ParamProperties properties = new ParamProperties(Date.class);
            properties.setEditorClass(DateEditor.class);
            properties.setValidatorClass(DateValidator.class);
            properties.setNullValueAllowed(true);
            properties.setEmptyValuesNotAllowed(false);
            properties.setLabel(label);
            properties.setDescription(description);
            return properties;
        }

        private void initProductBandGroupingParam() {
            final ParamProperties properties = new ParamProperties(String.class);
            properties.setNullValueAllowed(true);
            properties.setEmptyValuesNotAllowed(false);
            properties.setLabel("Band grouping"); /*I18N*/
            properties.setDescription(
                    "Colon-separated (':') list of band name parts which are used to auto-create band groups."); /*I18N*/
            properties.setNumRows(2);
            properties.setPropertyValue(ParamProperties.WORD_WRAP_KEY, true);
            paramBandSubGroupPaths = new Parameter("bandGrouping", formatBandSubGroupPaths(), properties);
        }

        private void initVirtualBandExpressionParam() {
            final ParamProperties properties = new ParamProperties(String.class);
            properties.setNullValueAllowed(true);
            properties.setLabel("Virtual band expression"); /*I18N*/
            properties.setNumRows(2);
            properties.setNumCols(42);
            properties.setDescription("The expression used to compute the pixel values of this band."); /*I18N*/
            properties.setEditorClass(GeneralExpressionEditor.class);
            // todo setting namespace as property to the ExpressionEditor for validating the expression
            properties.setPropertyValue(GeneralExpressionEditor.PROPERTY_KEY_SELECTED_PRODUCT,
                                        virtualBand.getProduct());
            properties.setPropertyValue(GeneralExpressionEditor.PROPERTY_KEY_INPUT_PRODUCTS,
                                        getCompatibleProducts(virtualBand));
            properties.setPropertyValue(GeneralExpressionEditor.PROPERTY_KEY_PREFERENCES,
                                        VisatApp.getApp().getPreferences());
            paramVBExpression = new Parameter("virtualBandExpr", virtualBand.getExpression(), properties);
            paramName.addParamChangeListener(new ParamChangeListener() {
                @Override
                public void parameterValueChanged(final ParamChangeEvent event) {
                    final String expresion = paramVBExpression.getValueAsText();
                    final String newExpression = StringUtils.replaceWord(expresion, (String) event.getOldValue(),
                                                                         paramName.getValueAsText());
                    paramVBExpression.setValueAsText(newExpression, null);
                }
            });
        }

        private void initValidPixelExpressionParam() {
            final ParamProperties properties = new ParamProperties(String.class);
            properties.setNullValueAllowed(true);
            properties.setLabel("Valid pixel expression"); /*I18N*/
            properties.setDescription("Boolean expression which is used to identify valid pixels"); /*I18N*/
            properties.setNumRows(2);
            properties.setEditorClass(BooleanExpressionEditor.class);
            // todo setting namespace as property to the ExpressionEditor for validating the expression
            properties.setPropertyValue(BooleanExpressionEditor.PROPERTY_KEY_SELECTED_PRODUCT,
                                        rasterDataNode.getProduct());
            properties.setPropertyValue(BooleanExpressionEditor.PROPERTY_KEY_INPUT_PRODUCTS,
                                        getCompatibleProducts(rasterDataNode));
            paramValidPixelExpr = new Parameter("validMaskExpr",
                                                rasterDataNode.getValidPixelExpression(),
                                                properties);
            paramName.addParamChangeListener(new ParamChangeListener() {
                @Override
                public void parameterValueChanged(final ParamChangeEvent event) {
                    final String expresion = paramValidPixelExpr.getValueAsText();
                    final String newExpression = StringUtils.replaceWord(expresion, (String) event.getOldValue(),
                                                                         paramName.getValueAsText());
                    paramValidPixelExpr.setValueAsText(newExpression, null);
                }
            });
        }

        private void initUncertaintyBandNameParam() {
            List<String> defaultRoleNames = Arrays.asList("uncertainty", "error", "variance", "confidence");

            Map<String, RasterDataNode> ancillaryBands = rasterDataNode.getAncillaryBands();
            LinkedHashSet<String> roleNames = new LinkedHashSet<>(ancillaryBands.keySet());
            String ancillaryRoleName;
            if (!roleNames.isEmpty()) {
                ancillaryRoleName = (String) roleNames.toArray()[0];
            } else {
                ancillaryRoleName = defaultRoleNames.get(0);
            }
            roleNames.addAll(defaultRoleNames);

            RasterDataNode ancillaryBand = ancillaryBands.get(ancillaryRoleName);
            String ancillaryBandName = ancillaryBand != null ? ancillaryBand.getName() : "";

            final ParamProperties arnProperties = new ParamProperties(String.class);
            arnProperties.setNullValueAllowed(false);
            arnProperties.setEmptyValuesNotAllowed(true);
            arnProperties.setLabel("Ancillary band role"); /*I18N*/
            arnProperties.setDescription("Role of the ancillary band"); /*I18N*/
            arnProperties.setValueSet(roleNames.toArray(new String[roleNames.size()]));
            arnProperties.setValueSetBound(false);
            paramAncillaryRoleName = new Parameter("ancillaryRoleName", ancillaryRoleName, arnProperties);

            final ParamProperties abnProperties = new ParamProperties(String.class);
            abnProperties.setNullValueAllowed(false);
            abnProperties.setEmptyValuesNotAllowed(false);
            abnProperties.setLabel("Ancillary band name"); /*I18N*/
            abnProperties.setDescription("Name of an ancillary band"); /*I18N*/
            abnProperties.setDefaultValue(ancillaryBandName);
            ArrayList<String> valueList = new ArrayList<>(Arrays.asList(rasterDataNode.getProduct().getBandNames()));
            if (!valueList.contains(ancillaryBandName)) {
                valueList.add(0, ancillaryBandName);
            }
            if (!valueList.contains("")) {
                valueList.add(0, "");
            }
            abnProperties.setValueSet(valueList.toArray(new String[valueList.size()]));
            paramAncillaryBandName = new Parameter("ancillaryBandName", ancillaryBandName, abnProperties);
            paramAncillaryBandName.addParamChangeListener(event -> {
                String bandName = paramAncillaryBandName.getValueAsText();
                for (Map.Entry<String, RasterDataNode> entry : rasterDataNode.getAncillaryBands().entrySet()) {
                    String roleName = entry.getKey();
                    String otherBandName = entry.getValue().getName();
                    if (bandName.equals(otherBandName)) {
                        paramAncillaryRoleName.setValueAsText(roleName, null);
                        break;
                    }
                }
            });
        }

        private void initUnitParam() {
            final ParamProperties properties = new ParamProperties(String.class);
            properties.setLabel("Geophysical unit");       /*I18N*/
            properties.setDescription("The geophysical unit of pixel values"); /*I18N*/
            paramGeophysUnit = new Parameter("unit",
                                             rasterDataNode.getUnit() == null ? "" : rasterDataNode.getUnit(),
                                             properties); /*I18N*/
        }

        private void initNoDataValueUsedParam() {
            final ParamProperties properties = new ParamProperties(Boolean.class);
            properties.setLabel("Use no-data value:"); /*I18N*/
            properties.setDescription("Indicates that the no-data value is used"); /*I18N*/
            paramNoDataValueUsed = new Parameter("noDataValueUsed",
                                                 rasterDataNode.isNoDataValueUsed(),
                                                 properties);
            paramNoDataValueUsed.addParamChangeListener(new ParamChangeListener() {
                @Override
                public void parameterValueChanged(final ParamChangeEvent event) {
                    paramNoDataValue.getEditor().setEnabled(
                            (Boolean) paramNoDataValueUsed.getValue());
                }
            });
        }

        private void initNoDataValueParam() {
            final Double noDataValue = rasterDataNode.getGeophysicalNoDataValue();
            final ParamProperties properties = new ParamProperties(Double.class);
            properties.setLabel("No-data value"); /*I18N*/
            properties.setDescription("The value used to indicate no-data"); /*I18N*/
            properties.setNumCols(13);
            paramNoDataValue = new Parameter("noDataValue", noDataValue, properties);
            paramNoDataValue.getEditor().setEnabled(rasterDataNode.isNoDataValueUsed());
        }

        private void initProductNodeParameters() {
            final ParamProperties nameProp = new ParamProperties(String.class);
            nameProp.setLabel("Name"); /*I18N*/
            paramName = new Parameter("nameParam", node.getName(), nameProp);
            if (node instanceof RasterDataNode) {
                addNameValidator();
            }

            final ParamProperties descProp = new ParamProperties(String.class);
            descProp.setLabel("Description"); /*I18N*/
            descProp.setNumRows(2);
            descProp.setPropertyValue(ParamProperties.WORD_WRAP_KEY, true);
            paramDescription = new Parameter("descParam", node.getDescription(), descProp);
        }


        private void initProductNodeUI() {
            setLayout(new GridBagLayout());
            gbc = GridBagUtils.createDefaultConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.weighty = 1;
            gbc.insets.top = 2;
            gbc.insets.bottom = 2;

            gbc.gridy++;
            gbc.weightx = 0;
            add(paramName.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            add(paramName.getEditor().getComponent(), gbc);
            gbc.gridy++;
            gbc.weightx = 0;
            add(paramDescription.getEditor().getLabelComponent(), gbc);
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1;
            gbc.weighty = 500;
            add(paramDescription.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;

        }

        private void initRasterDataNodeUI() {
            gbc.gridy++;
            gbc.weightx = 0;
            add(paramGeophysUnit.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            add(paramGeophysUnit.getEditor().getComponent(), gbc);

            gbc.insets.top += GROUP_GAP;
            gbc.gridy++;
            gbc.weightx = 0;
            add(paramNoDataValueUsed.getEditor().getComponent(), gbc);
            gbc.weightx = 1;
            add(paramNoDataValue.getEditor().getComponent(), gbc);
            gbc.insets.top -= GROUP_GAP;

            gbc.gridy++;
            gbc.weightx = 0;
            add(paramValidPixelExpr.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            gbc.weighty = 2000;
            gbc.fill = GridBagConstraints.BOTH;
            add(paramValidPixelExpr.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;

            gbc.gridy++;
            gbc.weightx = 0;
            add(paramAncillaryBandName.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            gbc.weighty = 2000;
            gbc.fill = GridBagConstraints.BOTH;
            add(paramAncillaryBandName.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;

            gbc.gridy++;
            gbc.weightx = 0;
            add(paramAncillaryRoleName.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            gbc.weighty = 2000;
            gbc.fill = GridBagConstraints.BOTH;
            add(paramAncillaryRoleName.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;
        }

        private void initProductUI() {
            gbc.gridy++;
            gbc.weightx = 0;
            add(paramProductType.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            add(paramProductType.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;

            gbc.gridy++;
            gbc.weightx = 0;
            add(paramStartTime.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            add(paramStartTime.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;

            gbc.gridy++;
            gbc.weightx = 0;
            add(paramEndTime.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            add(paramEndTime.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;

            gbc.gridy++;
            gbc.weightx = 0;
            add(paramBandSubGroupPaths.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            add(paramBandSubGroupPaths.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;
        }

        private void initBandUI() {
            gbc.insets.top += GROUP_GAP;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridy++;
            gbc.weightx = 0;
            add(paramSpectralWavelength.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            add(createValueUnitPair(paramSpectralWavelength.getEditor().getComponent(),
                                    paramSpectralWavelength.getEditor().getPhysUnitLabelComponent()), gbc);

            gbc.insets.top = 2;
            gbc.gridy++;
            gbc.weightx = 0;
            add(paramSpectralBandwidth.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            add(createValueUnitPair(paramSpectralBandwidth.getEditor().getComponent(),
                                    paramSpectralBandwidth.getEditor().getPhysUnitLabelComponent()), gbc);

            gbc.insets.top -= GROUP_GAP;
        }

        private JPanel createValueUnitPair(JComponent c1, JComponent c2) {
            final JPanel panel = new JPanel(new BorderLayout(2, 2));
            panel.add(c1, BorderLayout.CENTER);
            panel.add(c2, BorderLayout.EAST);
            return panel;
        }

        private void initVirtualBandUI() {
            gbc.insets.top += GROUP_GAP;
            gbc.gridy++;
            gbc.weightx = 0;
            add(paramVBExpression.getEditor().getLabelComponent(), gbc);
            gbc.weightx = 1;
            gbc.weighty = 2000;
            gbc.fill = GridBagConstraints.BOTH;
            add(paramVBExpression.getEditor().getComponent(), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;
            gbc.insets.top -= GROUP_GAP;
        }

        private void addNameValidator() {
            paramName.getProperties().setValidatorClass(ProductNodeNameValidator.class);
            paramName.getProperties().setPropertyValue(ProductNodeNameValidator.PRODUCT_PROPERTY_KEY,
                                                       node.getProduct());
        }


        private class ProductNodeHandler extends ProductNodeListenerAdapter {

            @Override
            public void nodeChanged(ProductNodeEvent event) {
                if (isVirtualBandRelevantPropertyName(event.getPropertyName())) {
                    virtualBandPropertyChanged = true;
                }
                ProductNode productNode = event.getSourceNode();
                if (productNode instanceof RasterDataNode) {
                    if (RasterDataNode.isValidMaskProperty(event.getPropertyName())) {
                        validMaskPropertyChanged = true;
                    }
                }
            }
        }
    }

    private static ProductSceneView getProductSceneView(final JInternalFrame internalFrame) {
        final Container contentPane = internalFrame.getContentPane();
        if (contentPane instanceof ProductSceneView) {
            return (ProductSceneView) contentPane;
        }
        return null;
    }


    private static boolean isVirtualBandRelevantPropertyName(final String propertyName) {
        return VirtualBand.PROPERTY_NAME_EXPRESSION.equals(propertyName);
    }

    private class PropertyEditorDialog extends ModalDialog {

        private final EditorContent editorContent;

        private PropertyEditorDialog(EditorContent editorContent) {
            super(PropertyEditor.this.visatApp.getMainFrame(), PropertyEditor.getTitleText(editorContent),
                  editorContent, ModalDialog.ID_OK_CANCEL_HELP, "propertyEditor");
            this.editorContent = editorContent;
        }

        @Override
        protected boolean verifyUserInput() {
            return editorContent.validateProperties();
        }
    }
}

