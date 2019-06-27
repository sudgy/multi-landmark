Copyright (C) 2019 Portland State University
Multi-Image Landmark Correspondences for ImageJ2 - David Cohoe

Multi-Image Landmark Correspondences is a plugin for ImageJ2 that takes in
several images with point ROIs and tries to align all of them using mpicbg's
Landmark Correspondences plugin.  There is support for several different kinds
of transformations, although not as many as mpicbg's plugin.  For using it to
align phase images, there are also extra options for interpolation to keep the
discontinuities correct.

INSTALLATION

To install the plugin, the update site "DHM Utilities" with the URL
"http://sites.imagej.net/Sudgy/" must be added in the ImageJ updater.  If you
want to modify the plugin, or if you want to install the plugin without
everything else from DHM utilities, compile it with maven and then copy the jar
to the ImageJ plugins folder, removing the old one if you need to.  This plugin
depends on dynamic_parameters, another plugin in DHM utilities, which can be
found at https://github.com/sudgy/dynamic-parameters.  The documentation can be
created using maven's javadoc plugin, and will be created in
target/site/apidocs/.

USE

Run the command "Plugins > Transform > Multi-Image Landmark Correspondences".
Use whatever options you want, and then the plugin will automatically detect
which images have point ROIs and will transform them.  You may also run the
plugin in code using the MultiLandmark Op.

If you have any questions that are not answered here, in the documentation, or
in the source code, please email David Cohoe at dcohoe@pdx.edu.
