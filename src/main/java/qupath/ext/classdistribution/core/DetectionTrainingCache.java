package qupath.ext.classdistribution.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassKey;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassSummary;
import qupath.lib.images.ImageData;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Per-image cache of labeled-detection counts plus the project-wide
 * poll harness. Mirrors {@link ProjectAnnotationCache} but the per-image
 * aggregator is {@link DetectionLabelCalculator#aggregate(PathObjectHierarchy)}.
 *
 * @author Mike Nelson
 */
public final class DetectionTrainingCache {

    private static final Logger logger = LoggerFactory.getLogger(DetectionTrainingCache.class);

    public static final class ImageRow {
        private final ImageData.ImageType imageType;
        private final String entryName;
        private final Map<ClassKey, ClassSummary> contributions;
        private final int distinctLabeledDetections;
        private final int trainingAnnotationCount;

        public ImageRow(ImageData.ImageType imageType,
                        String entryName,
                        Map<ClassKey, ClassSummary> contributions,
                        int distinctLabeledDetections,
                        int trainingAnnotationCount) {
            this.imageType = imageType;
            this.entryName = entryName == null ? "" : entryName;
            this.contributions = new LinkedHashMap<>(contributions == null
                    ? java.util.Map.of() : contributions);
            this.distinctLabeledDetections = distinctLabeledDetections;
            this.trainingAnnotationCount = trainingAnnotationCount;
        }

        public ImageData.ImageType imageType() {
            return imageType;
        }

        public String entryName() {
            return entryName;
        }

        public Map<ClassKey, ClassSummary> contributions() {
            return contributions;
        }

        public int distinctLabeledDetections() {
            return distinctLabeledDetections;
        }

        public int trainingAnnotationCount() {
            return trainingAnnotationCount;
        }
    }

    public static final class FilterSummary {
        private final Map<ClassKey, ClassSummary> contributions;
        private final int distinctLabeledDetections;
        private final int trainingAnnotationCount;
        private final int imageCount;

        public FilterSummary(Map<ClassKey, ClassSummary> contributions,
                             int distinctLabeledDetections,
                             int trainingAnnotationCount,
                             int imageCount) {
            this.contributions = contributions == null
                    ? new LinkedHashMap<>() : contributions;
            this.distinctLabeledDetections = distinctLabeledDetections;
            this.trainingAnnotationCount = trainingAnnotationCount;
            this.imageCount = imageCount;
        }

        public Map<ClassKey, ClassSummary> contributions() {
            return contributions;
        }

        public int distinctLabeledDetections() {
            return distinctLabeledDetections;
        }

        public int trainingAnnotationCount() {
            return trainingAnnotationCount;
        }

        public int imageCount() {
            return imageCount;
        }
    }

    private final Map<ProjectImageEntry<?>, ImageRow> rows = new ConcurrentHashMap<>();

    public DetectionTrainingCache() {
        // empty
    }

    public void clear() {
        rows.clear();
    }

    public ImageRow get(ProjectImageEntry<?> entry) {
        if (entry == null) {
            return null;
        }
        return rows.get(entry);
    }

    public void put(ProjectImageEntry<?> entry, ImageRow row) {
        if (entry == null || row == null) {
            return;
        }
        rows.put(entry, row);
    }

    public int size() {
        return rows.size();
    }

    /**
     * Computes the open image's per-class labeled-detection counts and
     * stores them under {@code entry}. No-op if {@code hierarchy} is null
     * or {@code entry} is null.
     */
    public ImageRow updateOpen(ProjectImageEntry<?> entry,
                               ImageData.ImageType imageType,
                               PathObjectHierarchy hierarchy) {
        if (hierarchy == null) {
            return null;
        }
        DetectionLabelCalculator.Result result = DetectionLabelCalculator.aggregate(hierarchy);
        ImageRow row = new ImageRow(
                imageType,
                entry == null ? "(open image)" : entry.getImageName(),
                result.perClass(),
                result.distinctLabeledDetections(),
                result.trainingAnnotationCount());
        if (entry != null) {
            rows.put(entry, row);
        }
        return row;
    }

    public void repollAll(Project<?> project,
                          Consumer<Double> progress) {
        repollAll(project, progress, null);
    }

    /**
     * Walks every image in the project on the calling thread, replacing
     * every row in the cache. See {@link ProjectAnnotationCache#repollAll}
     * for the shared cancellation / per-entry exception-swallowing pattern.
     */
    public void repollAll(Project<?> project,
                          Consumer<Double> progress,
                          BooleanSupplier cancelled) {
        clear();
        if (project == null) {
            if (progress != null) {
                progress.accept(1.0);
            }
            return;
        }
        List<? extends ProjectImageEntry<?>> entries = project.getImageList();
        int total = entries.size();
        int processed = 0;
        for (ProjectImageEntry<?> entry : entries) {
            if (isCancelled(cancelled)) {
                logger.info("Detection training refresh cancelled at {} of {}",
                        processed, total);
                break;
            }
            try {
                @SuppressWarnings("unchecked")
                ImageData<BufferedImage> data =
                        (ImageData<BufferedImage>) entry.readImageData();
                if (data != null) {
                    PathObjectHierarchy h = data.getHierarchy();
                    DetectionLabelCalculator.Result result =
                            DetectionLabelCalculator.aggregate(h);
                    ImageRow row = new ImageRow(
                            data.getImageType(),
                            entry.getImageName(),
                            result.perClass(),
                            result.distinctLabeledDetections(),
                            result.trainingAnnotationCount());
                    rows.put(entry, row);
                }
            } catch (Exception e) {
                logger.warn("Skipping project entry {} during detection refresh: {}",
                        safeName(entry), e.toString());
            }
            processed++;
            if (progress != null) {
                progress.accept((double) processed / Math.max(1, total));
            }
        }
        logger.info("Detection training refresh complete: {} of {} images cached",
                rows.size(), total);
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        return cancelled != null && cancelled.getAsBoolean();
    }

    public FilterSummary summarize(ImageData.ImageType filter) {
        Map<ClassKey, ClassSummary> total = new LinkedHashMap<>();
        int detections = 0;
        int trainingAnnotations = 0;
        int images = 0;
        for (ImageRow row : rows.values()) {
            if (!matches(row.imageType(), filter)) {
                continue;
            }
            total = ContributionCalculator.sum(total, row.contributions());
            detections += row.distinctLabeledDetections();
            trainingAnnotations += row.trainingAnnotationCount();
            images++;
        }
        return new FilterSummary(total, detections, trainingAnnotations, images);
    }

    public Map<ImageData.ImageType, Integer> typeCounts() {
        Map<ImageData.ImageType, Integer> counts = new LinkedHashMap<>();
        for (ImageData.ImageType t : ImageData.ImageType.values()) {
            counts.put(t, 0);
        }
        for (ImageRow row : rows.values()) {
            ImageData.ImageType t = row.imageType() == null
                    ? ImageData.ImageType.UNSET : row.imageType();
            counts.merge(t, 1, Integer::sum);
        }
        return counts;
    }

    public ImageData.ImageType mostCommonNonUnsetType() {
        Map<ImageData.ImageType, Integer> counts = typeCounts();
        ImageData.ImageType best = null;
        int bestCount = 0;
        for (Map.Entry<ImageData.ImageType, Integer> e : counts.entrySet()) {
            if (e.getKey() == ImageData.ImageType.UNSET) {
                continue;
            }
            if (e.getValue() != null && e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return bestCount > 0 ? best : null;
    }

    private static boolean matches(ImageData.ImageType rowType, ImageData.ImageType filter) {
        if (filter == null) {
            return true;
        }
        if (filter == ImageData.ImageType.UNSET) {
            return rowType == null || rowType == ImageData.ImageType.UNSET;
        }
        return filter == rowType;
    }

    private static String safeName(ProjectImageEntry<?> entry) {
        try {
            return entry == null ? "(null)" : entry.getImageName();
        } catch (Exception e) {
            return "(unnamed)";
        }
    }
}
