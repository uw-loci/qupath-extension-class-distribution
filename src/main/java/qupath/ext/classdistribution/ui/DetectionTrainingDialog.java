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
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
import qupath.ext.classdistribution.core.DetectionLabelCalculator;
import qupath.ext.classdistribution.core.DetectionTrainingCache;
import qupath.ext.classdistribution.core.DetectionTrainingCache.FilterSummary;
import qupath.ext.classdistribution.core.HighlightEvaluator;
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
 * Sister of {@link ClassDistributionDialog}, but the chart is driven by
 * how many DETECTIONS each class would label given the current set of
 * training annotations (area annotations enclosing detections, or
 * point annotations dropped on individual detections). Mirrors the
 * exact labeling logic in QuPath's Object Classifier command -- see
 * {@link DetectionLabelCalculator}.
 *
 * <p>Layout mirrors {@link ClassDistributionDialog}: Project + Current
 * Image either as tabs or side-by-side, shared Advanced section, shared
 * project status bar. The polyline-width control is hidden in this
 * dialog's Advanced section because polylines are not a labeling
 * mechanism for detections.
 *
 * @author Mike Nelson
 */
public final class DetectionTrainingDialog {

    private static final Logger logger = LoggerFactory.getLogger(DetectionTrainingDialog.class);

    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.classdistribution.strings");

    private static final ClassDistributionDialog.ImageTypeFilter ALL_TYPES_FILTER =
            new ClassDistributionDialog.ImageTypeFilter(null,
                    resources.getString("imagetype.allTypes"));

    private static final ClassDistributionDialog.ImageTypeFilter UNSET_FILTER =
            new ClassDistributionDialog.ImageTypeFilter(ImageData.ImageType.UNSET,
                    resources.getString("imagetype.unset"));

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static DetectionTrainingDialog INSTANCE;

    private final QuPathGUI qupath;
    private final Stage stage;
    private final DetectionTrainingCache cache = new DetectionTrainingCache();

    private final ComboBox<ClassDistributionDialog.ImageTypeFilter> typeCombo = new ComboBox<>();
    private final Button repollButton = new Button(resources.getString("label.repollProject"));
    private final Button cancelButton = new Button(resources.getString("label.repollCancel"));
    private final ProgressIndicator pollProgress = new ProgressIndicator();
    private final DirtyBanner dirtyBanner;
    private final ChartPane chartPane;
    private final ChartPane currentImageChartPane;
    private final Label currentImageHeader = new Label("");
    private final Label currentImageStatus = new Label("");
    private final TabPane tabPane = new TabPane();
    private final SplitPane sideBySidePane = new SplitPane();
    private final BorderPane centreWrap = new BorderPane();
    private VBox projectTabContent;
    private VBox currentImageTabContent;
    private final AdvancedSection advanced;
    private final Label statusSummary = new Label("");
    private final Label statusLastPolled = new Label(resources.getString("status.lastPolledNever"));

    private PathObjectHierarchy boundHierarchy;
    private PathObjectHierarchyListener hierarchyListener;
    private ChangeListener<ImageData<BufferedImage>> imageDataListener;
    private ChangeListener<Project<BufferedImage>> projectListener;

    private final BooleanProperty pollInProgress = new SimpleBooleanProperty(false);
    private Task<Void> currentTask;

    private DetectionTrainingDialog(QuPathGUI qupath) {
        this.qupath = qupath;
        this.stage = new Stage();
        this.dirtyBanner = new DirtyBanner(qupath, resources);
        this.chartPane = new ChartPane(resources);
        this.currentImageChartPane = new ChartPane(resources);
        // Hide polyline-width control -- not a labeling mechanism for detections.
        this.advanced = new AdvancedSection(resources, false);
        configureStage();
    }

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
            INSTANCE = new DetectionTrainingDialog(qupath);
            INSTANCE.stage.show();
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
        stage.setTitle(resources.getString("dialog.titleDetections"));
        stage.initOwner(qupath.getStage());
        stage.initModality(Modality.NONE);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: -fx-base;");

        VBox header = new VBox(8);
        header.setPadding(new Insets(10, 12, 0, 12));
        header.getChildren().add(dirtyBanner);
        root.setTop(header);

        tabPane.setSide(javafx.geometry.Side.TOP);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sideBySidePane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);

        projectTabContent = buildProjectTabContent();
        currentImageTabContent = buildCurrentImageTabContent();

        centreWrap.setPadding(new Insets(0, 12, 0, 12));
        applyDisplayMode(CDPreferences.isSideBySide());
        root.setCenter(centreWrap);

        VBox bottom = new VBox(8);
        bottom.setPadding(new Insets(0, 12, 12, 12));
        bottom.getChildren().addAll(advanced, buildStatusBar());
        root.setBottom(bottom);

        Scene scene = new Scene(root);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.hide();
                e.consume();
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(420);
        stage.setMinHeight(480);

        double width = clampDimension(CDPreferences.getDialogWidth(), 420.0, 2400.0, 520.0);
        double height = clampDimension(CDPreferences.getDialogHeight(), 480.0, 1800.0, 620.0);
        stage.setWidth(width);
        stage.setHeight(height);
        applyPersistedPosition();

        stage.setOnHidden(e -> handleHidden());
    }

    private VBox buildProjectTabContent() {
        Label sectionHeader = new Label(resources.getString("label.projectHeader"));
        sectionHeader.setStyle("-fx-font-weight: bold;");

        Label typeLabel = new Label(resources.getString("label.imageType"));
        typeLabel.setTooltip(new Tooltip(resources.getString("tooltip.imageType")));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setTooltip(new Tooltip(resources.getString("tooltip.imageType")));
        typeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ClassDistributionDialog.ImageTypeFilter f) {
                return f == null ? "" : f.label();
            }
            @Override public ClassDistributionDialog.ImageTypeFilter fromString(String s) {
                return null;
            }
        });
        rebuildTypeComboItems();
        typeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                return;
            }
            CDPreferences.setLastImageTypeFilter(newV.persistKey());
            renderProjectChart();
        });

        repollButton.setTooltip(new Tooltip(resources.getString("tooltip.repoll")));
        repollButton.setOnAction(e -> startProjectPoll());
        repollButton.disableProperty().bind(qupath.projectProperty().isNull().or(pollInProgress));

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

        HBox topRow = new HBox(8, typeLabel, typeCombo, spacer, repollButton, cancelButton);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPadding(new Insets(0, 8, 4, 8));

        VBox headerBox = new VBox(4, sectionHeader, topRow);
        headerBox.setPadding(new Insets(8, 0, 0, 0));

        StackPane chartHolder = new StackPane();
        chartPane.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(chartPane, Priority.ALWAYS);
        pollProgress.setMaxSize(64, 64);
        pollProgress.setVisible(false);
        pollProgress.setManaged(false);
        pollProgress.setAccessibleText(resources.getString("label.repollProject"));
        chartHolder.getChildren().addAll(chartPane, pollProgress);
        StackPane.setAlignment(pollProgress, Pos.CENTER);

        VBox content = new VBox(4, headerBox, chartHolder);
        VBox.setVgrow(chartHolder, Priority.ALWAYS);
        return content;
    }

    private VBox buildCurrentImageTabContent() {
        currentImageHeader.setStyle("-fx-font-weight: bold;");
        currentImageStatus.setStyle("-fx-text-fill: -fx-text-base-color; -fx-opacity: 0.85;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(8, currentImageHeader, spacer, currentImageStatus);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPadding(new Insets(8, 8, 4, 8));

        currentImageChartPane.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(currentImageChartPane, Priority.ALWAYS);

        VBox content = new VBox(4, topRow, currentImageChartPane);
        return content;
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
        imageDataListener = (obs, oldData, newData) -> Platform.runLater(() -> {
            if (!stage.isShowing()) {
                return;
            }
            rebindHierarchy(newData);
            renderChart();
        });
        qupath.imageDataProperty().addListener(imageDataListener);

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

        rebindHierarchy(qupath.getImageData());

        // Polyline width is not exposed in this dialog, but the listener is
        // still wired (no-op for detection labels).
        advanced.onPolylineWidthChanged(v -> {
            recomputeOpenImage();
            renderChart();
        });
        advanced.onRatioChanged(r -> renderChart());
        advanced.onColorsChanged(colors -> renderChart());
        advanced.onShowLabelsChanged(v -> renderChart());
        advanced.onSideBySideChanged(this::applyDisplayMode);
    }

    private void applyDisplayMode(boolean sideBySide) {
        tabPane.getTabs().clear();
        sideBySidePane.getItems().clear();

        if (sideBySide) {
            sideBySidePane.getItems().setAll(projectTabContent, currentImageTabContent);
            sideBySidePane.setDividerPositions(0.5);
            centreWrap.setCenter(sideBySidePane);
        } else {
            Tab projectTab = new Tab(resources.getString("tab.project"), projectTabContent);
            Tab currentImageTab = new Tab(resources.getString("tab.currentImage"), currentImageTabContent);
            projectTab.setClosable(false);
            currentImageTab.setClosable(false);
            tabPane.getTabs().setAll(projectTab, currentImageTab);
            centreWrap.setCenter(tabPane);
        }
        renderChart();
    }

    private void rebindHierarchy(ImageData<BufferedImage> imageData) {
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
        CDPreferences.setDialogX(stage.getX());
        CDPreferences.setDialogY(stage.getY());
        CDPreferences.setDialogWidth(stage.getWidth());
        CDPreferences.setDialogHeight(stage.getHeight());
        if (INSTANCE == this) {
            INSTANCE = null;
        }
        dirtyBanner.clear();
    }

    // ------------------------------------------------------------------
    // Polling
    // ------------------------------------------------------------------

    private void startInitialPoll() {
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

        int total = project.getImageList().size();
        repollButton.setText(String.format(resources.getString("label.repollProgress"), 0, total));

        final Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                cache.repollAll(project, progress -> {
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
            recomputeOpenImage();
            statusLastPolled.setText(String.format(
                    resources.getString("status.lastPolledFormat"),
                    LocalDateTime.now().format(TIME_FORMAT)));
            initialiseFilter();
            renderChart();
        });
        task.setOnCancelled(e -> {
            finishPoll(false);
            repollButton.setText(resources.getString("label.repollCancelled"));
            recomputeOpenImage();
            renderChart();
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

        Thread t = new Thread(task, "DetectionTraining-Refresh");
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
        cache.updateOpen(entry, data.getImageType(), data.getHierarchy());
    }

    private void renderChart() {
        renderProjectChart();
        renderCurrentImageChart();
    }

    private void renderProjectChart() {
        if (qupath.getProject() == null && qupath.getImageData() == null) {
            chartPane.showPlaceholder(resources.getString("empty.noProject"));
            statusSummary.setText("");
            return;
        }
        ClassDistributionDialog.ImageTypeFilter filter = typeCombo.getValue();
        if (filter == null) {
            filter = ALL_TYPES_FILTER;
        }

        FilterSummary summary = cache.summarize(filter.imageType());
        if (summary.imageCount() == 0 && filter != ALL_TYPES_FILTER) {
            chartPane.showPlaceholder(MessageFormat.format(
                    resources.getString("empty.noMatchingImages"), filter.label()));
            statusSummary.setText(String.format(
                    resources.getString("status.detectionsSummaryFormat"), 0, 0, 0));
            return;
        }
        if (summary.contributions().isEmpty() || sumContribution(summary.contributions()) <= 0.0) {
            chartPane.showPlaceholder(resources.getString("empty.noDetectionLabels"));
            statusSummary.setText(String.format(
                    resources.getString("status.detectionsSummaryFormat"),
                    summary.distinctLabeledDetections(),
                    summary.trainingAnnotationCount(),
                    summary.imageCount()));
            return;
        }
        var evaluation = HighlightEvaluator.evaluate(summary.contributions(),
                advanced.getHighlightRatio());
        chartPane.refresh(summary.contributions(), evaluation,
                advanced.isShowSliceLabels(),
                advanced.getOverColorHex(),
                advanced.getUnderColorHex());
        statusSummary.setText(String.format(
                resources.getString("status.detectionsSummaryFormat"),
                summary.distinctLabeledDetections(),
                summary.trainingAnnotationCount(),
                summary.imageCount()));
    }

    private void renderCurrentImageChart() {
        ImageData<BufferedImage> data = qupath.getImageData();
        if (data == null) {
            currentImageHeader.setText(resources.getString("label.currentImageNoneOpen"));
            currentImageStatus.setText("");
            currentImageChartPane.showPlaceholder(resources.getString("empty.noOpenImage"));
            return;
        }
        PathObjectHierarchy hierarchy = data.getHierarchy();
        String name = imageDisplayName(data);
        currentImageHeader.setText(String.format(
                resources.getString("label.currentImageHeader"), name));

        if (hierarchy == null) {
            currentImageStatus.setText("");
            currentImageChartPane.showPlaceholder(resources.getString("empty.noDetectionLabelsCurrent"));
            return;
        }
        DetectionLabelCalculator.Result result = DetectionLabelCalculator.aggregate(hierarchy);
        currentImageStatus.setText(String.format(
                resources.getString("status.currentImageDetectionsFormat"),
                result.distinctLabeledDetections(),
                result.trainingAnnotationCount()));

        var contribs = new java.util.LinkedHashMap<>(result.perClass());
        if (contribs.isEmpty() || sumContribution(contribs) <= 0.0) {
            currentImageChartPane.showPlaceholder(resources.getString("empty.noDetectionLabelsCurrent"));
            return;
        }

        // Inject MISSING placeholders for project classes the open image has
        // no labeled detections of. HighlightEvaluator picks them up via
        // their zero (count, total) and ChartPane renders them legend-only.
        var projectSummary = cache.summarize(null);
        for (var projKey : projectSummary.contributions().keySet()) {
            if (!contribs.containsKey(projKey)) {
                contribs.put(projKey,
                        new ContributionCalculator.ClassSummary(0.0, 0));
            }
        }

        var evaluation = HighlightEvaluator.evaluate(contribs, advanced.getHighlightRatio());
        currentImageChartPane.refresh(contribs, evaluation,
                advanced.isShowSliceLabels(),
                advanced.getOverColorHex(),
                advanced.getUnderColorHex());
    }

    private String imageDisplayName(ImageData<BufferedImage> data) {
        Project<BufferedImage> project = qupath.getProject();
        ProjectImageEntry<BufferedImage> entry = project == null ? null : project.getEntry(data);
        if (entry != null) {
            String n = entry.getImageName();
            if (n != null && !n.isBlank()) {
                return n;
            }
        }
        try {
            String n = data.getServer().getMetadata().getName();
            if (n != null && !n.isBlank()) {
                return n;
            }
        } catch (Exception ignored) {
            // Fall through.
        }
        return resources.getString("label.currentImageUnnamed");
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
        ClassDistributionDialog.ImageTypeFilter previous = typeCombo.getValue();
        List<ClassDistributionDialog.ImageTypeFilter> items = new ArrayList<>();
        items.add(ALL_TYPES_FILTER);
        Map<ImageData.ImageType, Integer> counts = cache.typeCounts();
        for (ImageData.ImageType t : ImageData.ImageType.values()) {
            if (t == ImageData.ImageType.UNSET) {
                continue;
            }
            int n = counts.getOrDefault(t, 0);
            String suffix = n > 0 ? " (" + n + ")" : "";
            items.add(new ClassDistributionDialog.ImageTypeFilter(t,
                    prettyTypeName(t) + suffix));
        }
        items.add(UNSET_FILTER);
        typeCombo.setItems(FXCollections.observableArrayList(items));
        if (previous != null) {
            for (var f : items) {
                if (java.util.Objects.equals(f.persistKey(), previous.persistKey())) {
                    typeCombo.getSelectionModel().select(f);
                    return;
                }
            }
        }
        typeCombo.getSelectionModel().select(ALL_TYPES_FILTER);
    }

    private void initialiseFilter() {
        String persisted = CDPreferences.getLastImageTypeFilter();
        ClassDistributionDialog.ImageTypeFilter chosen = null;
        if (persisted != null && !persisted.isEmpty()) {
            for (var f : typeCombo.getItems()) {
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

    private ClassDistributionDialog.ImageTypeFilter findFilterFor(ImageData.ImageType t) {
        for (var f : typeCombo.getItems()) {
            if (f.imageType() == t) {
                return f;
            }
        }
        return null;
    }

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
}
