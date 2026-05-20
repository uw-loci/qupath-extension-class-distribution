# Changelog

All notable changes to this extension are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.8] - 2026-05-19

### Added
- Third tab, "All images", in both dialogs: a scrollable, zoomable grid
  of one small pie chart per project image, with a single frozen shared
  legend pinned at the top.
- Thumbnail-size slider (60-240 px) on the All images tab resizes every
  thumbnail together; the value persists across sessions.
- The currently open image's thumbnail in the grid gets a cyan border;
  clicking any thumbnail opens that image in QuPath.

### Changed
- Mini charts in the grid have no per-chart labels or legend -- the
  shared legend at the top of the tab covers slice colours.
- In the Detection Training dialog the grid honours the "Split
  multi-part classifications" checkbox.
- The All images tab is available only in tabbed mode; it is not shown
  when side-by-side mode is active.

## [0.1.7] - 2026-05-16

### Added
- "Split multi-part classifications" checkbox in the Detection Training
  dialog's Advanced section, mirroring QuPath's "Distance to annotations
  2D" command. When checked, a composite class such as `CD4: CD8`
  contributes its labeled-detection count to both `CD4` and `CD8`
  separately (via `PathClassTools.splitNames`); unchecked, it is its own
  class. This checkbox appears only in the detection dialog.

### Changed
- The Detection Training dialog's Advanced section does not show the
  polyline-width control, since polylines do not label detections.

## [0.1.6] - 2026-05-16

### Added
- Second dialog and menu item: `Extensions > Class Distribution > Show
  Detection Training Distribution...`. It mirrors the first dialog's
  layout but charts how many detections each class would label given the
  current training annotations, matching QuPath's Object Classifier
  logic: area annotations with a class label every detection inside the
  ROI; point annotations (Counting tool) with a class label the
  detection at each point; lines and polylines do not label detections.
- Detection dialog status line reads "N labeled detections (via P
  training annotations) across M images."
- Bold "Project" header above the project chart in both dialogs,
  mirroring the existing "Image: <name>" header on the current-image
  side.

## [0.1.5] - 2026-05-16

### Added
- Missing-class surfacing on the Current image tab: a class that exists
  elsewhere in the project but has no annotations in the open image
  appears in that tab's legend with a yellow `[missing]` marker
  (legend-only, not a pie slice).
- "Show Project and Current Image side-by-side" checkbox in the Advanced
  section. When checked, the tabbed display is swapped for a horizontal
  split with a draggable divider. The setting persists across sessions.

## [0.1.4] - 2026-05-16

### Added
- Tabbed dialog layout. Tab 1 "Project" is the existing project-wide
  aggregate chart; tab 2 "Current image" charts only the image open in
  the QuPath viewer, computed live from its hierarchy, with an empty
  placeholder when no image is open.

## [0.1.3] - 2026-05-16

### Changed
- The over- / under-representation algorithm reached its final form: a
  multiplicative ratio against the global median. A class is OVER when
  its share is at least N times the median class share, UNDER when it is
  at most 1/N times the median. Default N is 2.0x; the Advanced slider
  runs 1.5x to 10.0x. This is symmetric in log space, fixing the
  asymmetry of the percentage-point approach (a small-share class could
  never be flagged under-represented).

## [0.1.2] - 2026-05-15

### Changed
- Unclassified slices now use QuPath's `colorDefaultObjects` preference
  -- the same colour QuPath paints unclassified annotations with (red by
  default) -- instead of a hardcoded grey.
- Reworked the over- / under-representation algorithm: instead of
  comparing each class to the median of the OTHER classes with a
  percentage threshold (which over-flagged in skewed data), it now
  compares each class to the GLOBAL median of all classes using an
  absolute percentage-point threshold. (This was an interim step; see
  0.1.3 for the final ratio-based algorithm.)

## [0.1.1] - 2026-05-15

### Changed
- Pie slices now always render in each class's actual QuPath `PathClass`
  colour. Previously over- and under-represented slices were filled with
  the highlight colour, so the chart could only ever show two colours.
  Highlighting is now conveyed by a colored drop-shadow "aura" around the
  slice (and a stroke plus glow on the legend swatch) together with an
  `[over]` / `[under]` text marker on legend labels, so the class colour
  always shows through.

## [0.1.0] - 2026-05-14

### Added
- First release.
- `Extensions > Class Distribution > Show Class Distribution...` menu item
  opens a single non-modal tool window.
- Live pie chart of annotation class distribution across project images,
  filtered by `ImageType`.
- Closed annotations contribute by area (`ROI.getArea()`); polyline / line
  annotations contribute by `length * polyline_width_px` so they sum
  coherently with area-based slices.
- Annotations with no `PathClass` are aggregated into a single
  "Unclassified" bucket displayed in grey.
- Per-image in-memory cache. The currently open image updates live via a
  `PathObjectHierarchyListener`; other images update on the manual
  `Refresh from project` button.
- Cancel button next to `Refresh from project` while a refresh is in
  flight; partial cache is retained on cancel.
- Amber dirty-image banner when the open hierarchy has unsaved changes;
  `Save image to project` button calls `ProjectImageEntry.saveImageData()`
  to commit.
- Configurable over- / under-representation threshold (default 30%) with
  Okabe-Ito blue (`#0072B2`) / vermilion (`#D55E00`) slice highlighting --
  the standard colourblind-safe "good vs bad" pair; both colours
  user-pickable in the Advanced section.
- Per-user persistence of all eight Advanced controls (polyline width,
  threshold, both colours, slice-labels checkbox, advanced-section
  expanded state, last image-type filter) plus the dialog's last position
  and size, via `PathPrefs`.
- Catalog dispatch via `.github/workflows/notify-catalog.yml` targeting
  `uw-loci/qupath-catalog-mikenelson`.

### Changed (Phase 5 test-response fixes)
- Default highlight palette switched to Okabe-Ito blue / vermilion
  (colourblind-safe) instead of the original cyan / red pair (grad-student
  finding M-1).
- Default highlight threshold lowered from 50% to 30% so a 60/30/10
  split no longer reads as "balanced" (PI minor finding).
- "Re-poll" terminology replaced with "Refresh" throughout user-facing
  strings (button label, progress text, status bar, tooltips, README,
  user guide). Internal class names unchanged (grad-student finding m-2).
- "Last polled HH:mm" replaced with "Last refreshed yyyy-MM-dd HH:mm" so
  long-running sessions and screenshots are unambiguous (PI major finding).
- Per-slice hover format now uses thousands separators and the unit label
  "px-equivalent" instead of bare "px" (grad-student m-4, scientist NIT-1
  + MIN-6).
- Status summary uses thousands separators (scientist MIN-6).
- Threshold readout now renders as `30%` (no space) to match the user
  guide and conventional percent notation (grad-student n-3, PI nit).
- Empty-state messages now suggest the next user action (grad-student m-3).
- Save button label clarified to "Save image to project" (PI minor).
- DirtyBanner inline strings moved into `strings.properties` so the
  bundle is the single source of truth for user-facing text
  (grad-student m-5).
- Polyline-width tooltip and user guide now name the DL Pixel Classifier
  brush-width default (5) so users matching that pipeline know the
  expected value (scientist MAJ-3 -- doc-only, default unchanged).
- README Quick Start step 1 rewritten to define "annotation" and
  "classified annotation" inline (grad-student M-2).
- User guide gains a top-of-document "Vocabulary" block defining
  Annotation, Class, Image Type, Polyline (grad-student M-2).
- README Quick Start gains an inline mixed-pixel-size caveat at step 4
  so the trap is signposted before the chart is read (PI minor).
- README gains a one-line "Storage" disclosure so institutional
  reviewers can see at a glance that nothing is written into the
  project (PI minor).
- User guide gains a "renamed class -> two slices" troubleshooting
  bullet (scientist NIT-4).

### Fixed
- The initial 0.1.0 jar crashed on dialog open with
  `Button.disable: A bound value cannot be set` --
  `repollButton.disableProperty()` had been bound and then `setDisable`
  was called on it. The fix was applied and the 0.1.0 release was recut
  the same day.

### Known limitations
- Raw-pixel summation does not convert to physical units; mixed-pixel-size
  projects produce slightly misleading totals. Workaround: filter to one
  `ImageType` whose images share a magnification.
- No scripting API; menu item only.
- No CSV / PNG export.
- One global highlight setting for all slices; no per-class thresholds.
- No companion bar-chart view for high class counts; slice labels
  auto-suppress above ten slices and fall back to the legend.
- macOS not verified; Linux + Windows only.
