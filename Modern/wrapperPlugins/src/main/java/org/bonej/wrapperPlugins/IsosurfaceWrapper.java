
package org.bonej.wrapperPlugins;

import static org.bonej.utilities.Streamers.spatialAxisStream;
import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.mesh.Mesh;
import net.imagej.mesh.Triangle;
import net.imagej.mesh.Triangles;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.geom.geom3d.DefaultSurfaceArea;
import net.imagej.ops.geom.geom3d.mesh.TriangularFacet;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.space.AnnotatedSpace;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.StringUtils;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

/**
 * A wrapper command to calculate mesh surface area
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Surface area")
public class IsosurfaceWrapper<T extends RealType<T> & NativeType<T>> extends
	ContextCommand
{

	public static final String STL_WRITE_ERROR =
		"Failed to write the following STL files:\n\n";
	public static final String STL_HEADER = StringUtils.padEnd(
		"Binary STL created by BoneJ", 80, '.');
	public static final String BAD_SCALING =
		"Cannot scale result because axis calibrations don't match";

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	/**
	 * The surface area results in a {@link Table}
	 * <p>
	 * Null if there are no results
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;

	@Parameter(label = "Export STL file(s)",
		description = "Create a binary STL file from the surface mesh",
		required = false)
	private boolean exportSTL;

	@Parameter(label = "Help", description = "Open help web page",
		callback = "openHelpPage")
	private Button helpButton;

	@Parameter
	private LogService logService;

	@Parameter
	private OpService ops;

	@Parameter
	private PlatformService platformService;

	@Parameter
	private UIService uiService;

	@Parameter
	private UnitService unitService;

    @Parameter
    private StatusService statusService;

	private String path = "";
	private String extension = "";
	private UnaryFunctionOp<RandomAccessibleInterval, Mesh> marchingCubesOp;
	private double areaScale;
	private String unitHeader = "";

	@Override
	public void run() {
	    statusService.showStatus("Surface area: initialising");
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(ops,
			inputImage);
		final List<Subspace<BitType>> subspaces = HyperstackUtils.split3DSubspaces(
			bitImgPlus).collect(Collectors.toList());
		matchOps(subspaces.get(0).interval);
		prepareResults();
		statusService.showStatus("Surface area: creating meshes");
		final Map<String, Mesh> meshes = processViews(subspaces);
		if (exportSTL) {
			getFileName();
            statusService.showStatus("Surface area: saving files");
			saveMeshes(meshes);
		}
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	private void prepareResults() {
		unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, '²');
		if (unitHeader.isEmpty()) {
			uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
		}

		if (!isAxesMatchingSpatialCalibration(inputImage)) {
			uiService.showDialog(BAD_SCALING, WARNING_MESSAGE);
			areaScale = 1.0;
		}
		else {
			final double scale = inputImage.axis(0).averageScale(0.0, 1.0);
			areaScale = scale * scale;
		}
	}

	// -- Helper methods --
	private static void writeSTLFacet(ByteBuffer buffer, TriangularFacet facet) {
		writeSTLVector(buffer, facet.getNormal());
		writeSTLVector(buffer, facet.getP0());
		writeSTLVector(buffer, facet.getP1());
		writeSTLVector(buffer, facet.getP2());
		buffer.putShort((short) 0); // Attribute byte count
	}

	private static void writeSTLVector(ByteBuffer buffer, Vector3D v) {
		buffer.putFloat((float) v.getX());
		buffer.putFloat((float) v.getY());
		buffer.putFloat((float) v.getZ());
	}

	private void matchOps(final RandomAccessibleInterval<BitType> interval) {
		marchingCubesOp = Functions.unary(ops, Ops.Geometric.MarchingCubes.class,
			Mesh.class, interval);
	}

	private Map<String, Mesh> processViews(List<Subspace<BitType>> subspaces) {
		final String name = inputImage.getName();
		final Map<String, Mesh> meshes = new HashMap<>();
		for (Subspace<BitType> subspace : subspaces) {
			final Mesh mesh = marchingCubesOp.calculate(subspace.interval);
			final DefaultSurfaceArea surfaceArea = new DefaultSurfaceArea();
			DoubleType area = new DoubleType();
			surfaceArea.compute(mesh,area);
			final String suffix = subspace.toString();
			final String label = suffix.isEmpty() ? name : name + " " + suffix;
			addResult(label, area.get());
			meshes.put(subspace.toString(), mesh);
		}
		return meshes;
	}

	private void saveMeshes(final Map<String, Mesh> meshes) {
		final Map<String, String> savingErrors = new HashMap<>();
		meshes.forEach((key, subspaceMesh) -> {
            final String subspaceId = key.replace(' ', '_');
            final String filePath = path + "_" + subspaceId + extension;
            try {
                writeBinarySTLFile(filePath, subspaceMesh);
            } catch (IOException e) {
                savingErrors.put(filePath, e.getMessage());
            }
        });
		if (!savingErrors.isEmpty()) {
			showSavingErrorsDialog(savingErrors);
		}
	}

	private void showSavingErrorsDialog(final Map<String, String> savingErrors) {
		StringBuilder msgBuilder = new StringBuilder(STL_WRITE_ERROR);
		savingErrors.forEach((k, v) -> msgBuilder.append(k).append(": ").append(v));
		uiService.showDialog(msgBuilder.toString(), ERROR_MESSAGE);
	}

	private void getFileName() {
		path = choosePath();
		if (path == null) {
			return;
		}

		final String fileName = path.substring(path.lastIndexOf(File.separator) +
			1);
		final int dot = fileName.lastIndexOf(".");
		if (dot >= 0) {
			extension = fileName.substring(dot);
			// TODO Verify extension if not .stl, when DialogPrompt YES/NO options
			// work correctly
			path = stripFileExtension(path);
		}
		else {
			extension = ".stl";
		}
	}

	private String choosePath() {
		String initialName = stripFileExtension(inputImage.getName());

		// The file dialog won't allow empty filenames, and it prompts when file
		// already exists
		File file = uiService.chooseFile(new File(initialName),
			FileWidget.SAVE_STYLE);
		if (file == null) {
			// User pressed cancel on file dialog
			return null;
		}

		return file.getAbsolutePath();
	}

	// TODO make into a utility method
	private static String stripFileExtension(String path) {
		final int dot = path.lastIndexOf('.');

		return dot == -1 ? path : path.substring(0, dot);
	}

	private void addResult(final String label, final double area) {
		SharedTable.add(label, "Surface area " + unitHeader, area * areaScale);
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

	@SuppressWarnings("unused")
	private void openHelpPage() {
		Help.openHelpPage("http://bonej.org/isosurface", platformService, uiService,
			logService);
	}

	// -- Utility methods --

	/**
	 * Writes the surface mesh as a binary, little endian STL file
	 * <p>
	 * NB: Public and static for testing purposes
	 * </p>
	 *
	 * @param path The absolute path to the save location of the STL file
	 * @param mesh A mesh consisting of triangular facets
	 * @throws NullPointerException if mesh is null
	 * @throws IllegalArgumentException if path is null or empty, or mesh doesn't
	 *           have triangular facets
	 * @throws IOException if there's an error while writing the file
	 */
	// TODO: Remove when imagej-mesh / ThreeDViewer supports STL
	public static void writeBinarySTLFile(final String path, final Mesh mesh)
		throws IllegalArgumentException, IOException, NullPointerException
	{
		if (mesh == null) {
			throw new NullPointerException("Mesh cannot be null");
		}

		if (StringUtils.isNullOrEmpty(path)) {
			throw new IllegalArgumentException("Filename cannot be null or empty");
		}
		if (mesh.triangles().size()==0) {
			throw new IllegalArgumentException(
				"Cannot write STL file: invalid surface mesh");
		}

		final Triangles triangles =  mesh.triangles();
		final long numTriangles = triangles.size();
		try (FileOutputStream writer = new FileOutputStream(path)) {
			final byte[] header = STL_HEADER.getBytes();
			writer.write(header);
			final byte[] facetBytes = ByteBuffer.allocate(4).order(
				ByteOrder.LITTLE_ENDIAN).putLong(numTriangles).array();
			writer.write(facetBytes);
			final ByteBuffer buffer = ByteBuffer.allocate(50);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			for (Triangle facet : triangles) {
				final TriangularFacet triangularFacet = (TriangularFacet) facet;
				writeSTLFacet(buffer, triangularFacet);
				writer.write(buffer.array());
				buffer.clear();
			}
		}
	}

	/**
	 * Check if all the spatial axes have a matching calibration, e.g. same unit,
	 * same scaling.
	 * <p>
	 * NB: Public and static for testing purposes.
	 * </p>
	 * 
	 * @param space an N-dimensional space.
	 * @param <T> type of the space
	 * @return true if all spatial axes have matching calibration. Also returns
	 *         true if none of them have a unit
	 */
	// TODO make into a utility method or remove if mesh area considers
	// calibration in the future
	public static <T extends AnnotatedSpace<CalibratedAxis>> boolean
		isAxesMatchingSpatialCalibration(T space)
	{
		final boolean noUnits = spatialAxisStream(space).map(CalibratedAxis::unit)
			.allMatch(StringUtils::isNullOrEmpty);
		final boolean matchingUnit = spatialAxisStream(space).map(
			CalibratedAxis::unit).distinct().count() == 1;
		final boolean matchingScale = spatialAxisStream(space).map(a -> a
			.averageScale(0, 1)).distinct().count() == 1;

		return (matchingUnit || noUnits) && matchingScale;
	}
}
