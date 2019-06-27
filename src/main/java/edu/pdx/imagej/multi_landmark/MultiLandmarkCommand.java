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

import ij.WindowManager;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.gui.Roi;
import ij.gui.PointRoi;

import net.imagej.ops.OpService;
import org.scijava.Initializable;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import mpicbg.models.*;

import edu.pdx.imagej.dynamic_parameters.ChoiceParameter;

@Plugin(type = Command.class,
        menuPath = "Plugins > Transform > Multi-Image Landmark Correspondences")
public class MultiLandmarkCommand implements Command, Initializable {
    @Parameter private UIService P_ui;
    @Parameter private OpService P_ops;

    @Parameter private InterpolationParameter P_interpolation;
    @Parameter private ScaleParameter         P_scale;
    @Parameter private ChoiceParameter        P_transform_type;
    @Override
    public void initialize()
    {
        P_interpolation = new InterpolationParameter();
        P_scale         = new ScaleParameter();
        String[] choices = {"Translation", "Rigid", "Similarity", "Affine"};
        P_transform_type = new ChoiceParameter("Transform Type", choices,
                                               "Similarity");
    }
    @Override
    public void run()
    {
        int[] ids = WindowManager.getIDList();
        if (ids == null) {
            P_ui.showDialog("There must be at least two images open that have "
                + "point rois", "Error");
            return;
        }
        int final_size = 0;
        for (int id : ids) {
            Roi roi = WindowManager.getImage(id).getRoi();
            if (roi != null) {
                if (roi instanceof PointRoi) {
                    ++final_size;
                }
            }
        }
        if (final_size <= 1) {
            P_ui.showDialog("There must be at least two images open that have "
                + "point rois");
            return;
        }
        ImagePlus[] final_images = new ImagePlus[final_size];
        int i = 0;
        for (int id : ids) {
            ImagePlus image = WindowManager.getImage(id);
            Roi roi = image.getRoi();
            if (roi != null) {
                if (roi instanceof PointRoi) {
                    final_images[i++] = image;
                }
            }
        }

        InterpolationOptions interp = P_interpolation.get_value();
        ScaleOptions scale = P_scale.get_value();
        Class<? extends AbstractAffineModel2D<?>> model_type = null;
        switch (P_transform_type.get_value()) {
            case "Translation":
                model_type = TranslationModel2D.class;
                break;
            case "Rigid":
                model_type = RigidModel2D.class;
                break;
            case "Similarity":
                model_type = SimilarityModel2D.class;
                break;
            case "Affine":
                model_type = AffineModel2D.class;
                break;
        }
        int to = -1;
        switch (scale.to) {
            case Biggest:
                to = -1;
                break;
            case Smallest:
                to = -2;
                break;
            case Specific:
                for (i = 0; i < final_images.length; ++i) {
                    if (final_images[i] == scale.specific_image) {
                        to = i;
                        break;
                    }
                }
                break;
        }
        ImagePlus[] result = (ImagePlus[])P_ops.run(
            MultiLandmark.class,
            final_images,
            interp.type,
            model_type,
            interp.stop_at_discontinuity,
            interp.discontinuity_threshold,
            to);
        if (result == null) return;
        for (ImagePlus imp : result) imp.show();
    }
}
