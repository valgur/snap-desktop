package org.esa.snap.framework.ui.product;

import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.grender.Rendering;
import com.bc.ceres.swing.figure.support.DefaultFigureStyle;
import com.vividsolutions.jts.geom.Geometry;
import org.esa.snap.framework.datamodel.RasterDataNode;
import org.esa.snap.framework.datamodel.SceneRasterTransform;
import org.esa.snap.framework.datamodel.VectorDataNode;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.operation.MathTransform2D;
import org.opengis.referencing.operation.TransformException;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * A special layer type that is used to create layers for {@link VectorDataNode}s that
 * have a special feature type. In this case "org.esa.snap.TrackPoint".
 * <p>
 * <i>Note: this is experimental code.</i>
 *
 * @author Norman Fomferra
 * @since BEAM 4.10
 */
public class TrackLayerType extends VectorDataLayerType {

    public static boolean isTrackPointNode(VectorDataNode node) {
        final Object trackPoints = node.getFeatureType().getUserData().get("trackPoints");
        return trackPoints != null && trackPoints.toString().equals("true");
    }

    @Override
    protected VectorDataLayer createLayer(VectorDataNode vectorDataNode, RasterDataNode rasterDataNode, PropertySet configuration) {
        return new TrackLayer(this, vectorDataNode, rasterDataNode.getSceneRasterTransform(), configuration);
    }

    public static class TrackLayer extends VectorDataLayer {

        public static final Color STROKE_COLOR = Color.ORANGE;
        public static final double STROKE_OPACITY = 0.8;
        public static final double STROKE_WIDTH = 2.0;
        public static final double FILL_OPACITY = 0.5;
        public static final Color FILL_COLOR = Color.WHITE;

        private final Paint strokePaint;

        public TrackLayer(VectorDataLayerType vectorDataLayerType, VectorDataNode vectorDataNode,
                          SceneRasterTransform sceneRasterTransform, PropertySet configuration) {
            super(vectorDataLayerType, vectorDataNode, sceneRasterTransform, configuration);
            String styleCss = vectorDataNode.getDefaultStyleCss();
            DefaultFigureStyle style = new DefaultFigureStyle(styleCss);
            style.fromCssString(styleCss);
            style.setSymbolName("circle");
            style.setStrokeColor(STROKE_COLOR);
            style.setStrokeWidth(STROKE_WIDTH);
            style.setStrokeOpacity(STROKE_OPACITY);
            style.setFillColor(FILL_COLOR);
            style.setFillOpacity(FILL_OPACITY);
            strokePaint = style.getStrokePaint();
            vectorDataNode.setDefaultStyleCss(style.toCssString());
        }

        @Override
        protected void renderLayer(Rendering rendering) {
            drawTrackPointConnections(rendering);
            super.renderLayer(rendering);
        }

        private void drawTrackPointConnections(Rendering rendering) {

            Graphics2D g = rendering.getGraphics();
            AffineTransform oldTransform = g.getTransform();
            try {
                g.transform(rendering.getViewport().getModelToViewTransform());
                drawTrackPointConnections0(rendering);
            } finally {
                g.setTransform(oldTransform);
            }
        }

        private void drawTrackPointConnections0(Rendering rendering) {
            // todo - get these styles from vector data node.  (nf)
            rendering.getGraphics().setPaint(strokePaint);
            float scalingFactor = (float) rendering.getViewport().getViewToModelTransform().getScaleX();
            float effectiveStrokeWidth = (float) (scalingFactor * STROKE_WIDTH);
            float effectiveDash = Math.max(1.0F, scalingFactor * 5.0F);
            float effectiveMeterLimit = Math.max(1.0F, scalingFactor * 10.0F);
            BasicStroke basicStroke = new BasicStroke(effectiveStrokeWidth,
                                                      BasicStroke.CAP_SQUARE,
                                                      BasicStroke.JOIN_MITER,
                                                      effectiveMeterLimit,
                                                      new float[]{
                                                              effectiveDash,
                                                              effectiveDash},
                                                      0.0f);
            rendering.getGraphics().setStroke(basicStroke);

            // FeatureCollection.toArray() returns the feature in original order
            // todo - must actually sort using some (timestamp) attribute (nf)
            SimpleFeature[] features = getVectorDataNode().getFeatureCollection().toArray(new SimpleFeature[0]);
            double lastX = 0;
            double lastY = 0;
            for (int i = 0; i < features.length; i++) {
                SimpleFeature feature = features[i];
                Geometry geometry = (Geometry) feature.getDefaultGeometry();
                com.vividsolutions.jts.geom.Point centroid = geometry.getCentroid();
                final SceneRasterTransform sceneRasterTransform = getSceneRasterTransform();
                double sceneRasterCentroidX = centroid.getX();
                double sceneRasterCentroidY = centroid.getY();
                if (sceneRasterTransform != SceneRasterTransform.IDENTITY) {
                    final Point2D.Double start = new Point2D.Double(sceneRasterCentroidX, sceneRasterCentroidY);
                    final Point2D.Double target = new Point2D.Double();
                    try {
                        final MathTransform2D inverse = sceneRasterTransform.getInverse();
                        if (inverse == null) {
                            //todo error handling correct?
                            return;
                        }
                        inverse.transform(start, target);
                        sceneRasterCentroidX = target.getX();
                        sceneRasterCentroidY = target.getY();
                    } catch (TransformException e) {
                        e.printStackTrace();
                        //todo error handling correct?
                        return;
                    }
                }
                if (i > 0) {
                    rendering.getGraphics().draw(new Line2D.Double(lastX, lastY, sceneRasterCentroidX, sceneRasterCentroidY));
                }
                lastX = centroid.getX();
                lastY = centroid.getY();
            }
        }
    }
}
