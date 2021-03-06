package org.esa.snap.rcp.placemark.pin;

import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.PinDescriptor;
import org.esa.snap.framework.datamodel.PlacemarkDescriptor;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.TiePointGrid;
import org.esa.snap.rcp.placemark.PlacemarkManagerTopComponent;
import org.esa.snap.rcp.placemark.TableModelFactory;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

@TopComponent.Description(
        preferredID = "PinManagerTopComponent",
        iconBase = "org/esa/snap/rcp/icons/PinManager.gif",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS //todo define
)
@TopComponent.Registration(
        mode = "navigator",
        openAtStartup = false,
        position = 1
)
@ActionID(category = "Window", id = "org.esa.snap.rcp.placemark.pin.PinManagerTopComponent")
@ActionReferences({
                          @ActionReference(path = "Menu/Window/Tool Windows"),
                          @ActionReference(path = "Toolbars/Views")
                  })
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_PinManagerTopComponent_Name",
        preferredID = "PinManagerTopComponent"
)
@NbBundle.Messages({
                           "CTL_PinManagerTopComponent_Name=Pin Manager",
                           "CTL_PinManagerTopComponent_HelpId=showPinManagerWnd"
                   })
/**
 * @author Tonio Fincke
 */
public class PinManagerTopComponent extends PlacemarkManagerTopComponent {

    public PinManagerTopComponent() {
        super(PinDescriptor.getInstance(), new TableModelFactory() {
            @Override
            public PinTableModel createTableModel(PlacemarkDescriptor placemarkDescriptor, Product product,
                                                  Band[] selectedBands, TiePointGrid[] selectedGrids) {
                return new PinTableModel(placemarkDescriptor, product, selectedBands, selectedGrids);
            }
        });
    }

    @Override
    protected String getTitle() {
        return Bundle.CTL_PinManagerTopComponent_Name();
    }

    @Override
    protected String getHelpId() {
        return Bundle.CTL_PinManagerTopComponent_HelpId();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return new HelpCtx(Bundle.CTL_PinManagerTopComponent_HelpId());
    }
}
