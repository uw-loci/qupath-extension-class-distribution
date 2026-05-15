package qupath.ext.classdistribution.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassKey;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassSummary;
import qupath.ext.classdistribution.core.HighlightEvaluator.Evaluation;
import qupath.ext.classdistribution.core.HighlightEvaluator.Highlight;
import qupath.ext.classdistribution.core.HighlightEvaluator.SliceEval;
import qupath.lib.objects.classes.PathClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * A {@link VBox} containing the {@link PieChart} and a custom legend
 * {@link FlowPane}. Pure JavaFX -- no business logic. Owns no listener
 * lifecycle of its own.
 *
 * <p>Adapted from {@code qupath-extension-dl-pixel-classifier/.../TrainingDialog.java}
 * lines 4302-4309 (PieChart construction) and 5449-5496
 * ({@code refreshPieChart()} per-slice CSS pattern). The post-layout
 * {@code data.nodeProperty()} listener is the non-obvious part: a
 * {@link PieChart.Data} node is null until the chart lays out, so a
 * one-shot listener applies the colour CSS when the slice's chart node
 * first appears. Re-applied on every refresh so the colours survive a
 * ColorPicker change without needing a full chart rebuild.
 *
 * <p>Behavioural notes:
 * <ul>
 *   <li>Slice-label suppression: when the number of slices exceeds
 *       {@link #SLICE_LABEL_SUPPRESS_THRESHOLD}, {@code labelsVisible} is
 *       forced off regardless of the user's preference -- the legend
 *       takes over.</li>
 *   <li>Empty / placeholder messages are rendered as a centred {@link Label}
 *       overlay; the chart node itself is hidden in those states so the
 *       message is the only thing the user sees.</li>
 *   <li>Per-slice tooltips are installed on every refresh so they pick up
 *       the latest counts and percentages.</li>
 * </ul>
 *
 * @author Mike Nelson
 */
public final class ChartPane extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(ChartPane.class);

    /**
     * When the slice count exceeds this value we suppress on-chart labels
     * regardless of the user preference. The legend below the chart still
     * shows everything. Picked at 10 per UI/UX draft section 2 (control K).
     */
    public static final int SLICE_LABEL_SUPPRESS_THRESHOLD = 10;

    /**
     * Default slice colour used when the {@link PathClass} carries no colour
     * (rare in practice; QuPath assigns colours by default).
     */
    private static final String DEFAULT_SLICE_COLOR = "rgb(170,170,170)";

    /** Grey used for the Unclassified bucket. ASCII hex literal. */
    private static final String UNCLASSIFIED_SLICE_COLOR = "rgb(136,136,136)";

    private final ResourceBundle resources;
    private final PieChart pieChart;
    private final FlowPane legend;
    private final Label placeholder;
    private final StackPane chartStack;

    public ChartPane(ResourceBundle resources) {
        super(8);
        this.resources = resources;
        setPadding(new Insets(8, 0, 8, 0));
        setAlignment(Pos.TOP_CENTER);

        pieChart = new PieChart();
        pieChart.setLegendVisible(false);  // we render our own legend below.
        pieChart.setLabelsVisible(true);
        pieChart.setLabelLineLength(10);
        pieChart.setMinSize(180, 180);
        pieChart.setPrefSize(360, 360);
        Tooltip.install(pieChart, new Tooltip(resources.getString("tooltip.chart")));

        placeholder = new Label("");
        placeholder.setWrapText(true);
        placeholder.setStyle("-fx-text-fill: -fx-text-base-color; -fx-opacity: 0.7;");
        placeholder.setVisible(false);
        placeholder.setManaged(false);

        chartStack = new StackPane(pieChart, placeholder);
        chartStack.setMinSize(180, 180);
        VBox.setVgrow(chartStack, Priority.ALWAYS);

        legend = new FlowPane(8, 4);
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(4, 8, 4, 8));
        Tooltip.install(legend, new Tooltip(resources.getString("tooltip.legend")));

        getChildren().addAll(chartStack, legend);
    }

    /**
     * Applies a new dataset to the chart.
     *
     * @param contributions per-class totals (driving slice size).
     * @param evaluation per-slice highlight verdicts.
     * @param showLabelsPref whether the user wants slice labels on at all.
     * @param overColorHex CSS-ready colour string (e.g. {@code "#00BCD4"}).
     * @param underColorHex CSS-ready colour string (e.g. {@code "#E53935"}).
     */
    public void refresh(Map<ClassKey, ClassSummary> contributions,
                        Evaluation evaluation,
                        boolean showLabelsPref,
                        String overColorHex,
                        String underColorHex) {
        Objects.requireNonNull(contributions, "contributions");
        Objects.requireNonNull(evaluation, "evaluation");

        pieChart.getData().clear();
        legend.getChildren().clear();

        if (contributions.isEmpty() || evaluation.total() <= 0.0) {
            // Caller handles empty messaging; default to a generic empty.
            showPlaceholder(resources.getString("empty.noAnnotations"));
            return;
        }

        hidePlaceholder();

        // Apply slice-label suppression policy.
        boolean labelsOn = showLabelsPref && contributions.size() <= SLICE_LABEL_SUPPRESS_THRESHOLD;
        pieChart.setLabelsVisible(labelsOn);

        // Build slice list in insertion order so the chart and legend agree.
        List<Map.Entry<ClassKey, ClassSummary>> ordered = new java.util.ArrayList<>(contributions.entrySet());
        // Stable sort: largest slice first (cosmetic; keeps the chart tidy).
        ordered.sort((a, b) -> Double.compare(b.getValue().total(), a.getValue().total()));

        for (Map.Entry<ClassKey, ClassSummary> e : ordered) {
            ClassKey key = e.getKey();
            ClassSummary summary = e.getValue();
            SliceEval eval = evaluation.perSlice().get(key);
            if (eval == null) {
                eval = new SliceEval(0.0, 0.0, Highlight.NORMAL);
            }
            double pct = eval.share() * 100.0;

            String labelText = String.format("%s (%.1f%%)", key.label(), pct);
            PieChart.Data slice = new PieChart.Data(labelText, summary.total());
            pieChart.getData().add(slice);

            String style = "-fx-pie-color: " + colorForSlice(key, eval.highlight(), overColorHex, underColorHex) + ";";
            applySliceStyle(slice, style);

            String hover = formatHoverText(key, summary, pct);
            installSliceTooltip(slice, hover);

            // Add a swatch + label to the custom legend.
            legend.getChildren().add(buildLegendSwatch(key, eval, summary, pct,
                    overColorHex, underColorHex));
        }
    }

    /**
     * Shows a centred placeholder message in place of the chart.
     */
    public void showPlaceholder(String message) {
        pieChart.getData().clear();
        legend.getChildren().clear();
        placeholder.setText(message == null ? "" : message);
        placeholder.setVisible(true);
        placeholder.setManaged(true);
        pieChart.setVisible(false);
        pieChart.setManaged(false);
    }

    /**
     * Hides the placeholder and re-shows the chart node.
     */
    public void hidePlaceholder() {
        placeholder.setVisible(false);
        placeholder.setManaged(false);
        pieChart.setVisible(true);
        pieChart.setManaged(true);
    }

    /**
     * Disables / re-enables the chart visually for "polling in progress"
     * states. Does not actually disable JavaFX events on its sub-nodes;
     * the caller is responsible for not feeding in stale data while a
     * poll runs.
     */
    public void setBusy(boolean busy) {
        pieChart.setOpacity(busy ? 0.5 : 1.0);
        pieChart.setMouseTransparent(busy);
    }

    private void applySliceStyle(PieChart.Data slice, String style) {
        // Adapted from qupath-extension-dl-pixel-classifier
        // TrainingDialog.java:5479-5495. The slice node is null until
        // first layout; install both an immediate setStyle and a
        // nodeProperty listener so the colour applies whether or not
        // layout has happened.
        if (slice.getNode() != null) {
            slice.getNode().setStyle(style);
        }
        slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle(style);
            }
        });
    }

    private void installSliceTooltip(PieChart.Data slice, String text) {
        Tooltip tip = new Tooltip(text);
        if (slice.getNode() != null) {
            Tooltip.install(slice.getNode(), tip);
        }
        slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Tooltip.install(newNode, tip);
            }
        });
    }

    private String formatHoverText(ClassKey key, ClassSummary summary, double pct) {
        if (key.isUnclassified()) {
            return resources.getString("tooltip.slice.unclassified");
        }
        // Substitute via String.format using the resource-bundle pattern.
        // Pattern: "%1$s: %2$d objects, %3$.1f px (%4$.1f%%)" -- ASCII only.
        try {
            return String.format(resources.getString("slice.hoverFormat"),
                    key.label(),
                    summary.count(),
                    summary.total(),
                    pct);
        } catch (Exception e) {
            // Fallback if the pattern is malformed in a bundle override.
            return String.format("%s: %d objects, %.1f px (%.1f%%)",
                    key.label(), summary.count(), summary.total(), pct);
        }
    }

    /**
     * Returns a CSS-ready {@code rgb(...)} string for one slice given the
     * highlight verdict. OVER / UNDER take the user's chosen colours;
     * NORMAL takes the QuPath {@link PathClass} colour, defaulting to grey
     * for the Unclassified bucket.
     */
    private static String colorForSlice(ClassKey key, Highlight highlight,
                                        String overColorHex, String underColorHex) {
        switch (highlight) {
            case OVER:
                return cssRgbFromHex(overColorHex, "rgb(0,188,212)");
            case UNDER:
                return cssRgbFromHex(underColorHex, "rgb(229,57,53)");
            case NORMAL:
            default:
                return baseColorFor(key);
        }
    }

    /**
     * Base (un-highlighted) colour for one slice. Drawn from the
     * {@link PathClass} colour stored in QuPath; grey for the
     * Unclassified bucket per the design.
     */
    private static String baseColorFor(ClassKey key) {
        if (key == null || key.isUnclassified()) {
            return UNCLASSIFIED_SLICE_COLOR;
        }
        PathClass pc = key.pathClass();
        if (pc == null) {
            return UNCLASSIFIED_SLICE_COLOR;
        }
        Integer argb = pc.getColor();
        if (argb == null) {
            return DEFAULT_SLICE_COLOR;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return "rgb(" + r + "," + g + "," + b + ")";
    }

    /**
     * Converts a hex colour string ({@code "#RRGGBB"}) to a CSS
     * {@code rgb(r,g,b)} string. Falls back to {@code fallback} if the
     * hex is not parseable. Intentionally lenient about a missing
     * leading {@code #}.
     */
    static String cssRgbFromHex(String hex, String fallback) {
        if (hex == null) {
            return fallback;
        }
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() != 6) {
            return fallback;
        }
        try {
            int r = Integer.parseInt(s.substring(0, 2), 16);
            int g = Integer.parseInt(s.substring(2, 4), 16);
            int b = Integer.parseInt(s.substring(4, 6), 16);
            return "rgb(" + r + "," + g + "," + b + ")";
        } catch (NumberFormatException nfe) {
            return fallback;
        }
    }

    /**
     * Builds one entry for the custom FlowPane legend: a coloured swatch +
     * the class label + the percent. Tooltip on hover reuses the slice
     * tooltip for parity with the chart.
     */
    private HBox buildLegendSwatch(ClassKey key, SliceEval eval, ClassSummary summary,
                                   double pct, String overColorHex, String underColorHex) {
        Rectangle swatch = new Rectangle(12, 12);
        String css = colorForSlice(key, eval.highlight(), overColorHex, underColorHex);
        swatch.setFill(parseCssRgb(css));
        swatch.setStroke(Color.color(0.4, 0.4, 0.4, 0.6));

        Label text = new Label(String.format("%s %.1f%%", key.label(), pct));
        Tooltip.install(text, new Tooltip(formatHoverText(key, summary, pct)));

        HBox row = new HBox(4, swatch, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Parses {@code "rgb(r,g,b)"} into a JavaFX {@link Color}. Returns
     * {@link Color#GRAY} on failure.
     */
    private static Color parseCssRgb(String css) {
        if (css == null) {
            return Color.GRAY;
        }
        try {
            String inner = css.replace("rgb(", "").replace(")", "").trim();
            String[] parts = inner.split(",");
            if (parts.length != 3) {
                return Color.GRAY;
            }
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            return Color.rgb(clip(r), clip(g), clip(b));
        } catch (Exception e) {
            return Color.GRAY;
        }
    }

    private static int clip(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // Diagnostic helper for tests; not used in production code paths.
    Map<String, String> debugSliceLabels() {
        Map<String, String> out = new LinkedHashMap<>();
        for (PieChart.Data d : pieChart.getData()) {
            out.put(d.getName(), d.getNode() == null ? "(no node)" : d.getNode().getStyle());
        }
        return out;
    }
}
