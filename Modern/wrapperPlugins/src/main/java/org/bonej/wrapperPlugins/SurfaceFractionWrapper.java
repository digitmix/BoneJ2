
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.util.List;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.geom.geom3d.mesh.DefaultMesh;
import net.imagej.ops.geom.geom3d.mesh.Mesh;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

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

/**
 * First this command creates a surface mesh from both all foreground voxels
 * (bone) and the whole image stack. Then it calculates the surfaces' volumes,
 * their ratio, and shows the results. Results are shown in calibrated units, if
 * possible.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BoneJ>Fraction>Surface fraction", headless = true)
public class SurfaceFractionWrapper<T extends RealType<T> & NativeType<T>>
	extends ContextCommand
{

	/** Header of ratio column in the results table */
	private static final String ratioHeader = "Volume ratio";
	private static UnaryFunctionOp<RandomAccessibleInterval, Mesh> marchingCubes;
	private static UnaryFunctionOp<Mesh, DoubleType> meshVolume;
	private static UnaryFunctionOp<RandomAccessibleInterval, RandomAccessibleInterval> raiCopy;

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	@Parameter
	private OpService opService;

	@Parameter
	private UIService uiService;

	@Parameter
	private UnitService unitService;

	/** Header of the thresholded volume column in the results table */
	private String bVHeader;
	/** Header of the total volume column in the results table */
	private String tVHeader;
	/** The calibrated size of an element in the image */
	private double elementSize;
	private static ResultsInserter resultsInserter = ResultsInserter
		.getInstance();

	@Override
	public void run() {
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService,
			inputImage);
		final List<SpatialView<BitType>> subspaces = ViewUtils.createSpatialViews(
			bitImgPlus);
		matchOps(subspaces.get(0).view);
		prepareResultDisplay();
		final String name = inputImage.getName();
		subspaces.forEach(subspace -> {
			final double[] results = subSpaceFraction(subspace.view);
			final String label = name + subspace.hyperPosition;
			addResults(label, results);
		});
		resultsInserter.updateResults();
	}

	// region -- Helper methods --

	private void matchOps(final RandomAccessibleInterval<BitType> view) {
		raiCopy = Functions.unary(opService, Ops.Copy.RAI.class,
			RandomAccessibleInterval.class, view);
		marchingCubes = Functions.unary(opService,
			Ops.Geometric.MarchingCubes.class, Mesh.class, view);
		// Create a dummy object to make op matching happy
		meshVolume = Functions.unary(opService, Ops.Geometric.Size.class,
			DoubleType.class, new DefaultMesh());
	}

	private void addResults(final String label, final double[] results) {
		resultsInserter.setMeasurementInFirstFreeRow(label, bVHeader, results[0]);
		resultsInserter.setMeasurementInFirstFreeRow(label, tVHeader, results[1]);
		resultsInserter.setMeasurementInFirstFreeRow(label, ratioHeader,
			results[2]);
	}

	private void prepareResultDisplay() {
		final char exponent = ResultUtils.getExponent(inputImage);
		final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService,
			exponent);
		if (unitHeader.isEmpty()) {
			uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
		}
		bVHeader = "Bone volume " + unitHeader;
		tVHeader = "Total volume " + unitHeader;
		elementSize = ElementUtil.calibratedSpatialElementSize(inputImage,
			unitService);
	}

	/** Process surface fraction for one 3D subspace in the n-dimensional image */
	@SuppressWarnings("unchecked")
	private double[] subSpaceFraction(
		RandomAccessibleInterval<BitType> subSpace)
	{
		// Create masks for marching cubes
		final RandomAccessibleInterval totalMask = raiCopy.calculate(subSpace);
		// Because we want to create a surface from the whole image, set everything
		// in the mask to foreground
		((Img<BitType>) totalMask).forEach(BitType::setOne);

		// Create surface meshes and calculate their volume. If the input interval
		// wasn't binary, we'd have to threshold it before these calls.
		final Mesh thresholdMesh = marchingCubes.calculate(subSpace);
		final double rawThresholdVolume = meshVolume.calculate(thresholdMesh).get();
		final Mesh totalMesh = marchingCubes.calculate(totalMask);
		final double rawTotalVolume = meshVolume.calculate(totalMesh).get();

		final double thresholdVolume = rawThresholdVolume * elementSize;
		final double totalVolume = rawTotalVolume * elementSize;
		final double ratio = thresholdVolume / totalVolume;

		return new double[] { thresholdVolume, totalVolume, ratio };
	}

	@SuppressWarnings("unused")
	private void validateImage() {
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
	// endregion
}
