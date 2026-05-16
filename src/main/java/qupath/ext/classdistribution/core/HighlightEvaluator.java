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
 *   <li>An {@link Highlight} verdict using a multiplicative ratio:
 *     <ul>
 *       <li>{@link Highlight#OVER} if {@code share / median >= ratio}</li>
 *       <li>{@link Highlight#UNDER} if {@code share / median <= 1 / ratio}</li>
 *       <li>{@link Highlight#NORMAL} otherwise.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Earlier versions used absolute percentage-point differences from the
 * median, but that algorithm is asymmetric for distributions with a small
 * median -- a class CAN be N percentage points above a small median, but
 * cannot be N percentage points below it (shares are non-negative). The
 * multiplicative ratio is symmetric in log-space and flags small
 * under-represented classes the same way it flags large over-represented
 * ones.
 *
 * <p>Edge cases:
 * <ul>
 *   <li>Single-class data: every slice is NORMAL (nothing to compare to).</li>
 *   <li>Total contribution is zero: every slice is NORMAL (cannot compute shares).</li>
 *   <li>Median is zero (i.e. fewer than half the classes have any data):
 *       any class with a positive share is OVER; UNDER cannot fire.</li>
 *   <li>Ratio &lt;= 1.0: clamped to 1.0 (a ratio of 1 means "any deviation"
 *       and below 1 has no meaningful interpretation).</li>
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
     * flagging slices whose share is at least {@code ratio} times above
     * or at most {@code 1/ratio} times below the median.
     *
     * @param contributions per-class contribution totals (typically from
     *                      {@link ContributionCalculator#sum(Map, Map)}); never null.
     * @param ratio multiplicative threshold. Values &lt;= 1.0 are clamped to
     *              1.0. A ratio of 2.0 means "flag when share is at least
     *              2x or at most 0.5x the median class share."
     * @return per-class evaluation; never null.
     */
    public static Evaluation evaluate(Map<ClassKey, ClassSummary> contributions,
                                      double ratio) {
        if (contributions == null) {
            contributions = Collections.emptyMap();
        }
        double k = Math.max(1.0, ratio);
        double total = 0.0;
        for (ClassSummary s : contributions.values()) {
            if (s != null) {
                total += s.total();
            }
        }

        Map<ClassKey, SliceEval> out = new LinkedHashMap<>();

        if (total <= 0.0 || contributions.isEmpty()) {
            for (ClassKey c : contributions.keySet()) {
                out.put(c, new SliceEval(0.0, 0.0, Highlight.NORMAL));
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
            Highlight h;
            if (!canCompare) {
                h = Highlight.NORMAL;
            } else if (globalMedian <= 0.0) {
                // Degenerate: more than half the classes are empty. Any
                // positive share is OVER; nothing can be UNDER.
                h = share > 0.0 ? Highlight.OVER : Highlight.NORMAL;
            } else {
                double r = share / globalMedian;
                if (r >= k) {
                    h = Highlight.OVER;
                } else if (r <= 1.0 / k) {
                    h = Highlight.UNDER;
                } else {
                    h = Highlight.NORMAL;
                }
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
