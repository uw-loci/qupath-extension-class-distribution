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
 *   <li>The median share across ALL classes.</li>
 *   <li>An {@link Highlight} verdict using an absolute
 *       percentage-point threshold:
 *     <ul>
 *       <li>{@link Highlight#OVER} if {@code share - median >= thresholdPct/100}</li>
 *       <li>{@link Highlight#UNDER} if {@code median - share >= thresholdPct/100}</li>
 *       <li>{@link Highlight#NORMAL} otherwise.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Earlier versions compared against the median of OTHER classes with a
 * ratio-of-median threshold; in skewed distributions that flagged almost
 * every class. The current implementation uses the global median plus an
 * absolute percentage-point threshold, which reads as "flag a class when
 * its share is more than X percentage points above or below the typical
 * class share."
 *
 * <p>Edge cases:
 * <ul>
 *   <li>Single-class data: every slice is NORMAL (nothing to compare to).</li>
 *   <li>Total contribution is zero: every slice is NORMAL (cannot compute shares).</li>
 *   <li>Threshold of zero: any deviation from the median flags as OVER or
 *       UNDER (noisy; documented at the slider tooltip).</li>
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
     * Computed per-class slice metadata: share (0..1), the global median
     * across all classes (0..1), and the Highlight verdict.
     */
    public static final class SliceEval {
        private final double share;
        private final double median;
        private final Highlight highlight;

        public SliceEval(double share, double median, Highlight highlight) {
            this.share = share;
            this.median = median;
            this.highlight = highlight;
        }

        public double share() {
            return share;
        }

        /**
         * The global median share across all classes in the evaluation.
         */
        public double median() {
            return median;
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
     * Evaluates each class against the global median of all class shares,
     * flagging slices whose share is more than {@code thresholdPct}
     * percentage points above or below the median.
     *
     * @param contributions per-class contribution totals (typically from
     *                      {@link ContributionCalculator#sum(Map, Map)}); never null.
     * @param thresholdPct absolute percentage-point deviation from the
     *                     median required to flip a slice from NORMAL to
     *                     OVER / UNDER. Negative values are treated as zero.
     * @return per-class evaluation; never null.
     */
    public static Evaluation evaluate(Map<ClassKey, ClassSummary> contributions,
                                      double thresholdPct) {
        if (contributions == null) {
            contributions = Collections.emptyMap();
        }
        double thresholdShare = Math.max(0.0, thresholdPct) / 100.0;
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

        // Pre-compute shares (fraction of total, in [0, 1]).
        Map<ClassKey, Double> shares = new LinkedHashMap<>();
        java.util.List<Double> allShares = new java.util.ArrayList<>(contributions.size());
        for (Map.Entry<ClassKey, ClassSummary> e : contributions.entrySet()) {
            double v = e.getValue() == null ? 0.0 : e.getValue().total();
            double share = v / total;
            shares.put(e.getKey(), share);
            allShares.add(share);
        }

        double globalMedian = median(allShares);
        boolean canCompare = shares.size() >= 2;

        for (Map.Entry<ClassKey, Double> e : shares.entrySet()) {
            double share = e.getValue();
            double diff = share - globalMedian;
            Highlight h;
            if (!canCompare) {
                h = Highlight.NORMAL;
            } else if (thresholdShare == 0.0) {
                // Edge case: threshold zero flags any deviation from the median.
                if (diff > 1e-9) {
                    h = Highlight.OVER;
                } else if (diff < -1e-9) {
                    h = Highlight.UNDER;
                } else {
                    h = Highlight.NORMAL;
                }
            } else if (diff >= thresholdShare) {
                h = Highlight.OVER;
            } else if (diff <= -thresholdShare) {
                h = Highlight.UNDER;
            } else {
                h = Highlight.NORMAL;
            }
            out.put(e.getKey(), new SliceEval(share, globalMedian, h));
        }
        return new Evaluation(out, total);
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
