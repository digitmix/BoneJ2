package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import org.bonej.ops.thresholdFraction.SurfaceFraction;
import org.bonej.ops.thresholdFraction.SurfaceFraction.Results;
import org.bonej.ops.thresholdFraction.Thresholds;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.ResultsInserter;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils.SpatialView;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.util.List;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * A wrapper UI class for the SurfaceFraction Op
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Fraction>Surface fraction", headless = true)
public class SurfaceFractionWrapper<T extends RealType<T> & NativeType<T>> extends ContextCommand {
    @Parameter(initializer = "initializeImage")
    private ImgPlus<T> inputImage;

    @Parameter
    private OpService opService;

    @Parameter
    private UIService uiService;

    @Parameter
    private UnitService unitService;

    private boolean calibrationWarned = false;

    @Override
    public void run() {
        final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService, inputImage);
        final String name = inputImage.getName();
        final List<SpatialView<BitType>> views = ViewUtils.createSpatialViews(bitImgPlus);
        final Thresholds thresholds = new Thresholds<>(bitImgPlus, 1, 1);

        for (SpatialView view : views) {
            final String label = name + view.hyperPosition;
            viewFraction(label, view.view, thresholds);
        }
    }

    //region -- Helper methods --

    /** Process surface fraction for one 3D subspace in the n-dimensional image */
    private void viewFraction(String label, RandomAccessibleInterval subSpace, Thresholds thresholds) {
        final Results results = (Results) opService.run(SurfaceFraction.class, subSpace, thresholds);
        final char exponent = ResultUtils.getExponent(inputImage);
        final double elementSize = ElementUtil.calibratedSpatialElementSize(inputImage, unitService);
        final double thresholdVolume = results.thresholdSurfaceVolume * elementSize;
        final double totalVolume = results.totalSurfaceVolume * elementSize;
        final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, exponent);

        if (unitHeader.isEmpty() && !calibrationWarned) {
            uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
            calibrationWarned = true;
        }

        final ResultsInserter resultsInserter = ResultsInserter.getInstance();
        resultsInserter.setMeasurementInFirstFreeRow(label, "Bone volume " + unitHeader, thresholdVolume);
        resultsInserter.setMeasurementInFirstFreeRow(label, "Total volume " + unitHeader, totalVolume);
        resultsInserter.setMeasurementInFirstFreeRow(label, "Volume ratio", results.ratio);
        resultsInserter.updateResults();
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
            cancel(NOT_3D_IMAGE);
        }

        if (!ElementUtil.isColorsBinary(inputImage)) {
            cancel(NOT_BINARY);
        }
    }
    //endregion
}
