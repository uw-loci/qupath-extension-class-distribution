package qupath.ext.classdistribution;

import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classdistribution.preferences.CDPreferences;
import qupath.ext.classdistribution.ui.ClassDistributionDialog;
import qupath.ext.classdistribution.ui.DetectionTrainingDialog;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;

import java.util.ResourceBundle;

/**
 * Entry point for the Class Distribution extension.
 *
 * <p>Registers a single menu item under {@code Extensions > Class Distribution}
 * that opens the {@link ClassDistributionDialog}. The dialog itself is a
 * single-instance non-modal {@link javafx.stage.Stage}; re-invoking the
 * menu raises the existing window.
 *
 * <p>Reference lift: menu-registration shape is verbatim from
 * {@code qupath-extension-confusion-matrix/.../ConfusionMatrixExtension.java}
 * lines 84-115.
 *
 * @author Mike Nelson
 */
public class ClassDistributionExtension implements QuPathExtension, GitHubProject {

    private static final Logger logger = LoggerFactory.getLogger(ClassDistributionExtension.class);

    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.classdistribution.strings");

    private static final String EXTENSION_NAME = resources.getString("name");
    private static final String EXTENSION_DESCRIPTION = resources.getString("description");
    private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.6.0");
    private static final GitHubRepo EXTENSION_REPOSITORY =
            GitHubRepo.create(EXTENSION_NAME, "MichaelSNelson", "qupath-extension-class-distribution");

    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getDescription() {
        return EXTENSION_DESCRIPTION;
    }

    @Override
    public Version getQuPathVersion() {
        return EXTENSION_QUPATH_VERSION;
    }

    @Override
    public GitHubRepo getRepository() {
        return EXTENSION_REPOSITORY;
    }

    @Override
    public void installExtension(QuPathGUI qupath) {
        logger.info("Installing extension: {}", EXTENSION_NAME);

        CDPreferences.installPreferences();

        Platform.runLater(() -> addMenuItems(qupath));
    }

    private void addMenuItems(QuPathGUI qupath) {
        var extensionMenu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);

        MenuItem showItem = new MenuItem(resources.getString("menu.show"));
        showItem.setOnAction(e -> {
            logger.info("Opening Class Distribution dialog");
            ClassDistributionDialog.showDialog(qupath);
        });
        // Note: JavaFX MenuItem does not natively support tooltips on hover.
        // The tooltip.menu.* resources are exposed only for the user guide;
        // hover help on a menu item is a known JavaFX limitation we accept.

        // Note: we deliberately do NOT bind disableProperty to imageData /
        // project. The dialog is useful even when no project is open
        // (it shows an explicit "Open a project to see class distribution."
        // placeholder), and the chart can keep rendering from the cache
        // after the user closes their image. Compare with
        // ConfusionMatrixExtension.java:91 which DOES require an open image.

        MenuItem showDetectionsItem = new MenuItem(resources.getString("menu.showDetections"));
        showDetectionsItem.setOnAction(e -> {
            logger.info("Opening Detection Training Distribution dialog");
            DetectionTrainingDialog.showDialog(qupath);
        });

        extensionMenu.getItems().addAll(showItem, showDetectionsItem);
        logger.info("Menu items added for extension: {}", EXTENSION_NAME);
    }
}
