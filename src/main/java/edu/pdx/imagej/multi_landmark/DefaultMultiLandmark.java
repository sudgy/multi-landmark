/* Copyright (C) 2019 Portland State University
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of version 3 of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * For any questions regarding the license, please contact the Free Software
 * Foundation.  For any other questions regarding this program, please contact
 * David Cohoe at dcohoe@pdx.edu.
 */

package edu.pdx.imagej.multi_landmark;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.gui.PointRoi;

import net.imagej.ops.AbstractOp;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.app.StatusService;
import org.scijava.ui.UIService;

import mpicbg.ij.util.Util;
import mpicbg.models.*;

/** This is the default implementation of the MultiLandmark Op.
 *
 * To run this op yourself, it has the name "multi-image landmark
 * correspondences" and has the following parameters:
 * <ol>
 *      <li>Images: An array of <code>ImagePlus</code> that you want to align.
 *                  They all <strong>must</strong> have Point ROIs.
 *      <li>Interpolation method: The method of interpolation.  The values are
 *                                the same as in <code>ImageProcessor</code>.
 *      <li>Model type: A <code>Class</code> of the mpicbg model you want to
 *                      use to transform.  It must extend from <code>
 *                      AbstractAffineModel2D</code>.
 *      <li>Stop interpolation: A boolean for if you want to stop interpolation
 *                              at discontinuities.  When transforming phase
 *                              images, it is a good idea to use this.
 *      <li>Discontinuity threshold: a float representing how big of a change
 *                                   there must be for it to count as a
 *                                   discontinuity.
 *      <li>Scale to: An integer representing which image to scale to.  If it is
 *                    -1, all images will be scaled to the biggest one.  If it
 *                    is -2, all images will be scaled to the smallest one.  If
 *                    it is nonnegative, it will use that value as the index
 *                    into the images array and will scale everything to that
 *                    image.
 *      <li>Show matrices: A boolean for if you want to see the matrices being
 *                         used to transform.
 * </ol>
 *
 * @see MultiLandmark
 */
@Plugin(type = MultiLandmark.class)
public class DefaultMultiLandmark extends AbstractOp implements MultiLandmark {

    // Inputs
    @Parameter private ImagePlus[] P_images;
    @Parameter private int P_interpolationMethod;
    @Parameter private Class<? extends AbstractAffineModel2D<?>> P_modelType;
    @Parameter private boolean P_stopInterpolation;
    @Parameter private float P_discontinuityThreshold;
    @Parameter private int P_scaleTo;
    @Parameter private boolean P_showMatrices;

    // Outputs
    @Parameter(type = ItemIO.OUTPUT) private ImagePlus[] P_output;

    // Others
    @Parameter private StatusService P_status;
    @Parameter private UIService P_ui;

    @Override
    public void run()
    {
        int imagesSize = P_images.length;
        ArrayList<ModelData> allData =
            new ArrayList<>(imagesSize * (imagesSize - 1));
        P_output = new ImagePlus[imagesSize];
        P_status.showStatus("Calculating transforms...");
        // The index to scale to
        // If scaling to the biggest or the smallest image, this value will
        // change
        int index = P_scaleTo;
        if (P_scaleTo >= 0) {
            for (int i = 0; i < imagesSize; ++i) {
                if (i != P_scaleTo) {
                    try {
                        allData.add(new ModelData(P_images[i],
                                                   P_images[P_scaleTo]));
                    }
                    catch (NotEnoughDataPointsException
                         | IllDefinedDataPointsException e) {
                        P_ui.showDialog("There are not enough data points to "
                            + "determine a transform.", "Error");
                        P_output = null;
                        return;
                    }
                }
            }
        }
        // Either scaling to biggest or smallest
        else {
            // An array used to determine which image is "biggest" (or
            // smallest).  Each element is how many other images are "smaller"
            // (bigger) than the index
            int[] biggestTo = new int[imagesSize];
            for (int i = 0; i < imagesSize - 1; ++i) {
                for (int j = i + 1; j < imagesSize; ++j) {
                    try {
                        ModelData data = new ModelData(P_images[i],
                                                       P_images[j]);
                        allData.add(data);
                        if (data.target() == P_images[i]) ++biggestTo[i];
                        else ++biggestTo[j];
                    }
                    catch (NotEnoughDataPointsException
                         | IllDefinedDataPointsException e) {
                        P_ui.showDialog("There are not enough data points to "
                            + "determine a transform.", "Error");
                        P_output = null;
                        return;
                    }
                }
            }
            int val = 0;
            for (int i = 0; i < imagesSize; ++i) {
                if (biggestTo[i] > val) {
                    val = biggestTo[i];
                    index = i;
                }
            }
        }
        P_status.showStatus("Performing transforms...");
        int i = 0;
        for (ModelData d : allData) {
            try {
                if (d.target() == P_images[index]) {
                    P_output[i++] = d.transform();
                }
            }
            catch (NoninvertibleModelException e) {
                P_ui.showDialog("The resulting transform was non-invertible.",
                                "Error");
                P_output = null;
                return;
            }
        }
        P_output[i] = P_images[index].duplicate();
        P_output[i].setTitle(P_images[index].getTitle() + " final");
    }
    /* This class holds the models used to perform the transformations.
     * They store the model and the indices of the images that it transforms
     * from and to.  This class really does all of the work here.
     */
    private class ModelData {
        private AbstractAffineModel2D<?> M_model;
        private ImagePlus M_source;
        private ImagePlus M_target;
        private int M_sourceWidth;
        private int M_sourceHeight;
        private int M_targetWidth;
        private int M_targetHeight;
        // This constructor initializes the model and sets M_source and M_target
        // to the correct values.  M_source and M_target might need to be
        // switched so that it is always scaling up.
        public ModelData(ImagePlus i, ImagePlus j)
            throws NotEnoughDataPointsException, IllDefinedDataPointsException
        {
            M_source = i;
            M_target = j;
            M_model = getModel();
            double[] modelArray = new double[6];
            M_model.toArray(modelArray);
            double determinant = modelArray[0] * modelArray[3]
                               - modelArray[1] * modelArray[2];
            if ( (P_scaleTo == -1 && determinant < 1) ||
                 (P_scaleTo == -2 && determinant > 1) ) {
                M_model = M_model.createInverse();
                ImagePlus temp = M_source;
                M_source = M_target;
                M_target = temp;
            }
            if (P_showMatrices) {
                M_model.toArray(modelArray);
                IJ.log("Transforming from " + M_source.getTitle() + " to "
                    + M_target.getTitle() + " has the following matrix:\n"
                    + "[" + modelArray[0] + ", " + modelArray[1] + "]\n"
                    + "[" + modelArray[2] + ", " + modelArray[3] + "]\n"
                    + "[" + modelArray[4] + ", " + modelArray[5] + "]");
            }
            M_sourceWidth = M_source.getWidth();
            M_sourceHeight = M_source.getHeight();
            M_targetWidth = M_target.getWidth();
            M_targetHeight = M_target.getHeight();
        }
        public ImagePlus source() {return M_source;}
        public ImagePlus target() {return M_target;}
        // Acquire the model for transforming the image from M_source to
        // M_target
        private AbstractAffineModel2D<?> getModel()
            throws NotEnoughDataPointsException, IllDefinedDataPointsException
        {
            ArrayList<PointMatch> matches = new ArrayList<PointMatch>();
            List<Point> sourcePoints
                = Util.pointRoiToPoints((PointRoi)M_source.getRoi());
            List<Point> targetPoints
                = Util.pointRoiToPoints((PointRoi)M_target.getRoi());
            int max = Math.min(sourcePoints.size(), targetPoints.size());
            for (int i = 0; i < max; ++i) {
                matches.add(new PointMatch(sourcePoints.get(i),
                                           targetPoints.get(i)));
            }
            try {
                AbstractAffineModel2D<?> model = P_modelType.getConstructor()
                                                             .newInstance();
                model.fit(matches);
                return model;
            }
            catch (NoSuchMethodException  | InstantiationException
                 | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        // Perform the transform
        public ImagePlus transform()
            throws NoninvertibleModelException
        {
            int stackSize
                = Math.min(M_source.getImageStackSize(),
                           M_target.getImageStackSize());
            ImageStack result
                = new ImageStack(M_target.getWidth(),
                                 M_target.getHeight());
            for (int i = 0; i < stackSize; ++i) {
                if (stackSize > 1) {
                    // Note that the status bar will reset multiple times
                    // depending on how many ImagePluses you started with
                    P_status.showStatus(i + 1, stackSize, "Transforming "
                        + M_source.getTitle());
                }
                ImageProcessor source
                    = M_source.getStack().getProcessor(i + 1);
                source.setInterpolationMethod(P_interpolationMethod);
                ImageProcessor target
                    = M_target.getProcessor().createProcessor(
                        M_target.getWidth(),
                        M_target.getHeight()
                    );
                final double[] t = new double[2];
                final float[][] pixels = source.getFloatArray();
                for (int y = 0; y < M_targetHeight; ++y) {
                    for (int x = 0; x < M_targetWidth; ++x) {
                        t[0] = x;
                        t[1] = y;
                        M_model.applyInverseInPlace(t);
                        putPixel(source, target, t[0], t[1], x, y, pixels);
                    }
                }
                result.addSlice(M_source.getStack()
                                                  .getSliceLabel(i + 1),
                                target);
            }
            return new ImagePlus(M_source.getTitle() + " final",
                                 result);
        }
        /* Puts the pixel from source at (sx, sy) on target at (tx, ty), taking
         * into acount any interpolation complications necessary.
         *
         * source: ImageProcessor to source from (interpolation method
         *         is already set).
         * target: ImageProcessor to apply to
         * sx:     Source x coordinate, can be non-integer
         * sy:     Source y coordinate, can be non-integer
         * tx:     Target x coordinate, must be integer
         * ty:     Target y coordinate, must be integer
         * pixels: Source pixels, as a float array
         */
        private void putPixel(ImageProcessor source, ImageProcessor target,
                               double sx, double sy, int tx, int ty,
                               final float[][] pixels)
        {
            if (   sx >= 0 && (sx+0.5) < M_sourceWidth
                && sy >= 0 && (sy+0.5) < M_sourceHeight) {
                if (P_stopInterpolation) {
                    int xPos = (int)(sx + 0.5);
                    int yPos = (int)(sy + 0.5);
                    // If there are any discontinuities in any of the eight
                    // directions, don't interpolate
                    float baseValue = pixels[xPos][yPos];
                    for (int newX = xPos-1; newX <= xPos+1; ++newX) {
                        for (int newY = yPos-1; newY <= yPos+1; ++newY) {
                            if (newX == xPos && newY == yPos) continue;
                            if (newX < 0 || newY < 0
                                || newX >= M_sourceWidth
                                || newY >= M_sourceHeight) continue;
                            float newValue = pixels[newX][newY];
                            if (Math.abs(baseValue - newValue)
                                    > P_discontinuityThreshold) {
                                target.putPixel(tx, ty,
                                                source.getPixel(xPos, yPos));
                                return;
                            }
                        }
                    }
                }
                target.putPixel(tx, ty, source.getPixelInterpolated(sx, sy));
            }
        }
    }
}
