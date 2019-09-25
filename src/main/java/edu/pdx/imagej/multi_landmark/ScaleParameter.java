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
class ScaleParameter
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
        M_to = addParameter(new ChoiceParameter("Scale to...", choices));
        M_image = addParameter(new ImageParameter("Image to scale to"));
        setVisibilities();
    }
    @Override
    public void readFromDialog()
    {
        super.readFromDialog();
        setVisibilities();
    }
    @Override
    public void readFromPrefs(Class<?> cls, String name)
    {
        super.readFromPrefs(cls, name);
        setVisibilities();
    }
    @Override
    public ScaleOptions getValue()
    {
        ScaleOptions result = new ScaleOptions();
        switch (M_to.getValue()) {
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
        result.specificImage = M_image.getValue();
        return result;
    }

    private void setVisibilities()
    {
        M_image.setNewVisibility(M_to.getValue().equals("Specific Image"));
    }

    private ChoiceParameter M_to;
    private ImageParameter M_image;
}
