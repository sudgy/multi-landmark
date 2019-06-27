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

import org.scijava.plugin.Plugin;

import edu.pdx.imagej.dynamic_parameters.ChoiceParameter;
import edu.pdx.imagej.dynamic_parameters.DParameter;
import edu.pdx.imagej.dynamic_parameters.HoldingParameter;
import edu.pdx.imagej.dynamic_parameters.ImageParameter;

@Plugin(type = DParameter.class)
public class ScaleParameter
             extends HoldingParameter<ScaleOptions>
{
    public ScaleParameter()
    {
        super("Scale");
    }
    @Override
    public void initialize()
    {
        String[] choices ={"Biggest Image", "Smallest Image", "Specific Image"};
        M_to = new ChoiceParameter("Scale to...", choices);
        M_image = new ImageParameter("Image to scale to");
        add_premade_parameter(M_to);
        add_premade_parameter(M_image);
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
    public ScaleOptions get_value()
    {
        ScaleOptions result = new ScaleOptions();
        switch (M_to.get_value()) {
            case "Biggest Image":
                result.to = ScaleOptions.To.Biggest;
                break;
            case "Smallest Image":
                result.to = ScaleOptions.To.Smallest;
                break;
            case "Specific Image":
                result.to = ScaleOptions.To.Specific;
                break;
        }
        result.specific_image = M_image.get_value();
        return result;
    }

    private void set_visibilities()
    {
        M_image.set_new_visibility(M_to.get_value().equals("Specific Image"));
    }

    private ChoiceParameter M_to;
    private ImageParameter M_image;
}
