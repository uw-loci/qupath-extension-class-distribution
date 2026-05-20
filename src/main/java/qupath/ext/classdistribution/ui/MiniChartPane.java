package qupath.ext.classdistribution.ui;

import javafx.scene.chart.PieChart;
import javafx.scene.layout.StackPane;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassKey;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassSummary;

import java.util.List;
import java.util.Map;

/**
 * A tiny pie chart for the All Images grid tab. Strips down
 * {@link ChartPane} to just the pie -- no labels, no legend, no
 * placeholder, no over/under highlight aura. Slice colors come from each
 * {@link qupath.lib.objects.classes.PathClass}'s color so a thumbnail is
 * readable next to the shared frozen legend at the top of the grid tab.
 *
 * <p>The pie has no fixed pref size; the containing grid item controls
 * width via {@link #resize(double)} (called when the zoom slider moves).
 *
 * @author Mike Nelson
 */
public final class MiniChartPane extends StackPane {

    private final PieChart pieChart;

    public MiniChartPane() {
        pieChart = new PieChart();
        pieChart.setLegendVisible(false);
        pieChart.setLabelsVisible(false);
        pieChart.setAnimated(false);
        pieChart.setMinSize(0, 0);
        pieChart.setStartAngle(90);
        getChildren().add(pieChart);
        setMinSize(0, 0);
    }

    /**
     * Replaces the chart contents. Entries with zero {@code total()} are
     * skipped (a zero-size slice renders nothing useful and would only
     * confuse the visual). Slice order: largest first.
     */
    public void setData(Map<ClassKey, ClassSummary> contributions) {
        pieChart.getData().clear();
        if (contributions == null || contributions.isEmpty()) {
            return;
        }
        List<Map.Entry<ClassKey, ClassSummary>> ordered =
                new java.util.ArrayList<>(contributions.entrySet());
        ordered.sort((a, b) -> Double.compare(b.getValue().total(), a.getValue().total()));

        for (Map.Entry<ClassKey, ClassSummary> e : ordered) {
            ClassKey key = e.getKey();
            ClassSummary summary = e.getValue();
            if (summary == null || summary.total() <= 0.0) {
                continue;
            }
            // The "name" of a slice would normally surface as a label;
            // labels are off here, so use the class label as the
            // accessible / hover string only.
            PieChart.Data slice = new PieChart.Data(key.label(), summary.total());
            pieChart.getData().add(slice);
            // Reuse the same coloring logic the full ChartPane uses so a
            // class has a consistent color across all views.
            ChartPane.applySliceStyle(slice,
                    "-fx-pie-color: " + ChartPane.baseColorFor(key) + ";");
        }
    }

    /**
     * Resize the mini chart to a square of {@code edge} pixels. Called by
     * the grid's zoom slider.
     */
    public void resize(double edge) {
        pieChart.setMinSize(edge, edge);
        pieChart.setPrefSize(edge, edge);
        pieChart.setMaxSize(edge, edge);
        setMinSize(edge, edge);
        setPrefSize(edge, edge);
        setMaxSize(edge, edge);
    }
}
