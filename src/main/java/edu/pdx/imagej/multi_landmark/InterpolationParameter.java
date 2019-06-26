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

import ij.process.ImageProcessor;

import org.scijava.plugin.Plugin;

import edu.pdx.imagej.dynamic_parameters.BoolParameter;
import edu.pdx.imagej.dynamic_parameters.ChoiceParameter;
import edu.pdx.imagej.dynamic_parameters.DoubleParameter;
import edu.pdx.imagej.dynamic_parameters.DParameter;
import edu.pdx.imagej.dynamic_parameters.HoldingParameter;

@Plugin(type = DParameter.class)
public class InterpolationParameter
             extends HoldingParameter<InterpolationOptions>
{
    public InterpolationParameter()
    {
        super("Interpolation");
    }
    @Override
    public void initialize()
    {
        String[] choices = {"None", "Nearest Neighbor", "Bilinear", "Bicubic"};
        M_type = new ChoiceParameter("Interpolation Type", choices);
        M_stop = new BoolParameter("Suppress interpolation at discontinuities",
                                   true);
        M_threshold = new DoubleParameter(128.0, "Discontinuity threshold");
        M_threshold.set_bounds(Double.MIN_VALUE, Double.MAX_VALUE);
        add_premade_parameter(M_type);
        add_premade_parameter(M_stop);
        add_premade_parameter(M_threshold);
        set_visibilities();
    }
    @Override
    public void read_from_dialog()
    {
        super.read_from_dialog();
        set_visibilities();
    }
    @Override
    public void read_from_prefs(Class<?> cls, String name)
    {
        super.read_from_prefs(cls, name);
        set_visibilities();
    }
    @Override
    public InterpolationOptions get_value()
    {
        InterpolationOptions result = new InterpolationOptions();
        switch (M_type.get_value()) {
            case "None":
                result.type = ImageProcessor.NONE;
                break;
            case "Nearest Neighbor":
                result.type = ImageProcessor.NEAREST_NEIGHBOR;
                break;
            case "Bilinear":
                result.type = ImageProcessor.BILINEAR;
                break;
            case "Bicubic":
                result.type = ImageProcessor.BICUBIC;
                break;
        }
        result.stop_at_discontinuity = M_stop.get_value();
        result.discontinuity_threshold = M_threshold.get_value();
        return result;
    }

    private void set_visibilities()
    {
        if (M_type.get_value().equals("None")) {
            M_stop.set_new_visibility(false);
            M_threshold.set_new_visibility(false);
        }
        else {
            M_stop.set_new_visibility(true);
            M_threshold.set_new_visibility(M_stop.get_value());
        }
    }

    private ChoiceParameter M_type;
    private BoolParameter M_stop;
    private DoubleParameter M_threshold;
}
