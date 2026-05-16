package qupath.ext.classdistribution.ui;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import qupath.ext.classdistribution.preferences.CDPreferences;

import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Collapsible {@link TitledPane} hosting the five Advanced controls:
 * polyline-width Spinner, threshold Slider, over- / under- ColorPickers,
 * and a slice-labels CheckBox.
 *
 * <p>Owns its own preference bindings (read-on-construct, write-on-change)
 * via {@link CDPreferences}. Exposes typed listener hooks so the parent
 * {@link ClassDistributionDialog} can re-render or re-style the chart on
 * any control change without re-implementing the binding plumbing.
 *
 * <p>Reference lift: structurally modelled on the {@code SectionBuilder}
 * pattern from
 * {@code qupath-extension-image-export-toolkit/.../ui/SectionBuilder.java}
 * (collapsible TitledPane factory). We do not depend on that extension;
 * the section is built inline.
 *
 * @author Mike Nelson
 */
public final class AdvancedSection extends TitledPane {

    private static final int POLYLINE_MIN = 1;
    private static final int POLYLINE_MAX = 50;
    private static final double RATIO_MIN = 1.5;
    private static final double RATIO_MAX = 10.0;

    private final Spinner<Integer> polylineWidthSpinner;
    private final Slider thresholdSlider;
    private final Label thresholdReadout;
    private final ColorPicker overPicker;
    private final ColorPicker underPicker;
    private final CheckBox showLabelsCheck;
    private final CheckBox sideBySideCheck;

    /**
     * Builds the section with all controls visible. The supplied
     * {@code resources} bundle must contain every key from
     * {@code strings.properties} that the section uses (see source for the
     * exact list).
     */
    public AdvancedSection(ResourceBundle resources) {
        this(resources, true);
    }

    /**
     * Builds the section. When {@code showPolylineWidth} is false the
     * polyline-width row is omitted -- useful for the detection-training
     * dialog where polyline ROIs are not a labeling mechanism.
     */
    public AdvancedSection(ResourceBundle resources, boolean showPolylineWidth) {
        super(resources.getString("section.advanced"), null);
        setCollapsible(true);
        setExpanded(CDPreferences.isAdvancedSectionExpanded());
        setTooltip(new Tooltip(resources.getString("tooltip.advanced")));

        // Polyline width spinner
        polylineWidthSpinner = new Spinner<>();
        polylineWidthSpinner.setEditable(true);
        polylineWidthSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        POLYLINE_MIN, POLYLINE_MAX, clampPolyline(CDPreferences.getPolylineWidthPx())));
        polylineWidthSpinner.setPrefWidth(80);
        polylineWidthSpinner.setTooltip(new Tooltip(resources.getString("tooltip.polylineWidth")));

        // Highlight-ratio slider + numeric readout.
        thresholdSlider = new Slider(RATIO_MIN, RATIO_MAX,
                clampRatio(CDPreferences.getHighlightRatio()));
        thresholdSlider.setShowTickMarks(true);
        thresholdSlider.setShowTickLabels(true);
        thresholdSlider.setMajorTickUnit(2.0);
        thresholdSlider.setMinorTickCount(3);
        thresholdSlider.setBlockIncrement(0.5);
        thresholdSlider.setSnapToTicks(false);
        thresholdSlider.setTooltip(new Tooltip(resources.getString("tooltip.threshold")));
        thresholdReadout = new Label(formatRatio(thresholdSlider.getValue()));
        thresholdReadout.setMinWidth(46);

        // Colour pickers (defaults are Okabe-Ito blue / vermilion for
        // colourblind safety; see CDPreferences for rationale).
        overPicker = new ColorPicker(parseHex(CDPreferences.getOverColorHex(), Color.web("#0072B2")));
        overPicker.setTooltip(new Tooltip(resources.getString("tooltip.overColor")));
        overPicker.setPrefWidth(96);
        underPicker = new ColorPicker(parseHex(CDPreferences.getUnderColorHex(), Color.web("#D55E00")));
        underPicker.setTooltip(new Tooltip(resources.getString("tooltip.underColor")));
        underPicker.setPrefWidth(96);

        // Show-labels checkbox
        showLabelsCheck = new CheckBox(resources.getString("label.showSliceLabels"));
        showLabelsCheck.setSelected(CDPreferences.isShowSliceLabels());
        showLabelsCheck.setTooltip(new Tooltip(resources.getString("tooltip.showSliceLabels")));
        Label showLabelsHint = new Label("(" + resources.getString("label.showSliceLabelsHint") + ")");
        showLabelsHint.setStyle("-fx-text-fill: -fx-text-base-color; -fx-opacity: 0.7; -fx-font-size: 0.9em;");

        // Side-by-side toggle: swaps the dialog's tabbed display for a
        // horizontal split. Useful on wide screens; the user will need to
        // resize the dialog to fit both charts comfortably.
        sideBySideCheck = new CheckBox(resources.getString("label.sideBySide"));
        sideBySideCheck.setSelected(CDPreferences.isSideBySide());
        sideBySideCheck.setTooltip(new Tooltip(resources.getString("tooltip.sideBySide")));

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 12, 10, 12));

        // Row 0: polyline width (omitted for the detection-training dialog).
        if (showPolylineWidth) {
            Label polylineLabel = new Label(resources.getString("label.polylineWidth"));
            polylineLabel.setTooltip(new Tooltip(resources.getString("tooltip.polylineWidth")));
            Label polylineSuffix = new Label(resources.getString("label.polylineWidthSuffix"));
            HBox polylineRow = new HBox(6, polylineLabel, polylineWidthSpinner, polylineSuffix);
            polylineRow.setAlignment(Pos.CENTER_LEFT);
            grid.add(polylineRow, 0, 0, 4, 1);
        }

        // Row 1: threshold slider
        Label thresholdLabel = new Label(resources.getString("label.threshold"));
        thresholdLabel.setTooltip(new Tooltip(resources.getString("tooltip.threshold")));
        Label thresholdSuffix = new Label(resources.getString("label.thresholdSuffix"));
        HBox thresholdRow = new HBox(6, thresholdLabel, thresholdSlider, thresholdReadout, thresholdSuffix);
        thresholdRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(thresholdSlider, javafx.scene.layout.Priority.ALWAYS);
        grid.add(thresholdRow, 0, 1, 4, 1);

        // Row 2: colour pickers
        Label overLabel = new Label(resources.getString("label.overColor"));
        overLabel.setTooltip(new Tooltip(resources.getString("tooltip.overColor")));
        Label underLabel = new Label(resources.getString("label.underColor"));
        underLabel.setTooltip(new Tooltip(resources.getString("tooltip.underColor")));
        HBox colorRow = new HBox(12,
                new HBox(6, overLabel, overPicker),
                new HBox(6, underLabel, underPicker));
        colorRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(colorRow, 0, 2, 4, 1);

        // Row 3: show-labels checkbox + side-by-side checkbox
        HBox labelsRow = new HBox(6, showLabelsCheck, showLabelsHint);
        labelsRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(labelsRow, 0, 3, 4, 1);

        HBox sideBySideRow = new HBox(6, sideBySideCheck);
        sideBySideRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(sideBySideRow, 0, 4, 4, 1);

        setContent(grid);

        // Persistence: write through to CDPreferences on every change.
        polylineWidthSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                CDPreferences.setPolylineWidthPx(clampPolyline(newV));
            }
        });
        thresholdSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                double clamped = clampRatio(newV.doubleValue());
                CDPreferences.setHighlightRatio(clamped);
                thresholdReadout.setText(formatRatio(clamped));
            }
        });
        overPicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                CDPreferences.setOverColorHex(toHex(newV));
            }
        });
        underPicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                CDPreferences.setUnderColorHex(toHex(newV));
            }
        });
        showLabelsCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                CDPreferences.setShowSliceLabels(newV);
            }
        });
        sideBySideCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                CDPreferences.setSideBySide(newV);
            }
        });
        expandedProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                CDPreferences.setAdvancedSectionExpanded(newV);
            }
        });
    }

    // ----------------------------------------------------------------
    // Listener attach helpers (the dialog wires these to its renderer)
    // ----------------------------------------------------------------

    /**
     * Adds a listener that fires whenever the polyline width changes.
     * The listener receives the new (already-clamped) integer value.
     */
    public void onPolylineWidthChanged(IntConsumer listener) {
        polylineWidthSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                listener.accept(clampPolyline(newV));
            }
        });
    }

    /**
     * Adds a listener that fires whenever the highlight-ratio slider changes.
     */
    public void onRatioChanged(Consumer<Double> listener) {
        thresholdSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                listener.accept(clampRatio(newV.doubleValue()));
            }
        });
    }

    /**
     * Adds a listener that fires whenever either colour picker changes.
     * Listener gets a two-element string array: [over hex, under hex].
     */
    public void onColorsChanged(Consumer<String[]> listener) {
        ChangeListener<Color> shared = (obs, oldV, newV) -> listener.accept(new String[] {
                toHex(overPicker.getValue()),
                toHex(underPicker.getValue())
        });
        overPicker.valueProperty().addListener(shared);
        underPicker.valueProperty().addListener(shared);
    }

    /**
     * Adds a listener that fires whenever the slice-labels checkbox toggles.
     */
    public void onShowLabelsChanged(Consumer<Boolean> listener) {
        showLabelsCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                listener.accept(newV);
            }
        });
    }

    /**
     * Adds a listener that fires whenever the side-by-side checkbox toggles.
     */
    public void onSideBySideChanged(Consumer<Boolean> listener) {
        sideBySideCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                listener.accept(newV);
            }
        });
    }

    public boolean isSideBySide() {
        return sideBySideCheck.isSelected();
    }

    public int getPolylineWidthPx() {
        Integer v = polylineWidthSpinner.getValue();
        return v == null ? POLYLINE_MIN : clampPolyline(v);
    }

    public double getHighlightRatio() {
        return clampRatio(thresholdSlider.getValue());
    }

    public boolean isShowSliceLabels() {
        return showLabelsCheck.isSelected();
    }

    public String getOverColorHex() {
        return toHex(overPicker.getValue());
    }

    public String getUnderColorHex() {
        return toHex(underPicker.getValue());
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static int clampPolyline(int v) {
        return Math.max(POLYLINE_MIN, Math.min(POLYLINE_MAX, v));
    }

    private static double clampRatio(double v) {
        if (Double.isNaN(v)) {
            return RATIO_MIN;
        }
        return Math.max(RATIO_MIN, Math.min(RATIO_MAX, v));
    }

    private static String formatRatio(double v) {
        return String.format("%.1fx", v);
    }

    static String toHex(Color c) {
        if (c == null) {
            return "#000000";
        }
        int r = (int) Math.round(c.getRed() * 255.0);
        int g = (int) Math.round(c.getGreen() * 255.0);
        int b = (int) Math.round(c.getBlue() * 255.0);
        return String.format("#%02X%02X%02X",
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b)));
    }

    static Color parseHex(String hex, Color fallback) {
        if (hex == null || hex.isEmpty()) {
            return fallback;
        }
        try {
            return Color.web(hex);
        } catch (Exception e) {
            return fallback;
        }
    }
}
