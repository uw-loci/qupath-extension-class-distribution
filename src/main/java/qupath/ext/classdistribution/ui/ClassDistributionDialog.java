package qupath.ext.classdistribution.ui;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classdistribution.core.ContributionCalculator;
import qupath.ext.classdistribution.core.HighlightEvaluator;
import qupath.ext.classdistribution.core.ProjectAnnotationCache;
import qupath.ext.classdistribution.core.ProjectAnnotationCache.FilterSummary;
import qupath.ext.classdistribution.preferences.CDPreferences;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Single non-modal {@link Stage} that hosts the chart, the live listeners,
 * the dirty banner, and the project-poll plumbing.
 *
 * <p>Architecture (Phase 1 design, section 1):
 * <ul>
 *   <li>Owns the {@link ProjectAnnotationCache} (per-image rows) and the
 *       {@link ChartPane} (pure JavaFX).</li>
 *   <li>Listens to the open image's hierarchy and re-renders on
 *       annotation changes. Filters {@code event.isChanging()} per
 *       gated-classifier's pattern
 *       ({@code GatedClassifierDialog.java:494-553}).</li>
 *   <li>Listens to {@code qupath.imageDataProperty()} and {@code
 *       qupath.projectProperty()}; on image switch, rebinds (does not
 *       close, contra gated-classifier).</li>
 *   <li>Single-instance: re-invoking the menu raises the existing window
 *       via {@link Stage#toFront()} rather than spawning a duplicate.</li>
 * </ul>
 *
 * @author Mike Nelson
 */
public final class ClassDistributionDialog {

    private static final Logger logger = LoggerFactory.getLogger(ClassDistributionDialog.class);

    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.classdistribution.strings");

    /** Sentinel filter value meaning "include every cached image". */
    private static final ImageTypeFilter ALL_TYPES_FILTER =
            new ImageTypeFilter(null, resources.getString("imagetype.allTypes"));

    /** Sentinel filter value matching only UNSET / null image types. */
    private static final ImageTypeFilter UNSET_FILTER =
            new ImageTypeFilter(ImageData.ImageType.UNSET, resources.getString("imagetype.unset"));

    // Date + time in the timestamp so a long-running session that crosses
    // midnight (or multiple days) is unambiguous in screenshots and audit
    // notes (Phase 5 PI feedback). ISO-style is locale-neutral and ASCII.
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Single-instance singleton. Static reference is cleared in
     * {@link #stage}'s {@code setOnHidden(...)} so a second invocation
     * after close opens a fresh dialog.
     */
    private static ClassDistributionDialog INSTANCE;

    private final QuPathGUI qupath;
    private final Stage stage;
    private final ProjectAnnotationCache cache = new ProjectAnnotationCache();

    private final ComboBox<ImageTypeFilter> typeCombo = new ComboBox<>();
    private final Button repollButton = new Button(resources.getString("label.repollProject"));
    private final Button cancelButton = new Button(resources.getString("label.repollCancel"));
    private final ProgressIndicator pollProgress = new ProgressIndicator();
    private final DirtyBanner dirtyBanner;
    private final ChartPane chartPane;
    private final AdvancedSection advanced;
    private final Label statusSummary = new Label("");
    private final Label statusLastPolled = new Label(resources.getString("status.lastPolledNever"));

    // Listener references kept so we can detach in setOnHidden.
    private PathObjectHierarchy boundHierarchy;
    private PathObjectHierarchyListener hierarchyListener;
    private ChangeListener<ImageData<BufferedImage>> imageDataListener;
    private ChangeListener<Project<BufferedImage>> projectListener;

    private final BooleanProperty pollInProgress = new SimpleBooleanProperty(false);
    /** The currently-running refresh task (for cancellation). Null when idle. */
    private Task<Void> currentTask;

    private ClassDistributionDialog(QuPathGUI qupath) {
        this.qupath = qupath;
        this.stage = new Stage();
        this.dirtyBanner = new DirtyBanner(qupath, resources);
        this.chartPane = new ChartPane(resources);
        this.advanced = new AdvancedSection(resources);
        configureStage();
    }

    /**
     * Opens the dialog, or raises the existing instance if one is already
     * showing. Safe to call from any thread.
     */
    public static void showDialog(QuPathGUI qupath) {
        if (qupath == null) {
            return;
        }
        Runnable run = () -> {
            if (INSTANCE != null && INSTANCE.stage.isShowing()) {
                INSTANCE.stage.toFront();
                INSTANCE.stage.requestFocus();
                return;
            }
            INSTANCE = new ClassDistributionDialog(qupath);
            INSTANCE.stage.show();
            // Wire post-show side effects (listeners, initial poll) once the
            // window is on screen so listener detach in setOnHidden runs
            // even if the user closes immediately.
            INSTANCE.attachListenersAfterShow();
            INSTANCE.startInitialPoll();
        };
        if (Platform.isFxApplicationThread()) {
            run.run();
        } else {
            Platform.runLater(run);
        }
    }

    // ------------------------------------------------------------------
    // Stage configuration
    // ------------------------------------------------------------------

    private void configureStage() {
        stage.setTitle(resources.getString("dialog.title"));
        stage.initOwner(qupath.getStage());
        stage.initModality(Modality.NONE);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: -fx-base;");

        VBox header = new VBox(8);
        header.setPadding(new Insets(10, 12, 0, 12));
        header.getChildren().addAll(dirtyBanner, buildTopRow());
        root.setTop(header);

        // Centre: chart with a busy overlay
        StackPane centre = new StackPane();
        centre.setPadding(new Insets(0, 12, 0, 12));
        chartPane.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(chartPane, Priority.ALWAYS);
        pollProgress.setMaxSize(64, 64);
        pollProgress.setVisible(false);
        pollProgress.setManaged(false);
        // Accessibility: ProgressIndicator carries no visible text label;
        // give screen readers something descriptive.
        pollProgress.setAccessibleText(resources.getString("label.repollProject"));
        centre.getChildren().addAll(chartPane, pollProgress);
        StackPane.setAlignment(pollProgress, Pos.CENTER);
        root.setCenter(centre);

        VBox bottom = new VBox(8);
        bottom.setPadding(new Insets(0, 12, 12, 12));
        bottom.getChildren().addAll(advanced, buildStatusBar());
        root.setBottom(bottom);

        Scene scene = new Scene(root);
        // Escape closes the dialog (UX checklist: same as Cancel; pattern
        // borrowed from channel-names-viewer ChannelLegendStage line 432-440).
        // Use an event filter so the stage gets the key press before any
        // focused control consumes it.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.hide();
                e.consume();
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(420);
        stage.setMinHeight(480);

        // Restore size + position from preferences.
        double width = clampDimension(CDPreferences.getDialogWidth(), 420.0, 2400.0, 520.0);
        double height = clampDimension(CDPreferences.getDialogHeight(), 480.0, 1800.0, 620.0);
        stage.setWidth(width);
        stage.setHeight(height);
        applyPersistedPosition();

        // Persist size + position on hide.
        stage.setOnHidden(e -> handleHidden());
    }

    private HBox buildTopRow() {
        Label typeLabel = new Label(resources.getString("label.imageType"));
        typeLabel.setTooltip(new Tooltip(resources.getString("tooltip.imageType")));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setTooltip(new Tooltip(resources.getString("tooltip.imageType")));
        typeCombo.setConverter(new StringConverter<ImageTypeFilter>() {
            @Override public String toString(ImageTypeFilter f) {
                return f == null ? "" : f.label();
            }
            @Override public ImageTypeFilter fromString(String s) {
                return null;
            }
        });
        rebuildTypeComboItems();
        typeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                return;
            }
            CDPreferences.setLastImageTypeFilter(newV.persistKey());
            renderChart();
        });

        repollButton.setTooltip(new Tooltip(resources.getString("tooltip.repoll")));
        repollButton.setOnAction(e -> startProjectPoll());
        repollButton.disableProperty().bind(qupath.projectProperty().isNull().or(pollInProgress));

        // Cancel button: visible only while a refresh task is running.
        cancelButton.setTooltip(new Tooltip(resources.getString("tooltip.repollCancel")));
        cancelButton.setOnAction(e -> {
            if (currentTask != null) {
                currentTask.cancel();
            }
        });
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(typeCombo, Priority.ALWAYS);

        HBox row = new HBox(8, typeLabel, typeCombo, spacer, repollButton, cancelButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0));
        return row;
    }

    private HBox buildStatusBar() {
        statusSummary.setMaxWidth(Double.MAX_VALUE);
        statusSummary.setTooltip(new Tooltip(resources.getString("tooltip.status.summary")));
        statusLastPolled.setTooltip(new Tooltip(resources.getString("tooltip.status.lastPolled")));
        Region spacer = new Region();
        HBox.setHgrow(statusSummary, Priority.ALWAYS);
        HBox row = new HBox(8, statusSummary, spacer, statusLastPolled);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 4, 2, 4));
        row.setStyle("-fx-text-fill: -fx-text-base-color; -fx-opacity: 0.85;");
        return row;
    }

    private void applyPersistedPosition() {
        double x = CDPreferences.getDialogX();
        double y = CDPreferences.getDialogY();
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return;
        }
        // Clamp into the union of currently-connected screens so a window
        // saved on a now-disconnected monitor still becomes visible.
        boolean inAnyScreen = false;
        for (var screen : Screen.getScreens()) {
            Rectangle2D b = screen.getVisualBounds();
            if (x >= b.getMinX() - 50 && x <= b.getMaxX() - 50
                    && y >= b.getMinY() - 50 && y <= b.getMaxY() - 50) {
                inAnyScreen = true;
                break;
            }
        }
        if (inAnyScreen) {
            stage.setX(x);
            stage.setY(y);
        }
    }

    private static double clampDimension(double v, double min, double max, double fallback) {
        if (Double.isNaN(v) || v <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------
    // Listener lifecycle
    // ------------------------------------------------------------------

    private void attachListenersAfterShow() {
        // Image-data switch: rebind (do NOT close; design directive 2.1.5).
        imageDataListener = (obs, oldData, newData) -> Platform.runLater(() -> {
            if (!stage.isShowing()) {
                return;
            }
            rebindHierarchy(newData);
            renderChart();
        });
        qupath.imageDataProperty().addListener(imageDataListener);

        // Project change: clear cache, rebuild combo, re-poll.
        projectListener = (obs, oldP, newP) -> Platform.runLater(() -> {
            if (!stage.isShowing()) {
                return;
            }
            cache.clear();
            rebuildTypeComboItems();
            renderChart();
            if (newP != null) {
                startProjectPoll();
            }
        });
        qupath.projectProperty().addListener(projectListener);

        // Initial bind to the open image (if any).
        rebindHierarchy(qupath.getImageData());

        // Wire Advanced controls back into the chart.
        advanced.onPolylineWidthChanged(v -> {
            // Polyline width affects every cached row's contributions, so
            // we have to recompute the open image (cheap; uses the live
            // hierarchy) and warn that a re-poll is needed for the rest.
            recomputeOpenImage();
            renderChart();
        });
        advanced.onRatioChanged(r -> renderChart());
        advanced.onColorsChanged(colors -> renderChart());
        advanced.onShowLabelsChanged(v -> renderChart());
    }

    private void rebindHierarchy(ImageData<BufferedImage> imageData) {
        // Detach from the old hierarchy first.
        if (boundHierarchy != null && hierarchyListener != null) {
            try {
                boundHierarchy.removeListener(hierarchyListener);
            } catch (Exception ignored) {
                // best-effort detach
            }
        }
        boundHierarchy = null;

        if (imageData == null) {
            dirtyBanner.refresh(null);
            recomputeOpenImage();
            return;
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        if (hierarchy == null) {
            dirtyBanner.refresh(imageData);
            recomputeOpenImage();
            return;
        }
        hierarchyListener = event -> {
            if (event != null && event.isChanging()) {
                return;
            }
            Platform.runLater(() -> {
                if (!stage.isShowing()) {
                    return;
                }
                try {
                    recomputeOpenImage();
                    renderChart();
                    dirtyBanner.refresh(qupath.getImageData());
                } catch (Exception e) {
                    logger.warn("Live update failed: {}", e.toString());
                    chartPane.showPlaceholder(resources.getString("empty.liveUpdateFailed"));
                }
            });
        };
        hierarchy.addListener(hierarchyListener);
        boundHierarchy = hierarchy;
        dirtyBanner.refresh(imageData);
        recomputeOpenImage();
    }

    private void handleHidden() {
        // Detach all listeners.
        if (boundHierarchy != null && hierarchyListener != null) {
            try {
                boundHierarchy.removeListener(hierarchyListener);
            } catch (Exception ignored) {
                // best-effort detach
            }
        }
        boundHierarchy = null;
        hierarchyListener = null;
        if (imageDataListener != null) {
            qupath.imageDataProperty().removeListener(imageDataListener);
            imageDataListener = null;
        }
        if (projectListener != null) {
            qupath.projectProperty().removeListener(projectListener);
            projectListener = null;
        }
        // Persist position + size.
        CDPreferences.setDialogX(stage.getX());
        CDPreferences.setDialogY(stage.getY());
        CDPreferences.setDialogWidth(stage.getWidth());
        CDPreferences.setDialogHeight(stage.getHeight());
        // Clear singleton so a fresh instance opens next time.
        if (INSTANCE == this) {
            INSTANCE = null;
        }
        dirtyBanner.clear();
    }

    // ------------------------------------------------------------------
    // Polling
    // ------------------------------------------------------------------

    private void startInitialPoll() {
        // Render whatever is currently in the (empty) cache, then kick a poll.
        renderChart();
        if (qupath.getProject() != null) {
            startProjectPoll();
        }
    }

    private void startProjectPoll() {
        if (pollInProgress.get()) {
            return;
        }
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            return;
        }
        pollInProgress.set(true);
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        pollProgress.setVisible(true);
        pollProgress.setManaged(true);
        pollProgress.setProgress(0);
        chartPane.setBusy(true);

        int polylineWidth = advanced.getPolylineWidthPx();
        int total = project.getImageList().size();
        // NOTE: status / progress strings use printf-style placeholders
        // (%d, %s) and are formatted via String.format. Only
        // empty.noMatchingImages uses MessageFormat ({0}) syntax.
        repollButton.setText(String.format(resources.getString("label.repollProgress"), 0, total));

        final Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                cache.repollAll(project, polylineWidth, progress -> {
                    int processed = (int) Math.round(progress * total);
                    Platform.runLater(() -> {
                        pollProgress.setProgress(progress);
                        if (!isCancelled()) {
                            repollButton.setText(String.format(
                                    resources.getString("label.repollProgress"),
                                    processed, total));
                        }
                    });
                }, this::isCancelled);
                return null;
            }
        };
        currentTask = task;
        task.setOnSucceeded(e -> {
            finishPoll(true);
            // Recompute the open image's row from its live hierarchy
            // (the cache poll loaded the *saved* version from disk).
            recomputeOpenImage();
            statusLastPolled.setText(String.format(
                    resources.getString("status.lastPolledFormat"),
                    LocalDateTime.now().format(TIME_FORMAT)));
            // First poll: pick the user's persisted filter or compute a default.
            initialiseFilter();
            renderChart();
        });
        task.setOnCancelled(e -> {
            // Show a brief "cancelled" status before returning to idle. The
            // partial cache is left intact so the chart still reflects what
            // was loaded before the user pressed Cancel.
            finishPoll(false);
            repollButton.setText(resources.getString("label.repollCancelled"));
            recomputeOpenImage();
            renderChart();
            // Reset the button label after a short delay so the user sees
            // the cancelled state but the dialog returns to idle.
            javafx.animation.PauseTransition pause =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            pause.setOnFinished(ev -> repollButton.setText(
                    resources.getString("label.repollProject")));
            pause.play();
        });
        task.setOnFailed(e -> {
            finishPoll(false);
            Throwable err = task.getException();
            logger.error("Project refresh failed", err);
            Dialogs.showErrorNotification(
                    resources.getString("notify.repollFailedTitle"),
                    resources.getString("notify.repollFailedBody"));
        });

        Thread t = new Thread(task, "ClassDistribution-Refresh");
        t.setDaemon(true);
        t.start();
    }

    private void finishPoll(boolean ok) {
        pollInProgress.set(false);
        currentTask = null;
        repollButton.setText(resources.getString("label.repollProject"));
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        pollProgress.setVisible(false);
        pollProgress.setManaged(false);
        chartPane.setBusy(false);
        rebuildTypeComboItems();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void recomputeOpenImage() {
        ImageData<BufferedImage> data = qupath.getImageData();
        if (data == null) {
            return;
        }
        Project<BufferedImage> project = qupath.getProject();
        ProjectImageEntry<BufferedImage> entry = project == null ? null : project.getEntry(data);
        cache.updateOpen(entry, data.getImageType(), data.getHierarchy(),
                advanced.getPolylineWidthPx());
    }

    private void renderChart() {
        if (qupath.getProject() == null && qupath.getImageData() == null) {
            chartPane.showPlaceholder(resources.getString("empty.noProject"));
            statusSummary.setText("");
            return;
        }
        ImageTypeFilter filter = typeCombo.getValue();
        if (filter == null) {
            filter = ALL_TYPES_FILTER;
        }

        FilterSummary summary = cache.summarize(filter.imageType());
        if (summary.imageCount() == 0 && filter != ALL_TYPES_FILTER) {
            // empty.noMatchingImages uses {0} -> MessageFormat.
            chartPane.showPlaceholder(MessageFormat.format(
                    resources.getString("empty.noMatchingImages"), filter.label()));
            statusSummary.setText(String.format(
                    resources.getString("status.summaryFormat"), 0, 0));
            return;
        }
        if (summary.contributions().isEmpty() || sumContribution(summary.contributions()) <= 0.0) {
            chartPane.showPlaceholder(resources.getString("empty.noAnnotations"));
            statusSummary.setText(String.format(
                    resources.getString("status.summaryFormat"),
                    summary.annotationCount(), summary.imageCount()));
            return;
        }
        var evaluation = HighlightEvaluator.evaluate(summary.contributions(),
                advanced.getHighlightRatio());
        chartPane.refresh(summary.contributions(), evaluation,
                advanced.isShowSliceLabels(),
                advanced.getOverColorHex(),
                advanced.getUnderColorHex());
        statusSummary.setText(String.format(
                resources.getString("status.summaryFormat"),
                summary.annotationCount(), summary.imageCount()));
    }

    private static double sumContribution(
            Map<ContributionCalculator.ClassKey, ContributionCalculator.ClassSummary> map) {
        double t = 0;
        for (var s : map.values()) {
            t += s.total();
        }
        return t;
    }

    // ------------------------------------------------------------------
    // ImageType combo
    // ------------------------------------------------------------------

    private void rebuildTypeComboItems() {
        ImageTypeFilter previous = typeCombo.getValue();
        List<ImageTypeFilter> items = new ArrayList<>();
        items.add(ALL_TYPES_FILTER);
        Map<ImageData.ImageType, Integer> counts = cache.typeCounts();
        // Always offer every QuPath enum value so the user can pre-select
        // a type before the cache has any rows of that type.
        for (ImageData.ImageType t : ImageData.ImageType.values()) {
            if (t == ImageData.ImageType.UNSET) {
                continue;
            }
            int n = counts.getOrDefault(t, 0);
            String suffix = n > 0 ? " (" + n + ")" : "";
            items.add(new ImageTypeFilter(t, prettyTypeName(t) + suffix));
        }
        items.add(UNSET_FILTER);
        typeCombo.setItems(FXCollections.observableArrayList(items));
        // Restore previous selection by persistKey if possible.
        if (previous != null) {
            for (ImageTypeFilter f : items) {
                if (java.util.Objects.equals(f.persistKey(), previous.persistKey())) {
                    typeCombo.getSelectionModel().select(f);
                    return;
                }
            }
        }
        typeCombo.getSelectionModel().select(ALL_TYPES_FILTER);
    }

    /**
     * Picks the dialog's initial filter after the first project poll
     * completes. Order of preference:
     * <ol>
     *   <li>The user's persisted last filter (if it still matches a row).</li>
     *   <li>The currently open image's type (never UNSET as auto-default).</li>
     *   <li>The most common non-UNSET type in the cache.</li>
     *   <li>"All types".</li>
     * </ol>
     */
    private void initialiseFilter() {
        String persisted = CDPreferences.getLastImageTypeFilter();
        ImageTypeFilter chosen = null;
        if (persisted != null && !persisted.isEmpty()) {
            for (ImageTypeFilter f : typeCombo.getItems()) {
                if (persisted.equals(f.persistKey())) {
                    chosen = f;
                    break;
                }
            }
        }
        if (chosen == null) {
            ImageData<BufferedImage> data = qupath.getImageData();
            ImageData.ImageType openType = data == null ? null : data.getImageType();
            if (openType != null && openType != ImageData.ImageType.UNSET) {
                chosen = findFilterFor(openType);
            }
        }
        if (chosen == null) {
            ImageData.ImageType common = cache.mostCommonNonUnsetType();
            if (common != null) {
                chosen = findFilterFor(common);
            }
        }
        if (chosen == null) {
            chosen = ALL_TYPES_FILTER;
        }
        typeCombo.getSelectionModel().select(chosen);
    }

    private ImageTypeFilter findFilterFor(ImageData.ImageType t) {
        for (ImageTypeFilter f : typeCombo.getItems()) {
            if (f.imageType() == t) {
                return f;
            }
        }
        return null;
    }

    /**
     * Prettier label for a {@link ImageData.ImageType} enum value
     * (replaces underscores with spaces, title-cases tokens).
     */
    private static String prettyTypeName(ImageData.ImageType t) {
        if (t == null) {
            return "";
        }
        String raw = t.name();
        StringBuilder sb = new StringBuilder(raw.length());
        for (String token : raw.split("_")) {
            if (token.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(token.charAt(0));
            if (token.length() > 1) {
                sb.append(token.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Filter value type
    // ------------------------------------------------------------------

    /**
     * One entry in the ImageType ComboBox. {@code imageType == null} means
     * "All types"; {@code imageType == UNSET} means the explicit Unset
     * bucket.
     */
    static final class ImageTypeFilter {
        private final ImageData.ImageType imageType;
        private final String label;

        ImageTypeFilter(ImageData.ImageType imageType, String label) {
            this.imageType = imageType;
            this.label = label == null ? "" : label;
        }

        ImageData.ImageType imageType() {
            return imageType;
        }

        String label() {
            return label;
        }

        /**
         * String key that survives across sessions. Empty for "all types"
         * (matches the empty-string default of
         * {@link CDPreferences#getLastImageTypeFilter()}).
         */
        String persistKey() {
            return imageType == null ? "" : imageType.name();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ImageTypeFilter other)) return false;
            return java.util.Objects.equals(imageType, other.imageType);
        }

        @Override
        public int hashCode() {
            return imageType == null ? 0 : imageType.hashCode();
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
