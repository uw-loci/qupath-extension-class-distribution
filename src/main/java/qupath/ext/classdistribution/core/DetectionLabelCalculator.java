package qupath.ext.classdistribution.core;

import qupath.ext.classdistribution.core.ContributionCalculator.ClassKey;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassSummary;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Counts how many detections would be labeled with each class given the
 * current set of training annotations -- mirroring exactly what QuPath's
 * Object Classifier command does at training time.
 *
 * <p>Two annotation methods are recognised, per
 * {@code qupath-qpsc-dev/.../ObjectClassifierCommand.java} lines 692-733
 * (the source of truth for the training pipeline):
 * <ul>
 *   <li><b>Area annotations</b> (closed ROIs with a class set) -- every
 *       detection inside the ROI is labeled with the annotation's class
 *       via {@link PathObjectHierarchy#getAllDetectionsForROI(ROI)}.</li>
 *   <li><b>Point annotations</b> (Counting-tool points with a class set) --
 *       for each point, detections at that location are labeled via
 *       {@link PathObjectTools#getObjectsForLocation(PathObjectHierarchy,
 *       double, double, int, int, double)}.</li>
 * </ul>
 *
 * <p>Polyline / line annotations are not handled specially -- they don't
 * meaningfully label detections in QuPath's pipeline either.
 *
 * <p>A single detection labeled by annotations of two different classes
 * is counted under BOTH classes -- this is deliberate, because such
 * ambiguity is itself information for the user (the sum of per-class
 * counts will exceed the number of distinct labeled detections, which is
 * a useful signal).
 *
 * @author Mike Nelson
 */
public final class DetectionLabelCalculator {

    private DetectionLabelCalculator() {
        // Utility class - prevent instantiation
    }

    /**
     * Result of aggregating labeled detections across an image.
     */
    public static final class Result {
        private final Map<ClassKey, ClassSummary> perClass;
        private final int distinctLabeledDetections;
        private final int trainingAnnotationCount;

        public Result(Map<ClassKey, ClassSummary> perClass,
                      int distinctLabeledDetections,
                      int trainingAnnotationCount) {
            this.perClass = perClass == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(perClass);
            this.distinctLabeledDetections = distinctLabeledDetections;
            this.trainingAnnotationCount = trainingAnnotationCount;
        }

        public Map<ClassKey, ClassSummary> perClass() {
            return perClass;
        }

        /**
         * Number of detections that received at least one class label.
         * Less than or equal to the sum of per-class counts (a detection
         * labeled by two different-class annotations is counted twice in
         * the per-class map but only once here).
         */
        public int distinctLabeledDetections() {
            return distinctLabeledDetections;
        }

        /**
         * Number of annotation objects that contributed at least one
         * detection label (i.e. annotations with a class AND a ROI that
         * enclose or touch at least one detection).
         */
        public int trainingAnnotationCount() {
            return trainingAnnotationCount;
        }
    }

    /**
     * Aggregates labeled detections from {@code hierarchy}. Never null,
     * even if the hierarchy is empty.
     *
     * <p>{@link ClassSummary#total()} is set equal to {@link ClassSummary#count()}
     * so the pie chart's slice size scales with the number of labeled
     * detections per class.
     */
    public static Result aggregate(PathObjectHierarchy hierarchy) {
        if (hierarchy == null) {
            return new Result(new LinkedHashMap<>(), 0, 0);
        }
        Map<ClassKey, Set<PathObject>> perClass = new LinkedHashMap<>();
        Set<PathObject> distinct = new HashSet<>();
        int trainingAnnotationCount = 0;

        for (PathObject annotation : hierarchy.getAnnotationObjects()) {
            if (annotation == null || !annotation.isAnnotation()) {
                continue;
            }
            if (annotation.getPathClass() == null) {
                continue;
            }
            ROI roi = annotation.getROI();
            if (roi == null) {
                continue;
            }
            Collection<PathObject> labeled;
            if (roi.isPoint()) {
                labeled = new HashSet<>();
                for (Point2 p : roi.getAllPoints()) {
                    Collection<PathObject> at = PathObjectTools.getObjectsForLocation(
                            hierarchy, p.getX(), p.getY(),
                            roi.getZ(), roi.getT(), -1);
                    if (at != null) {
                        for (PathObject o : at) {
                            if (o != null && o.isDetection()) {
                                labeled.add(o);
                            }
                        }
                    }
                }
            } else if (roi.isArea()) {
                Collection<PathObject> all = hierarchy.getAllDetectionsForROI(roi);
                labeled = new HashSet<>();
                if (all != null) {
                    for (PathObject o : all) {
                        if (o != null && o.isDetection()) {
                            labeled.add(o);
                        }
                    }
                }
            } else {
                // Line / polyline: not a labeling annotation. Skip.
                continue;
            }
            if (labeled.isEmpty()) {
                continue;
            }
            trainingAnnotationCount++;
            ClassKey key = ContributionCalculator.keyFor(annotation.getPathClass());
            Set<PathObject> bucket = perClass.computeIfAbsent(key, k -> new HashSet<>());
            bucket.addAll(labeled);
            distinct.addAll(labeled);
        }

        Map<ClassKey, ClassSummary> out = new LinkedHashMap<>();
        for (Map.Entry<ClassKey, Set<PathObject>> e : perClass.entrySet()) {
            int n = e.getValue().size();
            // total == count so the chart slice size scales with labeled
            // detection count. There is no separate "area" measure for
            // detection labeling.
            out.put(e.getKey(), new ClassSummary((double) n, n));
        }
        return new Result(out, distinct.size(), trainingAnnotationCount);
    }
}
