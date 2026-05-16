package qupath.ext.classdistribution.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Manages persistent preferences for the Class Distribution extension.
 * <p>
 * Single-namespace {@code PathPrefs} class modelled directly on
 * {@code qupath-extension-confusion-matrix}'s {@code CMPreferences}
 * (see Phase 1 design document, section 1, "Reference-extension code lifts").
 * Eleven keys total; see {@code 02_design.ui-ux-draft.md} section 4 for
 * the canonical table.
 *
 * <p>All keys live under the {@code classdistribution.} namespace prefix to
 * avoid collisions with other QuPath extensions.
 *
 * @author Mike Nelson
 */
public final class CDPreferences {

    private static final Logger logger = LoggerFactory.getLogger(CDPreferences.class);

    private static final String PREFIX = "classdistribution.";

    // Defaults (see ui-ux-draft section 4 table).
    // Highlight ratio: 2.0 means "flag a class as OVER when its share is
    // at least 2x the median class share, or UNDER when its share is at
    // most 0.5x." Symmetric in log-space, so it flags small under-
    // represented classes the same way it flags large over-represented
    // ones -- critical for distributions with a small median.
    // Over/Under colours: Okabe-Ito blue / vermilion (Phase 5 grad-student
    // feedback). Standard colourblind-safe "good vs bad" pair; replaces the
    // earlier cyan / red pair which was problematic for protanopia /
    // deuteranopia. Both ColorPickers remain user-overridable.
    private static final int DEFAULT_POLYLINE_WIDTH_PX = 1;
    private static final double DEFAULT_HIGHLIGHT_RATIO = 2.0;
    private static final boolean DEFAULT_SHOW_SLICE_LABELS = true;
    private static final boolean DEFAULT_SIDE_BY_SIDE = false;
    private static final String DEFAULT_LAST_IMAGE_TYPE_FILTER = "";
    private static final String DEFAULT_OVER_COLOR_HEX = "#0072B2";
    private static final String DEFAULT_UNDER_COLOR_HEX = "#D55E00";
    private static final boolean DEFAULT_ADVANCED_SECTION_EXPANDED = false;
    private static final double DEFAULT_DIALOG_X = Double.NaN;
    private static final double DEFAULT_DIALOG_Y = Double.NaN;
    private static final double DEFAULT_DIALOG_WIDTH = 520.0;
    private static final double DEFAULT_DIALOG_HEIGHT = 620.0;

    // Properties (initialised lazily by installPreferences()).
    private static IntegerProperty polylineWidthPxProperty;
    private static DoubleProperty highlightRatioProperty;
    private static BooleanProperty showSliceLabelsProperty;
    private static StringProperty lastImageTypeFilterProperty;
    private static StringProperty overColorHexProperty;
    private static StringProperty underColorHexProperty;
    private static BooleanProperty advancedSectionExpandedProperty;
    private static BooleanProperty sideBySideProperty;
    private static DoubleProperty dialogXProperty;
    private static DoubleProperty dialogYProperty;
    private static DoubleProperty dialogWidthProperty;
    private static DoubleProperty dialogHeightProperty;

    private CDPreferences() {
        // Utility class - prevent instantiation
    }

    /**
     * Installs the persistent preferences. Should be called once during
     * extension initialisation, before any UI binds to the properties.
     */
    public static void installPreferences() {
        if (polylineWidthPxProperty != null) {
            // Already installed; idempotent.
            return;
        }
        logger.info("Installing Class Distribution preferences");

        polylineWidthPxProperty = PathPrefs.createPersistentPreference(
                PREFIX + "polylineWidthPx", DEFAULT_POLYLINE_WIDTH_PX);
        // Key changed in v0.1.3 because the algorithm became multiplicative
        // (ratio against the global median rather than absolute percentage
        // points). Old saved values represented different units and would
        // misbehave; the rename forces a clean default.
        highlightRatioProperty = PathPrefs.createPersistentPreference(
                PREFIX + "highlightRatio", DEFAULT_HIGHLIGHT_RATIO);
        showSliceLabelsProperty = PathPrefs.createPersistentPreference(
                PREFIX + "showSliceLabels", DEFAULT_SHOW_SLICE_LABELS);
        lastImageTypeFilterProperty = PathPrefs.createPersistentPreference(
                PREFIX + "lastImageTypeFilter", DEFAULT_LAST_IMAGE_TYPE_FILTER);
        overColorHexProperty = PathPrefs.createPersistentPreference(
                PREFIX + "overColorHex", DEFAULT_OVER_COLOR_HEX);
        underColorHexProperty = PathPrefs.createPersistentPreference(
                PREFIX + "underColorHex", DEFAULT_UNDER_COLOR_HEX);
        advancedSectionExpandedProperty = PathPrefs.createPersistentPreference(
                PREFIX + "advancedSectionExpanded", DEFAULT_ADVANCED_SECTION_EXPANDED);
        sideBySideProperty = PathPrefs.createPersistentPreference(
                PREFIX + "sideBySide", DEFAULT_SIDE_BY_SIDE);
        dialogXProperty = PathPrefs.createPersistentPreference(
                PREFIX + "dialogX", DEFAULT_DIALOG_X);
        dialogYProperty = PathPrefs.createPersistentPreference(
                PREFIX + "dialogY", DEFAULT_DIALOG_Y);
        dialogWidthProperty = PathPrefs.createPersistentPreference(
                PREFIX + "dialogWidth", DEFAULT_DIALOG_WIDTH);
        dialogHeightProperty = PathPrefs.createPersistentPreference(
                PREFIX + "dialogHeight", DEFAULT_DIALOG_HEIGHT);

        logger.info("Class Distribution preferences installed");
    }

    // ----------------------------------------------------------------
    // Property accessors (for UI binding)
    // ----------------------------------------------------------------

    public static IntegerProperty polylineWidthPxProperty() {
        return polylineWidthPxProperty;
    }

    public static DoubleProperty highlightRatioProperty() {
        return highlightRatioProperty;
    }

    public static BooleanProperty showSliceLabelsProperty() {
        return showSliceLabelsProperty;
    }

    public static StringProperty lastImageTypeFilterProperty() {
        return lastImageTypeFilterProperty;
    }

    public static StringProperty overColorHexProperty() {
        return overColorHexProperty;
    }

    public static StringProperty underColorHexProperty() {
        return underColorHexProperty;
    }

    public static BooleanProperty advancedSectionExpandedProperty() {
        return advancedSectionExpandedProperty;
    }

    public static BooleanProperty sideBySideProperty() {
        return sideBySideProperty;
    }

    public static DoubleProperty dialogXProperty() {
        return dialogXProperty;
    }

    public static DoubleProperty dialogYProperty() {
        return dialogYProperty;
    }

    public static DoubleProperty dialogWidthProperty() {
        return dialogWidthProperty;
    }

    public static DoubleProperty dialogHeightProperty() {
        return dialogHeightProperty;
    }

    // ----------------------------------------------------------------
    // Convenience getters / setters
    // ----------------------------------------------------------------

    public static int getPolylineWidthPx() {
        return polylineWidthPxProperty != null
                ? polylineWidthPxProperty.get() : DEFAULT_POLYLINE_WIDTH_PX;
    }

    public static void setPolylineWidthPx(int v) {
        if (polylineWidthPxProperty != null) {
            polylineWidthPxProperty.set(v);
        }
    }

    public static double getHighlightRatio() {
        return highlightRatioProperty != null
                ? highlightRatioProperty.get() : DEFAULT_HIGHLIGHT_RATIO;
    }

    public static void setHighlightRatio(double v) {
        if (highlightRatioProperty != null) {
            highlightRatioProperty.set(v);
        }
    }

    public static boolean isShowSliceLabels() {
        return showSliceLabelsProperty != null
                ? showSliceLabelsProperty.get() : DEFAULT_SHOW_SLICE_LABELS;
    }

    public static void setShowSliceLabels(boolean v) {
        if (showSliceLabelsProperty != null) {
            showSliceLabelsProperty.set(v);
        }
    }

    public static String getLastImageTypeFilter() {
        return lastImageTypeFilterProperty != null
                ? lastImageTypeFilterProperty.get() : DEFAULT_LAST_IMAGE_TYPE_FILTER;
    }

    public static void setLastImageTypeFilter(String v) {
        if (lastImageTypeFilterProperty != null) {
            lastImageTypeFilterProperty.set(v == null ? "" : v);
        }
    }

    public static String getOverColorHex() {
        return overColorHexProperty != null
                ? overColorHexProperty.get() : DEFAULT_OVER_COLOR_HEX;
    }

    public static void setOverColorHex(String hex) {
        if (overColorHexProperty != null) {
            overColorHexProperty.set(hex);
        }
    }

    public static String getUnderColorHex() {
        return underColorHexProperty != null
                ? underColorHexProperty.get() : DEFAULT_UNDER_COLOR_HEX;
    }

    public static void setUnderColorHex(String hex) {
        if (underColorHexProperty != null) {
            underColorHexProperty.set(hex);
        }
    }

    public static boolean isAdvancedSectionExpanded() {
        return advancedSectionExpandedProperty != null
                ? advancedSectionExpandedProperty.get() : DEFAULT_ADVANCED_SECTION_EXPANDED;
    }

    public static void setAdvancedSectionExpanded(boolean v) {
        if (advancedSectionExpandedProperty != null) {
            advancedSectionExpandedProperty.set(v);
        }
    }

    public static boolean isSideBySide() {
        return sideBySideProperty != null
                ? sideBySideProperty.get() : DEFAULT_SIDE_BY_SIDE;
    }

    public static void setSideBySide(boolean v) {
        if (sideBySideProperty != null) {
            sideBySideProperty.set(v);
        }
    }

    public static double getDialogX() {
        return dialogXProperty != null ? dialogXProperty.get() : DEFAULT_DIALOG_X;
    }

    public static void setDialogX(double v) {
        if (dialogXProperty != null) {
            dialogXProperty.set(v);
        }
    }

    public static double getDialogY() {
        return dialogYProperty != null ? dialogYProperty.get() : DEFAULT_DIALOG_Y;
    }

    public static void setDialogY(double v) {
        if (dialogYProperty != null) {
            dialogYProperty.set(v);
        }
    }

    public static double getDialogWidth() {
        return dialogWidthProperty != null ? dialogWidthProperty.get() : DEFAULT_DIALOG_WIDTH;
    }

    public static void setDialogWidth(double v) {
        if (dialogWidthProperty != null) {
            dialogWidthProperty.set(v);
        }
    }

    public static double getDialogHeight() {
        return dialogHeightProperty != null ? dialogHeightProperty.get() : DEFAULT_DIALOG_HEIGHT;
    }

    public static void setDialogHeight(double v) {
        if (dialogHeightProperty != null) {
            dialogHeightProperty.set(v);
        }
    }
}
