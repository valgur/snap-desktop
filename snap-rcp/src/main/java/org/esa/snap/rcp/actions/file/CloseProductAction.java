/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.snap.rcp.actions.file;

import com.bc.jexp.ParseException;
import org.esa.snap.framework.dataio.ProductReader;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductNode;
import org.esa.snap.framework.datamodel.RasterDataNode;
import org.esa.snap.framework.datamodel.VirtualBand;
import org.esa.snap.netbeans.docwin.DocumentWindow;
import org.esa.snap.netbeans.docwin.DocumentWindowManager;
import org.esa.snap.netbeans.docwin.WindowUtilities;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.WeakSet;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Action which closes a selected product.
 *
 * @author Norman
 */
@ActionID(
        category = "File",
        id = "CloseProductAction"
)
@ActionRegistration(
        displayName = "#CTL_CloseProductActionName"
)
@ActionReference(path = "Menu/File", position = 20, separatorBefore = 18)
@NbBundle.Messages({
        "CTL_CloseProductActionName=Close Product"
})
public final class CloseProductAction extends AbstractAction{

    private final WeakSet<Product> productSet;

    public CloseProductAction(List<Product> products) {
        productSet = new WeakSet<>();
        productSet.addAll(products);
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        execute();
    }

    /**
     * Executes the action command.
     *
     * @return {@code Boolean.TRUE} on success, {@code Boolean.FALSE} on failure, or {@code null} on cancellation.
     */
    public Boolean execute() {
        return closeProducts(new HashSet<>(productSet));
    }

    private static Boolean closeProducts(Set<Product> products) {
        List<Product> closeList = new ArrayList<>(products);
        List<Product> saveList = new ArrayList<>();

        Product[] products1 = SnapApp.getDefault().getProductManager().getProducts();
        HashSet<Product> stillOpenProducts = new HashSet<>(Arrays.asList(products1));
        stillOpenProducts.removeAll(closeList);

        if (!stillOpenProducts.isEmpty()) {
            for (Product productToBeClosed : closeList) {
                Product firstSourceProduct = findFirstSourceProduct(productToBeClosed, stillOpenProducts);
                if (firstSourceProduct != null) {
                    SnapDialogs.showInformation("Close Not Possible",
                                                String.format("Can't close product '%s' because it is in use%n" +
                                                              "by product '%s'.%n" +
                                                              "Please close the latter first.",
                                                              productToBeClosed.getName(),
                                                              firstSourceProduct.getName()), null);
                    return false;
                }
            }
        }

        for (Product product : products) {
            if (product.isModified()) {
                SnapDialogs.Answer answer = SnapDialogs.requestDecision(Bundle.CTL_OpenProductActionName(),
                                                                        MessageFormat.format("Product ''{0}'' has been modified.\n" +
                                                                                             "Do you want to save it?",
                                                                                             product.getName()), true, null);
                if (answer == SnapDialogs.Answer.YES) {
                    saveList.add(product);
                } else if (answer == SnapDialogs.Answer.CANCELLED) {
                    return null;
                }
            }
        }

        for (Product product : saveList) {
            Boolean status = new SaveProductAction(product).execute();
            if (status == null) {
                // cancelled
                return null;
            }
        }

        for (Product product : closeList) {
            WindowUtilities.getOpened(DocumentWindow.class)
                    .filter(dw -> (dw.getDocument() instanceof ProductNode) && ((ProductNode) dw.getDocument()).getProduct() == product)
                    .forEach(dw -> DocumentWindowManager.getDefault().closeWindow(dw));
            SnapApp.getDefault().getProductManager().removeProduct(product);
        }

        closeList.forEach(Product::dispose);
        return true;
    }

    static Product findFirstSourceProduct(Product productToClose, Set<Product> productsStillOpen) {
        Product firstSourceProduct = findFirstDirectSourceProduct(productToClose, productsStillOpen);
        if (firstSourceProduct != null) {
            return firstSourceProduct;
        }
        return findFirstExpressionSourceProduct(productToClose, productsStillOpen);
    }

    private static Product findFirstDirectSourceProduct(Product productToBeClosed, Set<Product> productsStillOpen) {
        for (Product openProduct : productsStillOpen) {
            final ProductReader reader = openProduct.getProductReader();
            if (reader != null) {
                final Object input = reader.getInput();
                if (input instanceof Product) {
                    Product sourceProduct = (Product) input;
                    if (productToBeClosed.equals(sourceProduct)) {
                        return openProduct;
                    } else {
                        Product indirectSourceProduct = findFirstDirectSourceProduct(sourceProduct, productsStillOpen);
                        if (indirectSourceProduct != null) {
                            return openProduct;
                        }
                    }
                } else {
                    if (input instanceof Product[]) {
                        for (final Product sourceProduct : (Product[]) input) {
                            if (productToBeClosed.equals(sourceProduct)) {
                                return openProduct;
                            }
                            Product indirectSourceProduct = findFirstDirectSourceProduct(sourceProduct, productsStillOpen);
                            if (indirectSourceProduct != null) {
                                return openProduct;
                            }
                        }
                    }
                }
            }

        }
        return null;
    }

    static Product findFirstExpressionSourceProduct(Product productToBeClosed, Set<Product> productsStillOpen) {
        for (Product openProduct : productsStillOpen) {
            Band[] bands = openProduct.getBands();
            for (Band band : bands) {
                if (band instanceof VirtualBand) {
                    VirtualBand virtualBand = (VirtualBand) band;
                    try {
                        RasterDataNode[] nodes = openProduct.getRefRasterDataNodes(virtualBand.getExpression());
                        for (RasterDataNode node : nodes) {
                            if (productToBeClosed.equals(node.getProduct())) {
                                return openProduct;
                            }
                        }
                    } catch (ParseException e) {
                        // ok
                    }
                }
            }
        }
        return null;
    }


}
