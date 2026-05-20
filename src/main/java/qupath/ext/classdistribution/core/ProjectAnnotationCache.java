package qupath.ext.classdistribution.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassKey;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassSummary;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Per-image cache of annotation contributions plus the load harness for
 * the project-wide poll.
 *
 * <p>Keying: by {@link ProjectImageEntry} identity. Per Phase 0 feasibility
 * section 4 ("cache key"), this matches the {@code confusion-matrix}
 * pattern. Across {@code project.getImageList()} calls within a single
 * QuPath session, entry identity is stable. Across sessions identity is
 * not preserved, but the cache is never serialised; we re-poll on each
 * dialog open.
 *
 * <p>Threading:
 * <ul>
 *   <li>Reads happen on the JavaFX thread (via the dialog).</li>
 *   <li>Writes from a project poll happen on a background thread inside
 *       {@link #repollAll(Project, int, Consumer)}.</li>
 *   <li>Live updates from the open hierarchy happen via
 *       {@link #updateOpen(ProjectImageEntry, ImageData.ImageType, PathObjectHierarchy, int)},
 *       called on the FX thread by the dialog.</li>
 * </ul>
 * The cache is a {@link ConcurrentHashMap} so concurrent read / write across
 * threads is well-defined.
 *
 * <p>Reference lift: project-wide load harness modelled on
 * {@code qupath-extension-confusion-matrix/.../ProjectClassDiscovery.java}
 * (lines 113-155) -- catch-and-log per-entry exceptions, surface progress
 * via a {@code Consumer<Double>}.
 *
 * <p>Image-data load policy: this class reads the full {@link ImageData}
 * via {@link ProjectImageEntry#readImageData()} for each entry during a
 * project poll. The cheaper {@link ProjectImageEntry#readHierarchy()}
 * exists in QuPath 0.6.x, but the chart also needs the per-entry
 * {@link qupath.lib.images.ImageData.ImageType} for the type-filter
 * dropdown, and there is no light-weight accessor for that. Reading the
 * full image data once at poll time and caching the snapshot keeps
 * subsequent re-renders cheap.
 *
 * @author Mike Nelson
 */
public final class ProjectAnnotationCache {

    private static final Logger logger = LoggerFactory.getLogger(ProjectAnnotationCache.class);

    /**
     * One row of the cache: the per-class contribution map, the count of
     * annotations seen, the {@link ImageData.ImageType} (may be null for
     * UNSET / missing), and the entry's display name.
     */
    public static final class ImageRow {
        private final ImageData.ImageType imageType;
        private final String entryName;
        private final Map<ClassKey, ClassSummary> contributions;
        private final int annotationCount;

        public ImageRow(ImageData.ImageType imageType,
                        String entryName,
                        Map<ClassKey, ClassSummary> contributions,
                        int annotationCount) {
            this.imageType = imageType;
            this.entryName = entryName == null ? "" : entryName;
            this.contributions = new LinkedHashMap<>(contributions == null
                    ? java.util.Map.of() : contributions);
            this.annotationCount = annotationCount;
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

        public int annotationCount() {
            return annotationCount;
        }
    }

    /**
     * Aggregate result of {@link #summarize(ImageData.ImageType)}: the
     * per-class total, the number of annotations across all matching
     * images, and the number of matching images.
     */
    public static final class FilterSummary {
        private final Map<ClassKey, ClassSummary> contributions;
        private final int annotationCount;
        private final int imageCount;

        public FilterSummary(Map<ClassKey, ClassSummary> contributions,
                             int annotationCount,
                             int imageCount) {
            this.contributions = contributions == null
                    ? new LinkedHashMap<>() : contributions;
            this.annotationCount = annotationCount;
            this.imageCount = imageCount;
        }

        public Map<ClassKey, ClassSummary> contributions() {
            return contributions;
        }

        public int annotationCount() {
            return annotationCount;
        }

        public int imageCount() {
            return imageCount;
        }
    }

    private final Map<ProjectImageEntry<?>, ImageRow> rows = new ConcurrentHashMap<>();

    public ProjectAnnotationCache() {
        // empty
    }

    /**
     * Empties the cache. Called when the project changes or the user
     * clicks Re-poll.
     */
    public void clear() {
        rows.clear();
    }

    /**
     * Returns the row for {@code entry}, or null if no row is cached.
     */
    public ImageRow get(ProjectImageEntry<?> entry) {
        if (entry == null) {
            return null;
        }
        return rows.get(entry);
    }

    /**
     * Replaces the row for {@code entry}.
     */
    public void put(ProjectImageEntry<?> entry, ImageRow row) {
        if (entry == null || row == null) {
            return;
        }
        rows.put(entry, row);
    }

    /**
     * Total number of cached rows.
     */
    public int size() {
        return rows.size();
    }

    /**
     * Returns the cached rows in project-list order, skipping any entry
     * that has no cached row. Used by the per-image grid tab so the
     * thumbnail order matches QuPath's project tree.
     */
    public List<Map.Entry<ProjectImageEntry<?>, ImageRow>> entriesInProjectOrder(
            Project<?> project) {
        List<Map.Entry<ProjectImageEntry<?>, ImageRow>> out = new java.util.ArrayList<>();
        if (project == null) {
            return out;
        }
        for (ProjectImageEntry<?> entry : project.getImageList()) {
            ImageRow row = rows.get(entry);
            if (row != null) {
                out.add(Map.entry(entry, row));
            }
        }
        return out;
    }

    /**
     * Computes the open image's contribution from its live hierarchy and
     * stores the row under {@code entry}. Called on the FX thread by the
     * dialog whenever a {@code PathObjectHierarchyListener} fires.
     *
     * <p>If {@code entry} is null (image not part of a project), no work
     * happens and the dialog renders directly from the contributions
     * argument it computes itself.
     */
    public ImageRow updateOpen(ProjectImageEntry<?> entry,
                               ImageData.ImageType imageType,
                               PathObjectHierarchy hierarchy,
                               int polylineWidthPx) {
        if (hierarchy == null) {
            return null;
        }
        Collection<PathObject> annotations = hierarchy.getAnnotationObjects();
        Map<ClassKey, ClassSummary> contributions =
                ContributionCalculator.aggregate(annotations, polylineWidthPx);
        int count = sumCounts(contributions);
        ImageRow row = new ImageRow(
                imageType,
                entry == null ? "(open image)" : entry.getImageName(),
                contributions,
                count);
        if (entry != null) {
            rows.put(entry, row);
        }
        return row;
    }

    /**
     * Convenience overload of
     * {@link #repollAll(Project, int, Consumer, BooleanSupplier)} with no
     * cancellation hook.
     */
    public void repollAll(Project<?> project,
                          int polylineWidthPx,
                          Consumer<Double> progress) {
        repollAll(project, polylineWidthPx, progress, null);
    }

    /**
     * Walks every image in the project on the calling thread, replacing
     * every row in the cache. Catches and logs per-entry exceptions so a
     * single bad image does not abort the poll. Reports progress via the
     * supplied callback; the callback receives values in [0.0, 1.0].
     *
     * <p>If {@code cancelled} is non-null and returns {@code true}, or if
     * the calling thread's interrupt flag is set, the loop breaks cleanly
     * after the current entry. The partial cache is left intact (still
     * useful to the caller).
     *
     * <p>Reference lift: structurally identical to
     * {@code qupath-extension-confusion-matrix/.../ProjectClassDiscovery.java}
     * lines 113-155.
     */
    public void repollAll(Project<?> project,
                          int polylineWidthPx,
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
                logger.info("Class Distribution refresh cancelled at {} of {}",
                        processed, total);
                break;
            }
            try {
                @SuppressWarnings("unchecked")
                ImageData<BufferedImage> data =
                        (ImageData<BufferedImage>) entry.readImageData();
                if (data != null) {
                    PathObjectHierarchy h = data.getHierarchy();
                    Collection<PathObject> annotations = h == null
                            ? java.util.List.of() : h.getAnnotationObjects();
                    Map<ClassKey, ClassSummary> contributions =
                            ContributionCalculator.aggregate(annotations, polylineWidthPx);
                    int count = sumCounts(contributions);
                    ImageRow row = new ImageRow(
                            data.getImageType(),
                            entry.getImageName(),
                            contributions,
                            count);
                    rows.put(entry, row);
                }
            } catch (Exception e) {
                logger.warn("Skipping project entry {} during refresh: {}",
                        safeName(entry), e.toString());
            }
            processed++;
            if (progress != null) {
                progress.accept((double) processed / Math.max(1, total));
            }
        }
        logger.info("Class Distribution refresh complete: {} of {} images cached",
                rows.size(), total);
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        return cancelled != null && cancelled.getAsBoolean();
    }

    /**
     * Sums the cached rows whose {@link ImageData.ImageType} matches
     * {@code filter}. {@code filter == null} means "include every cached
     * row". {@code filter == ImageType.UNSET} matches rows whose stored
     * type is {@code UNSET} or {@code null}.
     */
    public FilterSummary summarize(ImageData.ImageType filter) {
        Map<ClassKey, ClassSummary> total = new LinkedHashMap<>();
        int annotations = 0;
        int images = 0;
        for (ImageRow row : rows.values()) {
            if (!matches(row.imageType(), filter)) {
                continue;
            }
            total = ContributionCalculator.sum(total, row.contributions());
            annotations += row.annotationCount();
            images++;
        }
        return new FilterSummary(total, annotations, images);
    }

    /**
     * Counts how many cached rows have each non-null {@link ImageData.ImageType}.
     * Returns a {@link LinkedHashMap} in QuPath enum order. Rows with a null
     * stored type are mapped under {@link ImageData.ImageType#UNSET}.
     */
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

    /**
     * Returns the most-common cached {@link ImageData.ImageType}, with
     * {@code UNSET} excluded by default per the intake's
     * "never auto-pick Unset" rule. Returns {@code null} if no cached row
     * has a non-UNSET type.
     */
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
            return true; // "All types"
        }
        if (filter == ImageData.ImageType.UNSET) {
            return rowType == null || rowType == ImageData.ImageType.UNSET;
        }
        return filter == rowType;
    }

    private static int sumCounts(Map<ClassKey, ClassSummary> contributions) {
        int n = 0;
        if (contributions != null) {
            for (ClassSummary s : contributions.values()) {
                if (s != null) {
                    n += s.count();
                }
            }
        }
        return n;
    }

    private static String safeName(ProjectImageEntry<?> entry) {
        try {
            return entry == null ? "(null)" : entry.getImageName();
        } catch (Exception e) {
            return "(unnamed)";
        }
    }
}
