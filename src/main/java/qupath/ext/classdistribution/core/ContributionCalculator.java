package qupath.ext.classdistribution.core;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure functions that turn a stream of {@link PathObject} annotations into
 * a per-class contribution map.
 *
 * <p>For closed annotations the contribution is the ROI's pixel area.
 * For polylines and lines the contribution is {@code length * polylineWidthPx}
 * so that the value is dimensionally comparable to a pixel area. Point ROIs
 * and any other geometry types contribute zero (intentional; see the
 * Phase 0 feasibility document section 5).
 *
 * <p>Annotations whose {@code PathClass} is {@code null} are aggregated under
 * a single sentinel string {@link #UNCLASSIFIED_KEY} so the chart can show a
 * single "Unclassified" bucket. The bucket is exposed as a {@code String}
 * key in the returned map (paired with a {@code null} {@link PathClass} value
 * in {@link ClassKey#pathClass()}) so a user-defined class literally named
 * "Unclassified" never collides with the bucket label.
 *
 * <p>Math reference: the polyline-as-area formula is the same one the DL
 * pixel classifier uses for its rebalance computation
 * ({@code qupath-extension-dl-pixel-classifier/.../TrainingDialog.java}
 * "For lines, pixel coverage is estimated from line length x stroke width").
 *
 * <p>Phase 0 design call: raw pixel quantities are summed across all images
 * regardless of their pixel-size calibration. This is intake's literal
 * reading; mixed-pixel-size projects produce slightly misleading totals.
 * Documented as a known limitation.
 *
 * @author Mike Nelson
 */
public final class ContributionCalculator {

    /** Display label for the bucket of annotations with no {@link PathClass}. */
    public static final String UNCLASSIFIED_LABEL = "Unclassified";

    /** Sentinel key used in returned maps for the null-class bucket. */
    public static final ClassKey UNCLASSIFIED_KEY = new ClassKey(null, UNCLASSIFIED_LABEL);

    private ContributionCalculator() {
        // Utility class - prevent instantiation
    }

    /**
     * Computes one annotation's contribution to the chart.
     *
     * @param roi the region of interest; must not be null.
     * @param polylineWidthPx pixel width to assume for polylines / lines;
     *                        clamped to a minimum of 1.
     * @return area in px for closed annotations, length * polylineWidthPx
     *         for lines, 0 for everything else (points, empty ROIs).
     */
    public static double contribution(ROI roi, int polylineWidthPx) {
        if (roi == null) {
            return 0.0;
        }
        int width = Math.max(1, polylineWidthPx);
        if (roi.isArea()) {
            // Closed annotations: rectangle, ellipse, polygon, area, geometry...
            double a = roi.getArea();
            return Double.isFinite(a) ? Math.max(0.0, a) : 0.0;
        }
        if (roi.isLine()) {
            double l = roi.getLength();
            return Double.isFinite(l) ? Math.max(0.0, l) * width : 0.0;
        }
        // Point ROIs and any other geometry types: not represented on the chart.
        return 0.0;
    }

    /**
     * Aggregates a collection of annotations into a per-class contribution
     * total and a per-class object count.
     *
     * <p>The map is a {@link LinkedHashMap} so iteration order is the
     * order in which classes were first encountered. Slice ordering at the
     * UI layer is the chart pane's responsibility.
     *
     * @param annotations the annotations to aggregate; may be empty but not
     *                    null. Detections are ignored if they leak in.
     * @param polylineWidthPx polyline width to use for line ROIs.
     * @return per-class summary; never null.
     */
    public static Map<ClassKey, ClassSummary> aggregate(
            Iterable<PathObject> annotations, int polylineWidthPx) {
        Objects.requireNonNull(annotations, "annotations");
        Map<ClassKey, ClassSummary> out = new LinkedHashMap<>();
        if (polylineWidthPx < 1) {
            polylineWidthPx = 1;
        }
        for (PathObject obj : annotations) {
            if (obj == null) {
                continue;
            }
            // We only chart annotations. A PathObjectHierarchy.getAnnotationObjects()
            // call already filters this, but defend against being handed a
            // mixed iterable.
            if (!obj.isAnnotation()) {
                continue;
            }
            ROI roi = obj.getROI();
            double c = contribution(roi, polylineWidthPx);
            ClassKey key = keyFor(obj.getPathClass());
            ClassSummary s = out.get(key);
            if (s == null) {
                s = new ClassSummary(0.0, 0);
                out.put(key, s);
            }
            // Always increment the count, even if contribution is zero
            // (a Point annotation still says "something is here for class X").
            s.add(c, 1);
        }
        return out;
    }

    /**
     * Returns the {@link ClassKey} for a {@link PathClass}, mapping
     * {@code null} to the {@link #UNCLASSIFIED_KEY} sentinel.
     */
    public static ClassKey keyFor(PathClass pathClass) {
        if (pathClass == null) {
            return UNCLASSIFIED_KEY;
        }
        return new ClassKey(pathClass, pathClass.toString());
    }

    /**
     * Sums two per-class summary maps in place, returning a fresh result map.
     * Used by {@link ProjectAnnotationCache} to combine per-image rows into
     * a project-wide total before handing it to the chart pane.
     */
    public static Map<ClassKey, ClassSummary> sum(
            Map<ClassKey, ClassSummary> a, Map<ClassKey, ClassSummary> b) {
        Map<ClassKey, ClassSummary> out = new LinkedHashMap<>(a == null ? Map.of() : a);
        if (b == null) {
            return out;
        }
        for (Map.Entry<ClassKey, ClassSummary> e : b.entrySet()) {
            ClassSummary existing = out.get(e.getKey());
            if (existing == null) {
                out.put(e.getKey(), new ClassSummary(e.getValue().total(), e.getValue().count()));
            } else {
                existing.add(e.getValue().total(), e.getValue().count());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------

    /**
     * Identity for one bucket on the chart. The {@code label} is what the
     * UI shows; the {@code pathClass} is the underlying QuPath object (or
     * {@code null} for the Unclassified bucket).
     *
     * <p>Equality is by {@code label} so that multiple {@link PathClass}
     * instances with the same {@code toString()} (e.g. across project
     * reloads) collapse into a single chart slice.
     */
    public static final class ClassKey {
        private final PathClass pathClass;
        private final String label;

        public ClassKey(PathClass pathClass, String label) {
            this.pathClass = pathClass;
            this.label = Objects.requireNonNullElse(label, "");
        }

        public PathClass pathClass() {
            return pathClass;
        }

        public String label() {
            return label;
        }

        public boolean isUnclassified() {
            return pathClass == null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClassKey other)) return false;
            return label.equals(other.label);
        }

        @Override
        public int hashCode() {
            return label.hashCode();
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Mutable accumulator for the total contribution and object count of one
     * class. Mutability is internal to {@link ContributionCalculator}; the
     * UI receives an immutable view through getters.
     */
    public static final class ClassSummary {
        private double total;
        private int count;

        public ClassSummary(double total, int count) {
            this.total = total;
            this.count = count;
        }

        public double total() {
            return total;
        }

        public int count() {
            return count;
        }

        void add(double moreTotal, int moreCount) {
            this.total += moreTotal;
            this.count += moreCount;
        }

        @Override
        public String toString() {
            return String.format("ClassSummary[total=%.3f, count=%d]", total, count);
        }
    }
}
