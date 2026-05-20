# Class Distribution -- User Guide

This guide walks through the extension one task at a time. Sections are
collapsible; expand the ones you need. The "Getting started" section is
expanded by default; everything else is collapsed.

## Vocabulary

The extension uses these QuPath terms with specific meanings:

- **Annotation:** A region you have drawn on an image in QuPath -- a
  polygon, rectangle, ellipse, line, or polyline. The annotation chart
  counts annotations across every image in the project.
- **Detection:** An object QuPath generates inside an image (typically a
  detected cell or nucleus). The Detection Training dialog charts
  detections, not annotations.
- **Training annotation:** An annotation that, in QuPath's Object
  Classifier pipeline, labels detections for classifier training. An
  area annotation with a class labels every detection inside its ROI; a
  point annotation (Counting tool) with a class labels the detection at
  each point. Lines and polylines do not label detections.
- **Class:** A label assigned to an annotation, e.g. "Tumour", "Stroma".
  Annotations with no class assigned go into a single "Unclassified"
  bucket on the chart.
- **Image Type:** A QuPath property of each image (Brightfield H&E,
  Brightfield other, Fluorescence, etc.) set when an image is added to
  the project. The charts can be filtered to one Image Type.
- **Polyline:** A multi-segment line annotation (not a closed shape).
  Polylines have length but no area, so the annotation chart estimates
  their pixel coverage as `length * polyline-width-pixels`.
- **Image grid:** The "All images" tab -- a scrollable grid of one small
  pie chart per project image, with a single shared legend.

<details open>
<summary><b>Getting started (read this first)</b></summary>

**Overview.** This extension provides two dialogs, each opened from the
`Extensions > Class Distribution` menu:

- **Show Class Distribution...** charts how balanced the *annotated
  classes* are across your project. Each pie slice is one class; slice
  size is the total annotated quantity for that class.
- **Show Detection Training Distribution...** charts how many
  *detections* each class would label given your current training
  annotations -- a direct read on object-classifier training balance.

Each dialog has three tabs:

- **Project** -- the aggregate chart across every project image whose
  Image Type matches the dropdown filter.
- **Current image** -- a chart of just the image open in the QuPath
  viewer, computed live from its hierarchy. Empty placeholder when no
  image is open.
- **All images** -- a scrollable, zoomable grid of one small pie chart
  per project image, with a single shared legend pinned at the top.

**Prerequisites.** A QuPath project. At least one image in the project
should have classified annotations -- without classifications the charts
are empty. Annotations with no class are bucketed under `Unclassified`
(a single combined slice) so you can see how much of your annotation
effort is unlabelled.

**First-run walkthrough.** On first open, a dialog runs a one-time
refresh of every image in the project on a background thread. A spinner
shows that the refresh is in progress; the Refresh button label morphs
to `Refreshing 7/12...` so you can see how far it has got, and a Cancel
button appears next to it if you need to stop. Once the refresh
completes, the charts render, listening starts on the open image, and
subsequent annotation edits update the Project and Current image charts
instantly.

</details>

<details>
<summary><b>Common tasks</b></summary>

### Check class balance while annotating (the primary use case)

You annotate as normal; every time you add, edit, or delete an annotation
on the open image, the Project and Current image charts update within a
frame. Watch the highlighting -- a class drawn with an over-represented
aura is well above the median; one with an under-represented aura is
well below it (the legend label also shows an `[over]` / `[under]`
marker). The point is to notice imbalance early enough to fix it before
you export.

### See just the image you are working on

Switch to the **Current image** tab to chart only the image open in the
viewer, computed live from its hierarchy. This isolates one image from
the project aggregate -- useful when you want to know whether *this*
slide is balanced without the rest of the project averaging it out. If
no image is open, the tab shows an empty placeholder.

The Current image tab also surfaces **missing classes**: a class that
exists elsewhere in the project but has no annotations in the open image
appears in that tab's legend with a yellow `[missing]` marker. It is a
legend entry only -- there is no pie slice for a class with no data --
so you can see at a glance which project classes this image still lacks.

### Browse every image at once with the grid

Switch to the **All images** tab for a scrollable grid of one small pie
chart per project image, with a single shared legend pinned at the top.
The thumbnail-size slider resizes every mini-chart together (60 to 240
px). The currently open image's thumbnail has a cyan border; click any
thumbnail to open that image in QuPath. The mini-charts have no labels
or per-chart legend -- the shared legend covers slice colours. The grid
is available only in tabbed mode (it is hidden when side-by-side mode is
on).

### Compare Project and Current image side-by-side

Open the Advanced section and tick **Show Project and Current Image
side-by-side**. The tabbed display is replaced with a horizontal split
-- Project chart on one side, Current image chart on the other -- with a
draggable divider so you can size each. The setting persists across
sessions. While side-by-side mode is on, the All images tab is not
available; untick the box to get the tabs (and the grid) back.

### Refresh the project after running a batch script

The live update only covers the currently open image. If you ran a
Groovy script via `Run > Run for project` that mutated annotations on
other images, click `Refresh from project` to walk the project from disk
again. A spinner shows progress; the dialog stays responsive (the
refresh runs on a background thread). A Cancel button next to the
Refresh button appears while the refresh is running -- press it to stop
early, and the charts keep whichever images were loaded before you
cancelled.

### Adjust polyline width to match your training pipeline

(Annotation dialog only.) Polyline annotations contribute
`length_pixels * polyline_width_pixels` to the chart so they sum
coherently with closed-annotation areas. The default width is `1` pixel.
Set the width to whatever value your downstream training pipeline uses
when it rasterizes polylines -- the chart will then reflect the actual
labelled-pixel coverage your model will see, not the geometric length.

If you are training with the DL Pixel Classifier extension, its default
brush width is 5 pixels -- set the polyline width here to 5 to match.

The Detection Training dialog has no polyline-width control: polylines do
not label detections, so they never contribute to that chart.

### Tune the over- / under-representation highlight

A class is highlighted using a multiplicative **ratio** against the
global median class share. A class is **over-represented** when its
share is at least N times the median class share, and
**under-represented** when its share is at most 1/N times the median.
The default ratio N is 2.0x. The Advanced "Highlight ratio" slider runs
from 1.5x to 10.0x. The ratio is symmetric in log space, so a small
class is just as likely to be flagged under-represented as a large class
is to be flagged over-represented.

Lower the ratio (toward 1.5x) if you want any real imbalance flagged;
raise it (toward 10x) if everything is highlighted and the visual signal
is lost. Highlighting is shown as a coloured drop-shadow aura around the
slice plus an `[over]` / `[under]` marker on the legend label; the slice
itself keeps its class colour.

### Check object-classifier training balance

Open `Extensions > Class Distribution > Show Detection Training
Distribution...`. This dialog charts how many detections each class
would label given the current training annotations, matching QuPath's
Object Classifier logic: an area annotation with a class labels every
detection inside its ROI; a point annotation (Counting tool) with a
class labels the detection at each point; lines and polylines do not
label detections. The status line reads "N labeled detections (via P
training annotations) across M images." Use it before training a
classifier to see whether the training set is balanced.

### Split multi-part classifications

(Detection dialog only.) Open the Advanced section and tick **Split
multi-part classifications**. A composite class such as `CD4: CD8` then
contributes its labeled-detection count to both `CD4` and `CD8`
separately; unticked, it stays its own composite class. This mirrors
QuPath's "Distance to annotations 2D" command. The All images grid in
the detection dialog honours this checkbox too.

### Switch between Image Types

Use the Image Type dropdown to filter the charts. The dropdown lists
every `ImageType` value present in QuPath plus an `Unset` entry (for
images whose ImageType has not been assigned). The default is the
currently open image's type, or -- if no image is open or the type is
`UNSET` -- the most common type in the project. `Unset` is selectable
but never auto-default.

</details>

<details>
<summary><b>Advanced features</b></summary>

**Scripting API.** *Not yet available.* Neither dialog has a scripting
entry point. If you need to open a chart or trigger a refresh from a
Groovy workflow, file an issue describing the use case.

**Exporting the chart.** *Not yet available.* Export of a chart as a PNG
or of the underlying counts as a CSV is on the nice-to-have list. For
now, screenshot the dialog or read the values from the chart's tooltips.

**Per-class custom thresholds.** *Not yet available.* The highlight
ratio is a single global setting that applies to every slice. Per-class
thresholds are a common power-user request; please file an issue if you
need them.

</details>

<details>
<summary><b>Settings and preferences</b></summary>

The Advanced section (collapsible) holds these controls. Some appear in
only one dialog, as noted.

| Preference | Default | What it does |
|---|---|---|
| Image Type filter | computed: open image type, else most-common in project, else "All types" | Restricts the charts to images of this type. `Unset` is selectable but never auto-default. |
| Polyline width (pixels) | `1` | *Annotation dialog only.* Multiplied by polyline length to estimate area-equivalent coverage. Set to your training pipeline's rasterization width. |
| Highlight ratio | `2.0x` | Multiplicative threshold against the global median class share. A class is over-represented when its share is at least N times the median, under-represented when at most 1/N times the median. Slider runs 1.5x to 10.0x. Lower it for any real imbalance, raise it if the chart is too noisy. |
| Over-representation colour | `#0072B2` (Okabe-Ito blue) | Aura / marker colour for over-represented classes. Colourblind-safe default; user-pickable. |
| Under-representation colour | `#D55E00` (Okabe-Ito vermilion) | Aura / marker colour for under-represented classes. Colourblind-safe default; user-pickable. |
| Show slice labels on chart | `true` | Whether to draw the percent label on each slice. Auto-suppressed when more than ten classes are present (the legend still shows everything). |
| Show Project and Current Image side-by-side | `false` | Swaps the tabbed display for a horizontal split with a draggable divider. While on, the All images tab is unavailable. |
| Split multi-part classifications | `false` | *Detection dialog only.* When on, a composite class such as `CD4: CD8` contributes its count to both `CD4` and `CD8` separately. |
| Thumbnail size (pixels) | mid-range | *All images tab.* Slider (60-240 px) resizes every mini-chart thumbnail together. |
| Advanced section expanded | `false` | Whether the collapsible Advanced section is open at startup. |
| Per-user persistence | yes | All settings above plus the dialog's last position and size are persisted between QuPath sessions. Per-project persistence (different filter for project A vs B) is on the nice-to-have list for a future release. |

</details>

<details>
<summary><b>Troubleshooting</b></summary>

**"Numbers look wrong on a project with mixed pixel sizes."**
The annotation chart sums raw pixel quantities across all selected
images -- it does **not** convert to physical units. If your project
mixes a 20x and 40x slide of the same tissue, the 40x slide will look 4x
larger in the chart even if the annotated tissue is the same.
Workaround: filter the chart to one Image Type whose images all share
the same magnification. (A physical-units toggle that converts to
micrometre squared is on the nice-to-have list for a future release.)

**"Chart is empty even though I have annotations."**
For the annotation dialog, three causes, in order of likelihood: (1) the
Image Type filter is set to a type that no project image has -- check
the dropdown. (2) The annotations have no class assigned -- they fall
into the `Unclassified` bucket; look for a single big slice rather than
zero slices. (3) The annotations are points -- point ROIs do not
contribute to the annotation chart (no area, no length); this is
intentional. For the Detection Training dialog, an empty chart usually
means there are no detections, or no *training* annotations: only area
and point annotations with a class label detections; lines and
polylines do not.

**"Pie chart slice labels overlap."**
JavaFX pie charts get illegible above about ten classes. The chart
auto-suppresses slice labels when the class count exceeds ten and falls
back to a side legend instead. If the legend itself is too tall, resize
the dialog. (A companion bar-chart view for high class counts is on the
nice-to-have list.)

**"I clicked Refresh and the dialog is unresponsive."**
The refresh runs on a background thread; the dialog should stay
responsive and the spinner should advance. Expect roughly 1-3 minutes
per 100 images on typical hardware (each image is fully loaded so the
chart can also know its Image Type); press Cancel to stop early if it
is too slow -- the chart keeps whichever images were already loaded.
If the dialog is genuinely frozen for more than a few seconds AND the
spinner is not advancing, you have hit a bug -- file an issue with the
project size and the QuPath log (`Help > Show log`).

**"Highlight colours are hard to distinguish."**
Each slice is drawn in its own class colour; highlighting is conveyed by
a coloured drop-shadow aura around the slice and an `[over]` / `[under]`
marker on the legend label. The default aura palette is the Okabe-Ito
blue / vermilion pair (a standard colourblind-safe "good vs bad" pair).
If they do not separate cleanly on your display, open the Advanced
section and pick alternative colours via the two ColorPicker controls.
The picker choice persists between sessions.

**"What colour is each slice?"**
Slices use the class's actual QuPath `PathClass` colour, so the chart
matches the colours you see on the image. The `Unclassified` slice uses
QuPath's `colorDefaultObjects` preference -- the same colour QuPath
paints unclassified annotations with (red by default). On the Current
image tab, classes that exist in the project but have no annotations in
the open image appear in the legend with a yellow `[missing]` marker and
no slice.

**"I renamed a class and now the chart shows two slices for it."**
The cache is keyed on the class label as it was when the project was
last refreshed. After renaming a `PathClass` mid-session, click
`Refresh from project` so the cache picks up the new name. Otherwise
the chart will show both old and new as separate slices until reload.
(Switching images and back without saving also leaves the cache in
"live" state for the prior image; refresh to update.)

**"The dirty-image warning banner is showing but I just saved."**
The banner reads `Displayed distribution reflects unsaved changes to
this image` and is driven by QuPath's own "is the image hierarchy
modified" flag. If the banner persists after a successful save, it is
most likely a race between the save callback and the chart's listener.
Workaround: switch to another image and back -- this re-reads the
on-disk hierarchy and clears the flag. File an issue if it recurs.

</details>

<details>
<summary><b>Known limitations</b></summary>

- Raw-pixel summation across mixed-pixel-size projects is approximate;
  filter to one Image Type whose images share a magnification for
  accurate results.
- Slice labels suppress above ten classes; the legend takes over.
- No CSV / PNG export.
- No scripting API; both dialogs open from the menu only.
- The highlight ratio is one global setting; no per-class thresholds.
- Length-only / area-only / combined toggles and a companion bar-chart
  view for high class counts are on the nice-to-have list.
- The All images grid is available only in tabbed mode; it is hidden
  while side-by-side mode is active.

</details>
