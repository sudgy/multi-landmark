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

@Plugin(type = Command.class,
        menuPath = "Plugins > Transform > Multi-Image Landmark Correspondences")
public class MultiLandmarkCommand implements Command, Initializable {
    @Parameter private UIService P_ui;
    @Parameter private OpService P_ops;

    @Parameter private InterpolationParameter P_interpolation;
    @Override
    public void initialize()
    {
        P_interpolation = new InterpolationParameter();
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
        ImagePlus[] result = (ImagePlus[])P_ops.run(
            MultiLandmark.class,
            final_images,
            interp.type,
            SimilarityModel2D.class,
            interp.stop_at_discontinuity,
            interp.discontinuity_threshold,
            -1);
        if (result == null) return;
        for (ImagePlus imp : result) imp.show();
    }
}
