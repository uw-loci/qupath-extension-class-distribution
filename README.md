# QuPath Extension: Class Distribution

> A live pie chart of annotation class distribution across the images in a
> QuPath project. Filter to a single ImageType, sum closed annotations by
> area and polylines by length, and watch the chart update as you annotate.
> Pie slices that are dramatically over- or under-represented relative to
> the rest are highlighted so you can spot class imbalance before you export
> training data, not after. Built for working scientists, graduate students,
> and PIs reviewing trainee work who want a quick read on whether their
> project is annotation-balanced enough to feed a classifier.

## Requirements

- QuPath 0.6.0 or later.
- A QuPath project (single-image use is degraded but not blocked -- the chart
  still polls the open image and renders a single-image distribution).
- JDK 21 (only required to build from source).

## Installation

### Catalog method (preferred)

1. In QuPath, open `Extensions > Manage extensions`.
2. Click `Manage extension catalogs > Add catalog`.
3. Add catalog URL:
   `https://github.com/uw-loci/qupath-catalog-mikenelson` (catalog name:
   `qupath-catalog-mikenelson`).
4. Find `Class Distribution` in the catalog list and click `Install`.
5. Restart QuPath.

The catalog auto-bumps when a new release ships, so the next time you open
QuPath after a release the update is offered.

### Manual jar method (fallback)

1. Download `qupath-extension-class-distribution-{version}-all.jar` from the
   [Releases page](https://github.com/uw-loci/qupath-extension-class-distribution/releases).
2. Drag the jar onto a running QuPath window. When QuPath asks whether to
   copy the jar into your extensions folder, accept.
3. Restart QuPath. (QuPath copies the jar but does not load new extensions
   on the fly; the menu item only appears after a full restart.)

The standard QuPath extensions folder is platform-specific:

| OS | Folder |
|---|---|
| Linux | `~/QuPath/v0.7/extensions/` |
| macOS | `~/Library/Application Support/QuPath/v0.7/extensions/` |
| Windows | `%LOCALAPPDATA%\QuPath\v0.7\extensions\` |

### Cross-platform notes

v0.1.0 was developed on Linux (WSL2 with the QPSC dev build of QuPath) and
verified on Windows by the maintainer. macOS compatibility is **expected
but not verified** for this release; the extension uses only JavaFX
`PieChart`, standard Swing-free QuPath APIs, and no native code, so no
macOS-specific issues are anticipated. Please file an issue if you hit any
rendering, dialog-modality, or font-sizing oddities on macOS so we can
fold a real macOS verification into the next release.

## Quick start

1. **Open a QuPath project that contains classified annotations.** An
   annotation is a region you have drawn (polygon, rectangle, ellipse,
   line, or polyline). A *classified* annotation is one you have assigned
   a class label to (e.g. "Tumour", "Stroma"). If your project has no
   classified annotations yet, draw a couple before opening the chart --
   otherwise the chart will be empty.
2. **Open the chart**: choose `Extensions > Class Distribution > Show
   Class Distribution...`. If the menu is not present, restart QuPath --
   new extensions only load on startup. A non-modal window titled
   "Class Distribution" opens; on first open, a spinner runs while the
   project is scanned (see step 4 once it finishes).
3. **Pick an Image Type** in the dropdown at the top of the dialog. The
   chart aggregates only images of this type. The default is the type of
   the currently open image; if that is unset, the most common type in
   the project is used.
4. **Read the chart.** Each slice is a class; slice size is the total area
   of that class in pixels (closed annotations) plus the total length
   times the polyline width in pixels (lines / polylines). Slices in
   blue are above-average; slices in vermilion are below-average (the
   default Okabe-Ito colourblind-safe pair; both colours are
   user-pickable in the Advanced section). Hover for the exact
   percentage.

   **Note:** The chart sums raw pixel quantities. If your project mixes
   images at different magnifications (e.g. 20x and 40x), filter to one
   Image Type whose images share a magnification for accurate
   comparison.
5. **Annotate live.** As you add, edit, or delete annotations on the open
   image, the chart updates immediately. Use `Refresh from project` if
   you have modified other images via a script and want their changes
   folded in.

See the [user guide](docs/user-guide.md) for filter behaviour, polyline-width
tuning, the over/under-representation threshold, troubleshooting, and the
known caveat about mixed-pixel-size projects.

## Issues / contributions / support

- Bug reports:
  [GitHub Issues](https://github.com/uw-loci/qupath-extension-class-distribution/issues)
- General QuPath questions:
  [image.sc forum](https://forum.image.sc/) with the `#qupath` tag; mention
  `@Mike_Nelson` to flag for my attention.
- Pull requests welcome -- open an issue first if the change is substantial.

## What's new

**v0.1.0** -- First release. Live pie chart of annotation class
distribution across project images filtered by `ImageType`. Closed
annotations contribute by area; polylines contribute by
`length * polyline_width_px`. Per-image in-memory cache with live update
on the open image via `PathObjectHierarchyListener`. Manual
`Refresh from project` button for the rest, with a Cancel button while a
refresh is running. Dirty-image warning banner when the open hierarchy
has unsaved changes. Configurable over/under-representation threshold
(default 30%) with Okabe-Ito blue / vermilion (colourblind-safe) slice
highlighting; both colours user-pickable.

**Storage:** this extension stores nothing inside your QuPath project.
Per-user preferences (filter, threshold, dialog position) live in
QuPath's standard `PathPrefs` storage.

## Authors

- **Michael Nelson** - [GitHub](https://github.com/MichaelSNelson)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## AI-Assisted Development

This project was developed with assistance from [Claude](https://claude.ai) (Anthropic). Claude was used as a development tool for code generation, architecture design, debugging, and documentation throughout the project.
