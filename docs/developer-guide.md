# Class Distribution -- Developer Guide

Developer-facing notes for building, extending, and releasing this
extension. End-user documentation lives in
[user-guide.md](user-guide.md).

## Building from source

```
./gradlew clean shadowJar
```

Requires JDK 21. The output is `build/libs/qupath-extension-class-distribution-{version}-all.jar`.

If your default Java is not 21, pin it:

```
./gradlew -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64 clean shadowJar
```

The shadow jar bundles the extension with its (minimal) dependencies; drop
it into your QuPath extensions folder for a manual install.

## Architecture overview

Fourteen classes, four packages:

```
qupath.ext.classdistribution
  ClassDistributionExtension       -- entry point + two menu items
  ui/
    ClassDistributionDialog        -- annotation dialog: Stage, tabs, listener
                                      lifecycle, dirty banner host
    DetectionTrainingDialog        -- detection-training dialog; mirrors the
                                      annotation dialog's layout
    ChartPane                      -- PieChart + custom legend + per-slice CSS
                                      + highlight aura
    MiniChartPane                  -- label-free PieChart for the All images grid
    ProjectGridPane                -- scrollable grid of MiniChartPanes +
                                      shared legend + thumbnail-size slider
    AdvancedSection                -- collapsible TitledPane of Advanced controls
    DirtyBanner                    -- amber HBox + Save-image button
  core/
    ContributionCalculator         -- area + length*width math; null-class
                                      -> Unclassified bucket
    HighlightEvaluator             -- global-median ratio logic;
                                      OVER / UNDER / NORMAL / MISSING
    ProjectAnnotationCache         -- per-image annotation cache + project poll
    DetectionLabelCalculator       -- labeled-detection counting; multi-part split
    DetectionTrainingCache         -- per-image detection-training cache + poll
  preferences/
    CDPreferences                  -- single-namespace PathPrefs
```

Both dialogs share `ChartPane`, `MiniChartPane`, `ProjectGridPane`,
`AdvancedSection`, `DirtyBanner`, `HighlightEvaluator`, and
`CDPreferences`. The annotation dialog uses `ContributionCalculator` +
`ProjectAnnotationCache`; the detection dialog uses
`DetectionLabelCalculator` + `DetectionTrainingCache`. Each dialog hosts
three tabs (Project / Current image / All images); the Advanced
"side-by-side" checkbox replaces the first two tabs with a `SplitPane`
and hides the All images tab.

### Live update flow

1. User opens dialog. `ClassDistributionDialog.attachListenersAfterShow()`
   attaches three listeners on the FX thread: a hierarchy listener on the
   open image, an `imageDataProperty` listener for image switches, and a
   `projectProperty` listener for project switches.
2. Initial poll spawns on a daemon background thread. Per-image rows are
   loaded via `entry.readImageData()` (we need both the hierarchy AND the
   `ImageType`; see "Reference choices" below) and stored in
   `ProjectAnnotationCache`.
3. When the user adds / edits / deletes an annotation on the open image,
   the hierarchy listener fires (after filtering `event.isChanging()`).
   The dialog recomputes only the open image's row from the live
   hierarchy and re-renders the chart. The other cached rows are
   untouched.
4. On image switch, the dialog detaches the old hierarchy listener,
   attaches to the new image's hierarchy, recomputes the new open image's
   contribution, and re-renders. The dialog does NOT close (contra the
   gated-classifier extension's image-switch policy).
5. On dialog close (`stage.setOnHidden`), every listener is detached, the
   singleton instance reference is cleared, and the dialog's last position
   + size are persisted to `CDPreferences`.

### Highlight evaluation

`HighlightEvaluator.evaluate(contributions, ratio)` flags each class
against a multiplicative ratio relative to the GLOBAL median of all
class shares (not the median of the *other* classes -- earlier versions
did that, and it over-flagged in skewed data).

Per slice: `share = total / sum-of-non-missing-totals`. The global
median is the median of every present class's share. With `k =
max(1.0, ratio)`:

- `OVER` if `share / median >= k`
- `UNDER` if `share / median <= 1 / k`
- `NORMAL` otherwise

The default ratio is `2.0`; the Advanced slider runs 1.5 to 10.0. The
ratio is symmetric in log space, which fixes the asymmetry of the old
percentage-point approach (a small-share class could never be N points
below a small median, so it could never be flagged under-represented).

Edge cases, exactly as implemented:

- Fewer than two present classes: every slice is `NORMAL` (nothing to
  compare against).
- Total contribution zero, or an empty input map: every slice is
  `NORMAL` (cannot compute shares), except missing entries (below).
- Degenerate median (`globalMedian <= 0.0`, i.e. more than half the
  present classes have zero share): any class with a positive share is
  `OVER`; nothing can be `UNDER`.
- A class passed in with zero contribution AND zero annotation count is
  treated as `MISSING`. Missing entries are excluded from the total and
  from the median computation (so injecting them does not skew the
  comparison group) but round-trip into the output map with
  `Highlight.MISSING`. The Current image view injects these to surface
  project classes the open image has none of.

### Detection-label evaluation

`DetectionLabelCalculator.aggregate(hierarchy)` counts how many
detections each class would label given the current training
annotations, mirroring QuPath's Object Classifier pipeline:

- An **area** annotation with a `PathClass` labels every detection
  inside its ROI, via `hierarchy.getAllDetectionsForROI(roi)`.
- A **point** annotation (Counting tool) with a `PathClass` labels the
  detection at each point, via
  `PathObjectTools.getObjectsForLocation(...)`.
- **Line / polyline** annotations are skipped -- they do not label
  detections.

A detection labeled by annotations of two different classes is counted
under BOTH classes; the sum of per-class counts can therefore exceed
`distinctLabeledDetections()`, which is itself a useful signal. The
result also reports `trainingAnnotationCount()` -- the number of
annotations that contributed at least one label. `ClassSummary.total()`
is set equal to `count()` so the pie slice scales with the
labeled-detection count.

`DetectionLabelCalculator.applySplit(raw, splitMultiPart)` optionally
re-keys the result so a composite class such as `CD4: CD8` contributes
its count to both `CD4` and `CD8`. It uses QuPath's
`PathClassTools.splitNames(PathClass)` for the split and
`PathClass.fromString(String)` to look up the canonical single-name
`PathClass` (so user-set colours on `CD4` / `CD8` are picked up). When
`splitMultiPart` is false the input is returned unchanged. This mirrors
the "Distance to annotations 2D" command's handling of the same flag.

### Reference choices

- **Pie chart construction and per-slice CSS** is adapted from
  `qupath-extension-dl-pixel-classifier/.../TrainingDialog.java` lines
  4302-4309 and 5449-5496. The `nodeProperty` listener is the non-obvious
  bit: a `PieChart.Data` node is null until layout, so a one-shot
  listener applies the colour CSS when the node first appears. Re-applied
  on every refresh so ColorPicker-driven colour changes survive without
  rebuilding the chart.
- **Per-image data load** uses `ProjectImageEntry.readImageData()` (the
  pattern in `qupath-extension-confusion-matrix/.../ProjectClassDiscovery.java`
  lines 113-155). The lighter-weight `ProjectImageEntry.readHierarchy()`
  exists in QuPath 0.6.x but does not return the `ImageType` that the
  dropdown filter needs, so a single `readImageData()` call gets us both
  pieces of state and is then cached.
- **Listener lifecycle** is taken from
  `qupath-extension-classify-object-subset/.../ClassifySubsetDialog.java`
  lines 494-553. Gated CLOSES on image switch; we REBIND. Otherwise the
  pattern is identical: attach in `configureStage()` after `stage.show()`,
  detach in `stage.setOnHidden(...)`, filter `event.isChanging()`.
- **Preferences** use the single-namespace `PathPrefs` shape from
  `qupath-extension-confusion-matrix/.../CMPreferences.java`. All keys
  live under the `classdistribution.` prefix: the Advanced controls
  (polyline width, highlight ratio, both highlight colours, slice-labels,
  advanced-expanded, side-by-side, split-multi-part, grid thumbnail
  size), the last image-type filter, and the dialog's last X / Y / width
  / height.

## Scripting API

None yet. Both dialogs open via their menu items only. If you need to
open a chart or trigger a re-poll from a Groovy script, file an issue
describing the use case so we can pick a sensible API surface.

## Contributing

- File an issue first for any non-trivial change so we can agree on the
  approach before code lands.
- Keep code ASCII-only (no arrows, no Greek letters, no en-dashes,
  including in log messages). Pre-commit grep:
  `grep -rn '[^\x00-\x7F]' src/main/java`.
- Match the existing code style: no parallel `_legacy` paths, refactor in
  place. Use `Path.of` / `Path.resolve` for any filesystem path.
- Use `qupath.fx.dialogs.Dialogs` for any user-facing alert; the
  deprecated `qupath.lib.gui.dialogs.Dialogs` is forbidden.

## Releasing

1. Bump `version` in `build.gradle.kts` and add a `CHANGELOG.md` entry.
2. Tag the commit `vX.Y.Z` and push.
3. Cut a GitHub Release on that tag with the shadow jar attached as
   `qupath-extension-class-distribution-X.Y.Z-all.jar`.
4. The
   [`notify-catalog.yml`](../.github/workflows/notify-catalog.yml)
   workflow fires on `release: published` and dispatches a
   `repository_dispatch: extension-release` event to
   `uw-loci/qupath-catalog-mikenelson`. The catalog repo's
   `update-on-release.yml` then auto-bumps `catalog.json` to the new tag
   and asset URL.
5. Verify within 1 minute: the catalog repo should show an
   `Auto-bump qupath-extension-class-distribution -> vX.Y.Z` commit. If
   it does not, check that the `CATALOG_DISPATCH_TOKEN` org-level secret
   is reachable from this repo. The workflow also exposes
   `workflow_dispatch` for manual recovery.
