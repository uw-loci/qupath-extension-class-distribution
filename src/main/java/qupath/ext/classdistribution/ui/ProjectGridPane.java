package qupath.ext.classdistribution.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassKey;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassSummary;
import qupath.ext.classdistribution.preferences.CDPreferences;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.ProjectImageEntry;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The "All images" tab: a scrollable, wrapping grid of {@link MiniChartPane}
 * thumbnails -- one per cached project image -- plus a frozen union legend
 * pinned above the scroll region and a zoom slider in the toolbar.
 *
 * <p>Stateless w.r.t. the data source: callers (the two dialogs) build a
 * list of {@code (entry, perClassMap)} pairs and hand them to
 * {@link #refresh}. A {@link UnaryOperator} lets the detection dialog
 * apply its split-multi-part transform without this class needing to know
 * about that semantics.
 *
 * @author Mike Nelson
 */
public final class ProjectGridPane extends VBox {

    private final QuPathGUI qupath;
    private final ResourceBundle resources;

    private final Slider zoomSlider;
    private final Label countLabel = new Label("");
    private final FlowPane legendPane = new FlowPane(8, 4);
    private final FlowPane grid = new FlowPane(10, 10);
    private final ScrollPane scrollPane = new ScrollPane();
    private final Label emptyPlaceholder = new Label("");

    private static final double ZOOM_MIN = 60.0;
    private static final double ZOOM_MAX = 240.0;

    public ProjectGridPane(QuPathGUI qupath, ResourceBundle resources) {
        super(6);
        this.qupath = qupath;
        this.resources = resources;
        setPadding(new Insets(8));

        // -- Top toolbar: zoom slider + image count -----------------
        Label zoomLabel = new Label(resources.getString("label.gridZoom"));
        zoomLabel.setTooltip(new Tooltip(resources.getString("tooltip.gridZoom")));

        zoomSlider = new Slider(ZOOM_MIN, ZOOM_MAX,
                clamp(CDPreferences.getGridThumbnailSize(), ZOOM_MIN, ZOOM_MAX));
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(60);
        zoomSlider.setMinorTickCount(2);
        zoomSlider.setBlockIncrement(20);
        zoomSlider.setTooltip(new Tooltip(resources.getString("tooltip.gridZoom")));
        zoomSlider.setPrefWidth(220);

        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);

        countLabel.setStyle("-fx-text-fill: -fx-text-base-color; -fx-opacity: 0.85;");

        HBox toolbar = new HBox(8, zoomLabel, zoomSlider, toolbarSpacer, countLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // -- Frozen legend ------------------------------------------
        legendPane.setAlignment(Pos.CENTER_LEFT);
        legendPane.setPadding(new Insets(4, 4, 4, 4));
        legendPane.setStyle("-fx-border-color: derive(-fx-base, -10%);"
                + "-fx-border-width: 0 0 1 0;");

        // -- Grid + scroll ------------------------------------------
        grid.setAlignment(Pos.TOP_LEFT);
        grid.setPadding(new Insets(8));

        scrollPane.setContent(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        emptyPlaceholder.setStyle("-fx-text-fill: -fx-text-base-color; -fx-opacity: 0.7;");
        emptyPlaceholder.setWrapText(true);
        emptyPlaceholder.setVisible(false);
        emptyPlaceholder.setManaged(false);

        getChildren().addAll(toolbar, legendPane, scrollPane, emptyPlaceholder);

        zoomSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                return;
            }
            double v = clamp(newV.doubleValue(), ZOOM_MIN, ZOOM_MAX);
            CDPreferences.setGridThumbnailSize(v);
            applyZoomToAllItems(v);
        });
    }

    /**
     * Re-render the grid from scratch.
     *
     * @param rows ordered list of {@code (entry, perClassMap)} from the cache.
     * @param currentlyOpen the project entry currently open in QuPath, or null.
     * @param postProcess a transform applied to each row's per-class map AND
     *                    to the union before legend computation; pass
     *                    {@link UnaryOperator#identity()} for no-op (annotation
     *                    dialog) or the split-multi-part transform (detection
     *                    dialog). Never null.
     */
    public void refresh(List<? extends RowEntry> rows,
                        ProjectImageEntry<?> currentlyOpen,
                        UnaryOperator<Map<ClassKey, ClassSummary>> postProcess) {
        grid.getChildren().clear();
        legendPane.getChildren().clear();

        if (rows == null || rows.isEmpty()) {
            countLabel.setText("");
            emptyPlaceholder.setText(resources.getString("empty.noGridImages"));
            emptyPlaceholder.setVisible(true);
            emptyPlaceholder.setManaged(true);
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            return;
        }
        emptyPlaceholder.setVisible(false);
        emptyPlaceholder.setManaged(false);
        scrollPane.setVisible(true);
        scrollPane.setManaged(true);

        countLabel.setText(String.format(
                resources.getString("label.gridImageCount"), rows.size()));

        // Build the union of classes across rows (post-processed so the
        // detection dialog's split-multi-part view groups correctly).
        Set<ClassKey> unionKeys = new LinkedHashSet<>();
        for (RowEntry row : rows) {
            Map<ClassKey, ClassSummary> processed = postProcess.apply(row.perClass());
            for (ClassKey k : processed.keySet()) {
                if (k != null) {
                    unionKeys.add(k);
                }
            }
        }
        for (ClassKey k : unionKeys) {
            legendPane.getChildren().add(buildLegendSwatch(k));
        }

        double zoom = clamp(zoomSlider.getValue(), ZOOM_MIN, ZOOM_MAX);
        for (RowEntry row : rows) {
            Map<ClassKey, ClassSummary> processed = postProcess.apply(row.perClass());
            grid.getChildren().add(buildGridItem(row, processed, zoom,
                    row.entry().equals(currentlyOpen)));
        }
    }

    private BorderPane buildGridItem(RowEntry row,
                                     Map<ClassKey, ClassSummary> processed,
                                     double zoom,
                                     boolean isCurrentlyOpen) {
        MiniChartPane mini = new MiniChartPane();
        mini.setData(processed);
        mini.resize(zoom);

        Label name = new Label(row.displayName());
        name.setMaxWidth(zoom);
        name.setStyle("-fx-font-size: 0.9em;");
        name.setEllipsisString("...");
        name.setAlignment(Pos.CENTER);

        BorderPane item = new BorderPane();
        item.setTop(name);
        BorderPane.setAlignment(name, Pos.CENTER);
        item.setCenter(mini);
        item.setPadding(new Insets(4));
        item.setCursor(Cursor.HAND);
        item.setUserData(row.entry());

        applyHighlight(item, isCurrentlyOpen);

        Tooltip.install(item, new Tooltip(buildTooltipText(row, processed)));

        item.setOnMouseClicked(e -> {
            @SuppressWarnings("unchecked")
            ProjectImageEntry<java.awt.image.BufferedImage> entry =
                    (ProjectImageEntry<java.awt.image.BufferedImage>) row.entry();
            try {
                qupath.openImageEntry(entry);
            } catch (Exception ex) {
                // Best-effort -- log via QuPath's notifier in callers if needed.
            }
        });
        return item;
    }

    private static void applyHighlight(BorderPane item, boolean isCurrentlyOpen) {
        if (isCurrentlyOpen) {
            item.setStyle("-fx-border-color: #00BCD4; -fx-border-width: 2;"
                    + "-fx-border-radius: 4; -fx-background-radius: 4;");
        } else {
            item.setStyle("-fx-border-color: transparent; -fx-border-width: 2;"
                    + "-fx-border-radius: 4; -fx-background-radius: 4;");
        }
    }

    private static HBox buildLegendSwatch(ClassKey key) {
        Rectangle swatch = new Rectangle(12, 12);
        swatch.setFill(parseCssRgb(ChartPane.baseColorFor(key)));
        swatch.setStroke(Color.color(0.4, 0.4, 0.4, 0.6));

        Label text = new Label(key.label());
        HBox row = new HBox(4, swatch, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String buildTooltipText(RowEntry row, Map<ClassKey, ClassSummary> processed) {
        StringBuilder sb = new StringBuilder();
        sb.append(row.fullName()).append('\n');
        sb.append(resources.getString("tooltip.gridThumbnail"));
        if (processed != null && !processed.isEmpty()) {
            sb.append("\n\n");
            int n = 0;
            for (Map.Entry<ClassKey, ClassSummary> e : processed.entrySet()) {
                if (n++ > 0) {
                    sb.append('\n');
                }
                ClassSummary s = e.getValue();
                if (s == null) {
                    continue;
                }
                sb.append(e.getKey().label()).append(": ").append(s.count());
            }
        }
        return sb.toString();
    }

    private void applyZoomToAllItems(double zoom) {
        for (var node : grid.getChildren()) {
            if (!(node instanceof BorderPane item)) {
                continue;
            }
            if (item.getCenter() instanceof MiniChartPane mini) {
                mini.resize(zoom);
            }
            if (item.getTop() instanceof Label label) {
                label.setMaxWidth(zoom);
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, v));
    }

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
            return Color.rgb(
                    clip(Integer.parseInt(parts[0].trim())),
                    clip(Integer.parseInt(parts[1].trim())),
                    clip(Integer.parseInt(parts[2].trim())));
        } catch (Exception e) {
            return Color.GRAY;
        }
    }

    private static int clip(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Minimal value type the two dialogs pass to {@link #refresh}.
     * Decouples this class from each cache's concrete row class.
     */
    public interface RowEntry {
        ProjectImageEntry<?> entry();
        String displayName();
        String fullName();
        Map<ClassKey, ClassSummary> perClass();
    }

    /**
     * Convenience constructor: build a RowEntry from a project entry +
     * per-class map. The display and full name fall back to a sentinel if
     * the entry's name is blank.
     */
    public static RowEntry rowFor(ProjectImageEntry<?> entry,
                                  Map<ClassKey, ClassSummary> perClass) {
        String name = safeName(entry);
        return new RowEntryImpl(entry, name, name,
                perClass == null ? new LinkedHashMap<>() : perClass);
    }

    private static String safeName(ProjectImageEntry<?> entry) {
        if (entry == null) {
            return "(image)";
        }
        try {
            String n = entry.getImageName();
            return (n == null || n.isBlank()) ? "(image)" : n;
        } catch (Exception e) {
            return "(image)";
        }
    }

    private record RowEntryImpl(ProjectImageEntry<?> entry,
                                String displayName,
                                String fullName,
                                Map<ClassKey, ClassSummary> perClass)
            implements RowEntry {
    }
}
