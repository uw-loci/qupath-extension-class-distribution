# Changelog

All notable changes to this extension are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

### Known limitations
- Raw-pixel summation does not convert to physical units; mixed-pixel-size
  projects produce slightly misleading totals. Workaround: filter to one
  `ImageType` whose images share a magnification.
- No scripting API; menu item only.
- No CSV / PNG export.
- No per-class custom thresholds; one global threshold for all slices.
- No companion bar-chart view for high class counts; slice labels
  auto-suppress above ten slices and fall back to the legend.
- macOS not verified for v0.1.0; Linux + Windows only.
