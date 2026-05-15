package qupath.ext.classdistribution.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.util.ResourceBundle;

/**
 * Amber HBox that warns the user when the open image hierarchy has unsaved
 * changes (so the chart's live update reflects something not yet on disk).
 *
 * <p>Visibility is driven by {@link ImageData#isChanged()} on the open image.
 * When no image is open, the banner shows a different "no image is open"
 * message instead of being hidden.
 *
 * <p>The Save button calls
 * {@link ProjectImageEntry#saveImageData(ImageData)} on the entry that
 * corresponds to the currently open image (the same pattern the DL pixel
 * classifier's {@code TrainingWorkflow} uses to auto-save before
 * project-wide reads). If no project is open or the open image is not
 * tracked by the project, the Save button is disabled.
 *
 * <p>Reference lift: amber {@code #fff4cd} background and {@code #664d03}
 * text colour are taken verbatim from {@code 02_design.ui-ux-draft.md}
 * section 2 (item B).
 *
 * @author Mike Nelson
 */
public final class DirtyBanner extends HBox {

    private static final Logger logger = LoggerFactory.getLogger(DirtyBanner.class);

    private static final String STYLE_BACKGROUND =
            "-fx-background-color: #fff4cd; -fx-background-radius: 4; "
                    + "-fx-border-color: #f0d878; -fx-border-radius: 4; -fx-border-width: 1;";
    private static final String STYLE_TEXT = "-fx-text-fill: #664d03;";

    private final QuPathGUI qupath;
    private final ResourceBundle resources;
    private final Label label;
    private final Button saveButton;

    public DirtyBanner(QuPathGUI qupath, ResourceBundle resources) {
        super(8);
        this.qupath = qupath;
        this.resources = resources;
        setPadding(new Insets(8, 10, 8, 10));
        setAlignment(Pos.CENTER_LEFT);
        setStyle(STYLE_BACKGROUND);

        label = new Label(resources.getString("banner.dirty"));
        label.setStyle(STYLE_TEXT);
        label.setWrapText(true);
        // Install the tooltip on both the label and the banner HBox so the
        // hover text appears regardless of where the cursor lands.
        Tooltip dirtyTip = new Tooltip(resources.getString("tooltip.banner.dirty"));
        Tooltip.install(label, dirtyTip);
        Tooltip.install(this, dirtyTip);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        saveButton = new Button(resources.getString("banner.saveButton"));
        saveButton.setTooltip(new Tooltip(resources.getString("tooltip.banner.saveButton")));
        saveButton.setOnAction(e -> triggerSave());

        getChildren().addAll(label, spacer, saveButton);

        // Hidden by default; the dialog explicitly drives visibility.
        setVisible(false);
        setManaged(false);
    }

    /**
     * Updates banner visibility, label text, and Save-button enabled state
     * for the current open {@link ImageData}.
     *
     * <p>Call this on the FX thread.
     *
     * @param imageData the currently open image data, or null if none.
     */
    public void refresh(ImageData<BufferedImage> imageData) {
        if (!Platform.isFxApplicationThread()) {
            ImageData<BufferedImage> snapshot = imageData;
            Platform.runLater(() -> refresh(snapshot));
            return;
        }
        if (imageData == null) {
            // No image open: show an informational variant. Same amber
            // styling so the user notices, different text.
            label.setText(resources.getString("banner.noImage"));
            saveButton.setDisable(true);
            saveButton.setVisible(false);
            saveButton.setManaged(false);
            setVisible(true);
            setManaged(true);
            return;
        }
        boolean dirty = false;
        try {
            dirty = imageData.isChanged();
        } catch (Exception e) {
            // Defensive: tolerate a future ImageData implementation that
            // does not support the call.
            logger.debug("imageData.isChanged() threw {}", e.toString());
        }
        if (dirty) {
            label.setText(resources.getString("banner.dirty"));
            saveButton.setDisable(false);
            saveButton.setVisible(true);
            saveButton.setManaged(true);
            setVisible(true);
            setManaged(true);
        } else {
            setVisible(false);
            setManaged(false);
        }
    }

    /**
     * Convenience: ensure the banner is hidden (e.g. on dialog close).
     */
    public void clear() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::clear);
            return;
        }
        setVisible(false);
        setManaged(false);
    }

    /**
     * Saves the currently open image's data via its project entry, then
     * refreshes this banner so the user sees the dirty flag clear.
     *
     * <p>If the open image is not tracked by a project (e.g. opened ad-hoc
     * from disk), saving is not supported and a notification is shown.
     */
    private void triggerSave() {
        if (qupath == null) {
            return;
        }
        ImageData<BufferedImage> data = qupath.getImageData();
        if (data == null) {
            return;
        }
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            Dialogs.showInfoNotification(
                    resources.getString("dialog.title"),
                    resources.getString("banner.save.noProject"));
            return;
        }
        ProjectImageEntry<BufferedImage> entry = project.getEntry(data);
        if (entry == null) {
            Dialogs.showInfoNotification(
                    resources.getString("dialog.title"),
                    resources.getString("banner.save.notInProject"));
            return;
        }
        try {
            entry.saveImageData(data);
            logger.info("Saved image data for {}", entry.getImageName());
        } catch (Exception e) {
            logger.warn("Save Image failed for {}: {}", entry.getImageName(), e.toString());
            Dialogs.showErrorNotification(
                    resources.getString("dialog.title"),
                    resources.getString("banner.save.failed"));
        }
        refresh(data);
    }
}
