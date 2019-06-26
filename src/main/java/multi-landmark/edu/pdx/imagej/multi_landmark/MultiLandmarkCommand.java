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
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import mpicbg.models.*;

@Plugin(type = Command.class, menuPath = "Plugins > Transform > Multi-Image Landmark Correspondences")
public class MultiLandmarkCommand implements Command {
    @Parameter
    private UIService P_ui;
    @Parameter
    private OpService P_ops;

    @Parameter(label = "Interpolation Type", choices={"None", "Nearest Neighbor", "Bilinear", "Bicubic"})
    private String P_interpolation_type = "Bilinear";
    @Parameter(label = "Suppress interpolation at discontinuities")
    private boolean P_stop_interpolation = true;
    @Parameter(label = "Discontinuity threshold", min = "0")
    private float P_discontinuity_threshold = 128;
    @Override
    public void run()
    {
        int[] ids = WindowManager.getIDList();
        if (ids == null) {
            P_ui.showDialog("There must be at least two images open that have point rois", "Error");
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
            P_ui.showDialog("There must be at least two images open that have point rois");
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

        int interpolation_type = ImageProcessor.NONE;
        switch (P_interpolation_type) {
            case "Nearest Neighbor":
                interpolation_type = ImageProcessor.NEAREST_NEIGHBOR;
                break;
            case "Bilinear":
                interpolation_type = ImageProcessor.BILINEAR;
                break;
            case "Bicubic":
                interpolation_type = ImageProcessor.BICUBIC;
                break;
        }
        ImagePlus[] result = (ImagePlus[])P_ops.run(MultiLandmark.class,
                                                    final_images,
                                                    interpolation_type,
                                                    SimilarityModel2D.class,
                                                    P_stop_interpolation,
                                                    P_discontinuity_threshold,
                                                    -1);
        if (result == null) return;
        for (ImagePlus imp : result) imp.show();
    }
}
