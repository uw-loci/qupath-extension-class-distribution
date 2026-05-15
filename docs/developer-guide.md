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

Eight classes, three packages:

```
qupath.ext.classdistribution
  ClassDistributionExtension       -- entry point + menu wiring
  ui/
    ClassDistributionDialog        -- Stage + listener lifecycle + dirty banner host
    ChartPane                      -- PieChart + custom legend + per-slice CSS
    AdvancedSection                -- collapsible TitledPane: width / threshold / colours / labels
    DirtyBanner                    -- amber HBox + Save-image button
  core/
    ProjectAnnotationCache         -- per-image cache + project poll
    ContributionCalculator         -- area + length*width math; null-class -> Unclassified bucket
    HighlightEvaluator             -- median + threshold logic; OVER / UNDER / NORMAL
  preferences/
    CDPreferences                  -- single-namespace PathPrefs
```

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

Per slice: share = total / sum-of-totals; median = median of OTHER slices'
shares; OVER if `share >= median * (1 + threshold/100)`, UNDER if
`share <= median * (1 - threshold/100)`, NORMAL otherwise. Single-class
data and zero-total cases short-circuit to NORMAL.

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
  `qupath-extension-gated-object-classifier/.../GatedClassifierDialog.java`
  lines 494-553. Gated CLOSES on image switch; we REBIND. Otherwise the
  pattern is identical: attach in `configureStage()` after `stage.show()`,
  detach in `stage.setOnHidden(...)`, filter `event.isChanging()`.
- **Preferences** use the single-namespace `PathPrefs` shape from
  `qupath-extension-confusion-matrix/.../CMPreferences.java`. Eleven keys
  total, all under the `classdistribution.` prefix.

## Scripting API

None in v0.1.0. The dialog opens via the menu item only. If you need to
open the chart or trigger a re-poll from a Groovy script, file an issue
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
