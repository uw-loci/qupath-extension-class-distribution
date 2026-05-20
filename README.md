# QuPath Extension: Class Distribution

> Live pie charts of class distribution across the images in a QuPath
> project. Two dialogs: one charts the **annotation** class distribution
> (closed annotations by area, polylines by length); the other charts the
> **detection-classifier training balance** -- how many detections each
> class would label given your current training annotations. Each dialog
> has three tabs: an aggregate Project chart, a live Current image chart,
> and an All images grid of one mini-chart per project image. Filter to a
> single ImageType, and watch the charts update as you annotate. Classes
> that are dramatically over- or under-represented relative to the rest
> are highlighted so you can spot class imbalance before you export
> training data, not after. Built for working scientists, graduate
> students, and PIs reviewing trainee work who want a quick read on
> whether their project is annotation-balanced enough to feed a
> classifier.

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

This extension was developed on Linux (WSL2 with the QPSC dev build of
QuPath) and verified on Windows by the maintainer. macOS compatibility is
**expected but not verified**; the extension uses only JavaFX `PieChart`,
standard Swing-free QuPath APIs, and no native code, so no macOS-specific
issues are anticipated. Please file an issue if you hit any rendering,
dialog-modality, or font-sizing oddities on macOS so we can fold a real
macOS verification into a future release.

## Quick start

1. **Open a QuPath project that contains classified annotations.** An
   annotation is a region you have drawn (polygon, rectangle, ellipse,
   line, or polyline). A *classified* annotation is one you have assigned
   a class label to (e.g. "Tumour", "Stroma"). If your project has no
   classified annotations yet, draw a couple before opening a chart --
   otherwise the chart will be empty.
2. **Open a dialog.** The menu `Extensions > Class Distribution` has two
   items:
   - `Show Class Distribution...` -- annotation class distribution.
   - `Show Detection Training Distribution...` -- how many detections
     each class would label given the current training annotations.
   If the menu is not present, restart QuPath -- new extensions only load
   on startup. A non-modal window opens; on first open, a spinner runs
   while the project is scanned.
3. **Pick a tab.** Each dialog has three tabs: **Project** (the
   aggregate across the project), **Current image** (just the image open
   in the viewer, computed live), and **All images** (a scrollable grid
   of one mini-chart per project image, with a shared legend).
4. **Pick an Image Type** in the dropdown at the top of the dialog. The
   charts aggregate only images of this type. The default is the type of
   the currently open image; if that is unset, the most common type in
   the project is used.
5. **Read the chart.** Each slice is a class, drawn in that class's
   actual QuPath colour. In the annotation dialog, slice size is the
   total area of that class in pixels (closed annotations) plus the total
   length times the polyline width in pixels (lines / polylines); in the
   detection dialog, slice size is the labeled-detection count. A class
   that is dramatically over- or under-represented relative to the median
   is highlighted with a coloured drop-shadow "aura" and an `[over]` /
   `[under]` marker on its legend label -- the class colour always shows
   through. Hover for the exact percentage.

   **Note:** The annotation chart sums raw pixel quantities. If your
   project mixes images at different magnifications (e.g. 20x and 40x),
   filter to one Image Type whose images share a magnification for
   accurate comparison.
6. **Annotate live.** As you add, edit, or delete annotations on the open
   image, the Current image and Project charts update immediately. Use
   `Refresh from project` if you have modified other images via a script
   and want their changes folded in.

See the [user guide](docs/user-guide.md) for the tabs, filter behaviour,
polyline-width tuning, the highlight ratio, the detection dialog,
troubleshooting, and the known caveat about mixed-pixel-size projects.

## Issues / contributions / support

- Bug reports:
  [GitHub Issues](https://github.com/uw-loci/qupath-extension-class-distribution/issues)
- General QuPath questions:
  [image.sc forum](https://forum.image.sc/) with the `#qupath` tag; mention
  `@Mike_Nelson` to flag for my attention.
- Pull requests welcome -- open an issue first if the change is substantial.

## What's new

Highlights since the first release (full detail in
[CHANGELOG.md](CHANGELOG.md)):

- **v0.1.8** -- Both dialogs gained an "All images" tab: a scrollable,
  zoomable grid of one mini-chart per project image with a shared legend
  and a thumbnail-size slider.
- **v0.1.6-0.1.7** -- A second dialog, `Show Detection Training
  Distribution...`, charts how many detections each class would label
  given the current training annotations. Its Advanced section adds a
  "Split multi-part classifications" option.
- **v0.1.4-0.1.5** -- The dialogs became tabbed (Project / Current
  image), the Current image tab surfaces project classes missing from
  the open image, and an optional side-by-side mode shows Project and
  Current Image together.
- **v0.1.1-0.1.3** -- Slices now use each class's actual QuPath colour
  (highlighting moved to a drop-shadow aura plus legend markers), and the
  over/under-representation algorithm became a multiplicative ratio
  against the global median (default 2.0x).

**Storage:** this extension stores nothing inside your QuPath project.
Per-user preferences (filter, highlight ratio, dialog position, and the
rest of the Advanced controls) live in QuPath's standard `PathPrefs`
storage.

## Authors

- **Michael Nelson** - [GitHub](https://github.com/MichaelSNelson)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## AI-Assisted Development

This project was developed with assistance from [Claude](https://claude.ai) (Anthropic). Claude was used as a development tool for code generation, architecture design, debugging, and documentation throughout the project.
