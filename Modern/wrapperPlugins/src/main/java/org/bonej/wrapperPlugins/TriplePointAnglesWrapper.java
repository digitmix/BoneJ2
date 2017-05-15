
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.GOT_SKELETONISED;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.CommonMessages.NO_SKELETONS;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.util.List;

import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;

import org.bonej.ops.TriplePointAngles;
import org.bonej.ops.TriplePointAngles.TriplePoint;
import org.bonej.utilities.ImagePlusUtil;
import org.bonej.utilities.SharedTable;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import ij.ImagePlus;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * A wrapper UI class for the TriplePointAngles Op
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Triple Point Angles")
public class TriplePointAnglesWrapper extends ContextCommand {

	static {
		// NB: Needed if you mix-and-match IJ1 and IJ2 classes.
		// And even then: do not use IJ1 classes in the API!
		LegacyInjector.preinit();
	}

	/**
	 * @implNote Use ImagePlus because of conversion issues of composite images
	 */
	@Parameter(validater = "validateImage")
	private ImagePlus inputImage;

	@Parameter(label = "Measurement mode",
		description = "Where to measure the triple point angles",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = {
			"Opposite vertex", "Edge point" })
	private String measurementMode;

	@Parameter(label = "Edge point #", min = "0", max = "100", stepSize = "1",
		description = "Ordinal of the edge point used for measuring",
		style = NumberWidget.SLIDER_STYLE, persist = false)
	private int edgePoint = 0;

	/**
	 * The triple point angle results in a {@link Table}
	 * <p>
	 * Null if there are no results
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;

	@Parameter(label = "Help", description = "Open help web page",
		callback = "openHelpPage")
	private Button helpButton;

	@Parameter
	private LogService logService;

	@Parameter
	private OpService opService;

	@Parameter
	private PlatformService platformService;

	@Parameter
	private UIService uiService;

	@Override
	public void run() {
		// Skeletonise input image
		final ImagePlus skeleton = inputImage.duplicate();
		final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);

		final int iterations = skeletoniser.getThinningIterations();
		if (iterations > 1) {
			skeleton.setTitle("Skeleton of " + inputImage.getTitle());
			skeleton.show();
			uiService.showDialog(GOT_SKELETONISED, INFORMATION_MESSAGE);
		}

		// Analyse skeleton
		final AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();
		analyser.setup("", skeleton);
		analyser.run();
		final Graph[] graphs = analyser.getGraphs();

		if (graphs == null || graphs.length == 0) {
			uiService.showDialog("Can't calculate triple point angles: " +
				NO_SKELETONS, ERROR_MESSAGE);
			return;
		}

		final List<List<TriplePoint>> results = callTPAOp(graphs);

		if (TriplePointAngles.hasCircularEdges(graphs)) {
			uiService.showDialog("The skeletons contained circular edges." +
				"Please try running the plugin with \"Measurement mode\" set to \"Edge point\".",
				WARNING_MESSAGE);
		}

		showResults(results);
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	// region -- Helper methods --
	private List<List<TriplePoint>> callTPAOp(final Graph[] graphs) {
		final int measurementPoint = measurementMode.equals("Opposite vertex")
			? TriplePointAngles.VERTEX_TO_VERTEX : edgePoint;
		return (List<List<TriplePoint>>) opService.run(TriplePointAngles.class,
			graphs, measurementPoint);
	}

	private void showResults(final List<List<TriplePoint>> graphList) {
		String label = inputImage.getTitle();

		for (List<TriplePoint> triplePointList : graphList) {
			for (TriplePoint triplePoint : triplePointList) {
				SharedTable.add(label, "Skeleton #", triplePoint.getGraphNumber());
				SharedTable.add(label, "Triple point #", triplePoint
					.getTriplePointNumber());
				final List<Double> angles = triplePoint.getAngles();
				SharedTable.add(label, "α (rad)", angles.get(0));
				SharedTable.add(label, "β (rad)", angles.get(1));
				SharedTable.add(label, "γ (rad)", angles.get(2));
			}
		}
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}

		if (inputImage.getBitDepth() != 8 || !ImagePlusUtil.isBinaryColour(
			inputImage))
		{
			cancel(NOT_8_BIT_BINARY_IMAGE);
			return;
		}
	}

	@SuppressWarnings("unused")
	private void openHelpPage() {
		Help.openHelpPage("http://bonej.org/triplepointangles", platformService,
			uiService, logService);
	}
	// endregion
}
