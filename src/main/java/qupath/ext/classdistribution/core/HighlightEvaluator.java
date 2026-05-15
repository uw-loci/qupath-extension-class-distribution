package qupath.ext.classdistribution.core;

import qupath.ext.classdistribution.core.ContributionCalculator.ClassKey;
import qupath.ext.classdistribution.core.ContributionCalculator.ClassSummary;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates per-class over- / under-representation against a global
 * threshold.
 *
 * <p>For each class, the evaluator computes:
 * <ol>
 *   <li>The class's share of the total (e.g. 0.42 for 42%).</li>
 *   <li>The median of the OTHER classes' shares (i.e. excluding this class).</li>
 *   <li>An {@link Highlight} verdict:
 *     <ul>
 *       <li>{@link Highlight#OVER} if {@code share >= median * (1 + thresholdPct/100)}</li>
 *       <li>{@link Highlight#UNDER} if {@code share <= median * (1 - thresholdPct/100)}</li>
 *       <li>{@link Highlight#NORMAL} otherwise.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Edge cases:
 * <ul>
 *   <li>Single-class data: every slice is NORMAL (no median of "others").</li>
 *   <li>Total contribution is zero: every slice is NORMAL (cannot compute shares).</li>
 *   <li>Median of others is zero (i.e. every other class is empty): if the
 *       single non-empty class has any positive share it is OVER; UNDER cannot
 *       fire because there is no positive median to be under.</li>
 *   <li>Threshold of zero: any deviation flags as OVER or UNDER (almost
 *       certainly noisy; documented at the slider tooltip).</li>
 * </ul>
 *
 * @author Mike Nelson
 */
public final class HighlightEvaluator {

    private HighlightEvaluator() {
        // Utility class - prevent instantiation
    }

    /**
     * Highlight verdict for one slice.
     */
    public enum Highlight {
        OVER,
        UNDER,
        NORMAL
    }

    /**
     * Computed per-class slice metadata: share (0..1), median-of-others
     * (0..1), and the Highlight verdict.
     */
    public static final class SliceEval {
        private final double share;
        private final double medianOfOthers;
        private final Highlight highlight;

        public SliceEval(double share, double medianOfOthers, Highlight highlight) {
            this.share = share;
            this.medianOfOthers = medianOfOthers;
            this.highlight = highlight;
        }

        public double share() {
            return share;
        }

        public double medianOfOthers() {
            return medianOfOthers;
        }

        public Highlight highlight() {
            return highlight;
        }
    }

    /**
     * Per-class evaluation result, keyed in iteration order matching the
     * input map.
     */
    public static final class Evaluation {
        private final Map<ClassKey, SliceEval> perSlice;
        private final double total;

        public Evaluation(Map<ClassKey, SliceEval> perSlice, double total) {
            this.perSlice = Collections.unmodifiableMap(new LinkedHashMap<>(perSlice));
            this.total = total;
        }

        public Map<ClassKey, SliceEval> perSlice() {
            return perSlice;
        }

        public double total() {
            return total;
        }
    }

    /**
     * Evaluates each class against the median of the other classes.
     *
     * @param contributions per-class contribution totals (typically from
     *                      {@link ContributionCalculator#sum(Map, Map)}); never null.
     * @param thresholdPct deviation percent that flips a slice from NORMAL to
     *                     OVER / UNDER. Negative values are treated as zero.
     * @return per-class evaluation; never null.
     */
    public static Evaluation evaluate(Map<ClassKey, ClassSummary> contributions,
                                      double thresholdPct) {
        if (contributions == null) {
            contributions = Collections.emptyMap();
        }
        double thresh = Math.max(0.0, thresholdPct);
        double total = 0.0;
        for (ClassSummary s : contributions.values()) {
            if (s != null) {
                total += s.total();
            }
        }

        Map<ClassKey, SliceEval> out = new LinkedHashMap<>();

        if (total <= 0.0 || contributions.isEmpty()) {
            for (ClassKey k : contributions.keySet()) {
                out.put(k, new SliceEval(0.0, 0.0, Highlight.NORMAL));
            }
            return new Evaluation(out, total);
        }

        // Pre-compute shares so we don't redo the divide for every slice.
        Map<ClassKey, Double> shares = new LinkedHashMap<>();
        for (Map.Entry<ClassKey, ClassSummary> e : contributions.entrySet()) {
            double v = e.getValue() == null ? 0.0 : e.getValue().total();
            shares.put(e.getKey(), v / total);
        }

        // For each class, take the median of the OTHER classes' shares.
        for (Map.Entry<ClassKey, Double> e : shares.entrySet()) {
            double share = e.getValue();
            double median = medianExcluding(shares, e.getKey());
            Highlight h;
            if (shares.size() < 2) {
                // Single class: nothing to compare against.
                h = Highlight.NORMAL;
            } else if (median <= 0.0) {
                // No positive comparison group; treat any positive share as OVER.
                h = share > 0.0 ? Highlight.OVER : Highlight.NORMAL;
            } else {
                double upper = median * (1.0 + thresh / 100.0);
                double lower = median * (1.0 - thresh / 100.0);
                if (share >= upper && upper > median) {
                    // Strict-above only when threshold > 0 (upper > median).
                    h = Highlight.OVER;
                } else if (share <= lower && lower < median) {
                    h = Highlight.UNDER;
                } else if (thresh == 0.0 && share != median) {
                    // Threshold of zero: any deviation flags.
                    h = share > median ? Highlight.OVER : Highlight.UNDER;
                } else {
                    h = Highlight.NORMAL;
                }
            }
            out.put(e.getKey(), new SliceEval(share, median, h));
        }
        return new Evaluation(out, total);
    }

    /**
     * Median of the values in {@code shares} excluding the entry keyed by
     * {@code excluded}. Returns zero if no values remain.
     */
    private static double medianExcluding(Map<ClassKey, Double> shares, ClassKey excluded) {
        // Copy values into a list, drop the excluded key.
        java.util.List<Double> values = new java.util.ArrayList<>(shares.size());
        for (Map.Entry<ClassKey, Double> e : shares.entrySet()) {
            if (!e.getKey().equals(excluded)) {
                values.add(e.getValue());
            }
        }
        return median(values);
    }

    /**
     * Numerical median of the supplied values; zero if the list is empty.
     */
    static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        java.util.List<Double> sorted = new java.util.ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if ((n & 1) == 1) {
            return sorted.get(n / 2);
        }
        return 0.5 * (sorted.get(n / 2 - 1) + sorted.get(n / 2));
    }
}
