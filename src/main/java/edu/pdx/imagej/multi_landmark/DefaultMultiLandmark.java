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
    private int M_images_size = 0; // Number of eligible images
    private ImageProcessor[] M_processors; // Their processors
    private PointRoi[] M_rois; // Their point rois
    // An array used to determine which image is "biggest".
    // Each element is how many other images are "smaller" than the index
    private int[] M_biggest_to;
    private ArrayList<ModelData> M_data;

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
    public void run() {
        init();
        P_status.showStatus("Calculating transforms...");
        // The index to scale to
        // If scaling to the biggest or the smallest image, this value will
        // change
        int index = P_scale_to;
        if (P_scale_to >= 0) {
            for (int i = 0; i < M_images_size - 1; ++i) {
                if (i != P_scale_to) {
                    try {M_data.add(new ModelData(i, P_scale_to));}
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
            for (int i = 0; i < M_images_size - 1; ++i) {
                for (int j = i + 1; j < M_images_size; ++j) {
                    try {M_data.add(new ModelData(i, j));}
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
            for (int i = 0; i < M_images_size; ++i) {
                // M_biggest_to was set while creating M_data
                if (M_biggest_to[i] > max) {
                    max = M_biggest_to[i];
                    index = i;
                }
            }
        }
        P_status.showStatus("Performing transforms...");
        for (ModelData d : M_data) {
            try {d.try_transform(index);}
            catch (NoninvertibleModelException e) {
                P_ui.showDialog("The resulting transform was non-invertible.",
                                "Error");
                P_output = null;
                return;
            }
        }
        P_output[index] = new ImagePlus(P_images[index].getTitle() + " final",
                                        P_images[index].getStack());
    }
    private void init()
    {
        M_images_size = P_images.length;
        M_processors = new ImageProcessor[M_images_size];
        M_rois = new PointRoi[M_images_size];
        for (int i = 0; i < M_images_size; ++i) {
            M_processors[i] = P_images[i].getProcessor();
            M_rois[i] = (PointRoi)P_images[i].getRoi();
        }
        M_data = new ArrayList<ModelData>(M_images_size * (M_images_size - 1));
        M_biggest_to = new int[M_images_size];
        P_output = new ImagePlus[M_images_size];
    }
    /* This class holds the models used to perform the transformations.
     * They store the model and the indices of the images that it transforms
     * from and to.  This class really does all of the work here.
     */
    private class ModelData {
        private AbstractAffineModel2D<?> M_model;
        private int M_source; // Index of the source image in P_images
        private int M_target; // Index of the target image in P_images
        private int M_source_width;
        private int M_source_height;
        private int M_target_width;
        private int M_target_height;
        // This constructor initializes the model and sets M_source and M_target
        // to the correct values.  M_source and M_target might need to be
        // switched so that it is always scaling up.
        public ModelData(int i, int j) throws NotEnoughDataPointsException,
                                              IllDefinedDataPointsException
        {
            M_source = i;
            M_target = j;
            M_model = get_model(i, j);
            double[] model_array = new double[6];
            M_model.toArray(model_array);
            double determinant = model_array[0] * model_array[3]
                               - model_array[1] * model_array[2];
            if (determinant < 1) {
                M_model = M_model.createInverse();
                int temp = M_source;
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
            M_source_width = P_images[M_source].getWidth();
            M_source_height = P_images[M_source].getHeight();
            M_target_width = P_images[M_target].getWidth();
            M_target_height = P_images[M_target].getHeight();
            ++M_biggest_to[M_target];
        }
        // Acquire the model for transforming the image at index id1 to id2
        private AbstractAffineModel2D<?> get_model(int id1, int id2)
            throws NotEnoughDataPointsException, IllDefinedDataPointsException
        {
            ArrayList<PointMatch> matches = new ArrayList<PointMatch>();
            List<Point> source_points = Util.pointRoiToPoints(M_rois[id1]);
            List<Point> target_points = Util.pointRoiToPoints(M_rois[id2]);
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
        // Perform the transform if this object is transforming to index
        public void try_transform(int index) throws NoninvertibleModelException
        {
            if (M_target == index) {
                int stack_size
                    = Math.min(P_images[M_source].getImageStackSize(),
                               P_images[M_target].getImageStackSize());
                ImageStack result
                    = new ImageStack(P_images[M_target].getWidth(),
                                     P_images[M_target].getHeight());
                for (int i = 0; i < stack_size; ++i) {
                    if (stack_size > 1) {
                        // Note that the status bar will reset multiple times
                        // depending on how many ImagePluses you started with
                        P_status.showStatus(i + 1, stack_size, "Transforming "
                            + P_images[M_source].getTitle());
                    }
                    ImageProcessor source
                        = P_images[M_source].getStack().getProcessor(i + 1);
                    source.setInterpolationMethod(P_interpolation_method);
                    ImageProcessor target
                        = M_processors[M_target].createProcessor(
                            P_images[M_target].getWidth(),
                            P_images[M_target].getHeight()
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
                    result.addSlice(P_images[M_source].getStack()
                                                      .getSliceLabel(i + 1),
                                    target);
                }
                P_output[M_source] = new ImagePlus(P_images[M_source].getTitle()
                    + " final", result);
            }
        }
        /*
         * Puts the pixel from source at (sx, sy) on target at (tx, ty), taking
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
