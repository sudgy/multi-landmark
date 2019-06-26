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

/**
 * This is the default implementation of the MultiLandmark Op.
 *
 * @see MultiLandmark
 * @author David Cohoe
 */
@Plugin(type = MultiLandmark.class)
public class DefaultMultiLandmark extends AbstractOp implements MultiLandmark {

    // Inputs
    @Parameter private ImagePlus[] P_images;
    @Parameter private int P_interpolation_method;
    @Parameter private Class<? extends AbstractAffineModel2D<?>> P_model_type;
    @Parameter private boolean P_stop_interpolation;
    @Parameter private float P_discontinuity_threshold;
    @Parameter private int P_scale_to;

    // Outputs
    @Parameter(type = ItemIO.OUTPUT) private ImagePlus[] P_output;

    // Others
    @Parameter private StatusService P_status;
    @Parameter private UIService P_ui;

    @Override
    public void run()
    {
        int images_size = P_images.length;
        ArrayList<ModelData> all_data =
            new ArrayList<>(images_size * (images_size - 1));
        P_output = new ImagePlus[images_size];
        P_status.showStatus("Calculating transforms...");
        // The index to scale to
        // If scaling to the biggest or the smallest image, this value will
        // change
        int index = P_scale_to;
        if (P_scale_to >= 0) {
            for (int i = 0; i < images_size - 1; ++i) {
                if (i != P_scale_to) {
                    try {
                        all_data.add(new ModelData(P_images[i],
                                                   P_images[P_scale_to]));
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
            // An array used to determine which image is "biggest".  Each
            // element is how many other images are "smaller" than the index
            int[] biggest_to = new int[images_size];
            for (int i = 0; i < images_size - 1; ++i) {
                for (int j = i + 1; j < images_size; ++j) {
                    try {
                        ModelData data = new ModelData(P_images[i],
                                                       P_images[j]);
                        all_data.add(data);
                        if (data.target() == P_images[i]) ++biggest_to[i];
                        else ++biggest_to[j];
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
            int max = 0;
            for (int i = 0; i < images_size; ++i) {
                if (biggest_to[i] > max) {
                    max = biggest_to[i];
                    index = i;
                }
            }
        }
        P_status.showStatus("Performing transforms...");
        int i = 0;
        for (ModelData d : all_data) {
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
        P_output[i] = new ImagePlus(P_images[index].getTitle() + " final",
                                        P_images[index].getStack());
    }
    /* This class holds the models used to perform the transformations.
     * They store the model and the indices of the images that it transforms
     * from and to.  This class really does all of the work here.
     */
    private class ModelData {
        private AbstractAffineModel2D<?> M_model;
        private ImagePlus M_source;
        private ImagePlus M_target;
        private int M_source_width;
        private int M_source_height;
        private int M_target_width;
        private int M_target_height;
        // This constructor initializes the model and sets M_source and M_target
        // to the correct values.  M_source and M_target might need to be
        // switched so that it is always scaling up.
        public ModelData(ImagePlus i, ImagePlus j)
            throws NotEnoughDataPointsException, IllDefinedDataPointsException
        {
            M_source = i;
            M_target = j;
            M_model = get_model();
            double[] model_array = new double[6];
            M_model.toArray(model_array);
            double determinant = model_array[0] * model_array[3]
                               - model_array[1] * model_array[2];
            if (determinant < 1) {
                M_model = M_model.createInverse();
                ImagePlus temp = M_source;
                M_source = M_target;
                M_target = temp;
            }
            {
                M_model.toArray(model_array);
                //IJ.showMessage("" + i + "->" + j + "\n" +
                //               "[" + model_array[0] + ", " + model_array[1] + "]\n" +
                //               "[" + model_array[2] + ", " + model_array[3] + "]\n" +
                //               "[" + model_array[4] + ", " + model_array[5] + "]");
            }
            M_source_width = M_source.getWidth();
            M_source_height = M_source.getHeight();
            M_target_width = M_target.getWidth();
            M_target_height = M_target.getHeight();
        }
        public ImagePlus source() {return M_source;}
        public ImagePlus target() {return M_target;}
        // Acquire the model for transforming the image at index id1 to id2
        private AbstractAffineModel2D<?> get_model()
            throws NotEnoughDataPointsException, IllDefinedDataPointsException
        {
            ArrayList<PointMatch> matches = new ArrayList<PointMatch>();
            List<Point> source_points
                = Util.pointRoiToPoints((PointRoi)M_source.getRoi());
            List<Point> target_points
                = Util.pointRoiToPoints((PointRoi)M_target.getRoi());
            int max = Math.min(source_points.size(), target_points.size());
            for (int i = 0; i < max; ++i) {
                matches.add(new PointMatch(source_points.get(i),
                                           target_points.get(i)));
            }
            try {
                AbstractAffineModel2D<?> model = P_model_type.getConstructor()
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
            int stack_size
                = Math.min(M_source.getImageStackSize(),
                           M_target.getImageStackSize());
            ImageStack result
                = new ImageStack(M_target.getWidth(),
                                 M_target.getHeight());
            for (int i = 0; i < stack_size; ++i) {
                if (stack_size > 1) {
                    // Note that the status bar will reset multiple times
                    // depending on how many ImagePluses you started with
                    P_status.showStatus(i + 1, stack_size, "Transforming "
                        + M_source.getTitle());
                }
                ImageProcessor source
                    = M_source.getStack().getProcessor(i + 1);
                source.setInterpolationMethod(P_interpolation_method);
                ImageProcessor target
                    = M_target.getProcessor().createProcessor(
                        M_target.getWidth(),
                        M_target.getHeight()
                    );
                final double[] t = new double[2];
                final float[][] pixels = source.getFloatArray();
                for (int y = 0; y < M_target_height; ++y) {
                    for (int x = 0; x < M_target_width; ++x) {
                        t[0] = x;
                        t[1] = y;
                        M_model.applyInverseInPlace(t);
                        put_pixel(source, target, t[0], t[1], x, y, pixels);
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
        private void put_pixel(ImageProcessor source, ImageProcessor target,
                               double sx, double sy, int tx, int ty,
                               final float[][] pixels)
        {
            if (   sx >= 0 && (sx+0.5) < M_source_width
                && sy >= 0 && (sy+0.5) < M_source_height) {
                if (P_stop_interpolation) {
                    int x_pos = (int)(sx + 0.5);
                    int y_pos = (int)(sy + 0.5);
                    // If there are any discontinuities in any of the eight
                    // directions, don't interpolate
                    float base_value = pixels[x_pos][y_pos];
                    for (int new_x = x_pos-1; new_x <= x_pos+1; ++new_x) {
                        for (int new_y = y_pos-1; new_y <= y_pos+1; ++new_y) {
                            if (new_x == x_pos && new_y == y_pos) continue;
                            if (new_x < 0 || new_y < 0
                                || new_x >= M_source_width
                                || new_y >= M_source_height) continue;
                            float new_value = pixels[new_x][new_y];
                            if (Math.abs(base_value - new_value)
                                    > P_discontinuity_threshold) {
                                target.putPixel(tx, ty,
                                                source.getPixel(x_pos, y_pos));
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
