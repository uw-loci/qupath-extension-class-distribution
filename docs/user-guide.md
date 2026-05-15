# Class Distribution -- User Guide

This guide walks through the chart one task at a time. Sections are
collapsible; expand the ones you need. The "Getting started" section is
expanded by default; everything else is collapsed.

## Vocabulary

The chart uses four QuPath terms that have specific meanings in this
extension:

- **Annotation:** A region you have drawn on an image in QuPath -- a
  polygon, rectangle, ellipse, line, or polyline. The chart counts
  annotations across every image in the project.
- **Class:** A label assigned to an annotation, e.g. "Tumour", "Stroma".
  Annotations with no class assigned go into a single "Unclassified"
  bucket on the chart.
- **Image Type:** A QuPath property of each image (Brightfield H&E,
  Brightfield other, Fluorescence, etc.) set when an image is added to
  the project. The chart can be filtered to one Image Type.
- **Polyline:** A multi-segment line annotation (not a closed shape).
  Polylines have length but no area, so the chart estimates their pixel
  coverage as `length * polyline-width-pixels`.

<details open>
<summary><b>Getting started (read this first)</b></summary>

**Overview.** The Class Distribution chart shows you, at a glance, how
balanced the annotated classes are across your project. Each pie slice is
one class; slice size is the total annotated quantity for that class
summed across every project image whose Image Type matches the dropdown
filter.

**Prerequisites.** A QuPath project. At least one image in the project
should have classified annotations -- without classifications the chart is
empty. Annotations with no class are bucketed under `Unclassified` (a
single combined slice) so you can see how much of your annotation effort
is unlabelled.

**First-run walkthrough.** On first open, the dialog runs a one-time
refresh of every image in the project on a background thread. A spinner
shows that the refresh is in progress; the Refresh button label morphs to
`Refreshing 7/12...` so you can see how far it has got, and a Cancel
button appears next to it if you need to stop. Once the refresh
completes, the chart renders, listening starts on the open image, and
subsequent annotation edits update the chart instantly.

</details>

<details>
<summary><b>Common tasks</b></summary>

### Check class balance while annotating (the primary use case)

You annotate as normal; every time you add, edit, or delete an annotation
on the open image, the chart updates within a frame. Watch the slice
highlighting -- if the class you are currently annotating turns red, you
are still under-represented relative to the others; if it turns cyan, you
have over-shot. The point is to notice imbalance early enough to fix it
before you export.

### Refresh the project after running a batch script

The chart's live update only covers the currently open image. If you ran
a Groovy script via `Run > Run for project` that mutated annotations on
other images, click `Refresh from project` to walk the project from disk
again. A spinner shows progress; the dialog stays responsive (the
refresh runs on a background thread). A Cancel button next to the
Refresh button appears while the refresh is running -- press it to stop
early, and the chart keeps whichever images were loaded before you
cancelled.

### Adjust polyline width to match your training pipeline

Polyline annotations contribute `length_pixels * polyline_width_pixels`
to the chart so they sum coherently with closed-annotation areas. The
default width is `1` pixel. Set the width to whatever value your
downstream training pipeline uses when it rasterizes polylines -- the
chart will then reflect the actual labelled-pixel coverage your model
will see, not the geometric length.

If you are training with the DL Pixel Classifier extension, its default
brush width is 5 pixels -- set the polyline width here to 5 to match.

### Tune the over- / under-representation threshold

A class is highlighted as over-represented (Okabe-Ito blue by default)
when its slice is more than `threshold` percent above the median of the
other slices, and under-represented (Okabe-Ito vermilion by default)
when it is more than `threshold` percent below. Default threshold is
30% (a slice highlights when it is at least roughly 1.3x or 0.7x the
median). Lower the threshold (~20%) if you want any real imbalance
flagged; raise it (~50%+) if everything is highlighted and the visual
signal is lost.

### Switch between Image Types

Use the Image Type dropdown to filter the chart. The dropdown lists
every `ImageType` value present in QuPath plus an `Unset` entry (for
images whose ImageType has not been assigned). The default is the
currently open image's type, or -- if no image is open or the type is
`UNSET` -- the most common type in the project. `Unset` is selectable
but never auto-default.

</details>

<details>
<summary><b>Advanced features</b></summary>

**Scripting API.** *Deferred from v0.1.0.* The dialog has no scripting
entry point yet. If you need to open the chart or trigger a refresh
from a Groovy workflow, file an issue describing the use case.

**Exporting the chart.** *Deferred from v0.1.0.* Export of the chart as
a PNG or of the underlying counts as a CSV is on the nice-to-have list.
For now, screenshot the dialog or read the values from the chart's
tooltips.

**Per-class custom thresholds.** *Deferred from v0.1.0.* All v0.1.0
supports is a single global threshold. Per-class thresholds are a common
power-user request; please file an issue if you need them.

</details>

<details>
<summary><b>Settings and preferences</b></summary>

| Preference | Default | What it does |
|---|---|---|
| Image Type filter | computed: open image type, else most-common in project, else "All types" | Restricts the chart to images of this type. `Unset` is selectable but never auto-default. |
| Polyline width (pixels) | `1` | Multiplied by polyline length to estimate area-equivalent coverage. Set to your training pipeline's rasterization width. |
| Highlight threshold (percent) | `30` | A class slice is flagged when it is at least `threshold` percent above or below the median of the other slices. Default chosen to flag a class that is at least roughly 1.3x or 0.7x the median; tighten to 20% for any real imbalance, loosen to 50%+ if the chart is too noisy. |
| Over-representation colour | `#0072B2` (Okabe-Ito blue) | Slice colour for over-represented classes. Colourblind-safe default; user-pickable. |
| Under-representation colour | `#D55E00` (Okabe-Ito vermilion) | Slice colour for under-represented classes. Colourblind-safe default; user-pickable. |
| Show slice labels on chart | `true` | Whether to draw the percent label on each slice. Auto-suppressed when more than ten classes are present (the legend below the chart still shows everything). |
| Advanced section expanded | `false` | Whether the collapsible Advanced section is open at startup. |
| Per-user persistence | yes | All eight preferences above plus the dialog's last position and size are persisted between QuPath sessions. Per-project persistence (different filter for project A vs B) is on the nice-to-have list for a future release. |

</details>

<details>
<summary><b>Troubleshooting</b></summary>

**"Numbers look wrong on a project with mixed pixel sizes."**
The chart sums raw pixel quantities across all selected images -- it
does **not** convert to physical units. If your project mixes a 20x and
40x slide of the same tissue, the 40x slide will look 4x larger in the
chart even if the annotated tissue is the same. Workaround: filter the
chart to one Image Type whose images all share the same magnification.
(A physical-units toggle that converts to micrometre squared is on the
nice-to-have list for a future release.)

**"Chart is empty even though I have annotations."**
Three causes, in order of likelihood: (1) the Image Type filter is set
to a type that no project image has -- check the dropdown. (2) The
annotations have no class assigned -- they fall into the `Unclassified`
bucket; look for a single big slice rather than zero slices. (3) The
annotations are points -- point ROIs do not contribute to the chart (no
area, no length); this is intentional.

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
The default palette is the Okabe-Ito blue / vermilion pair (a standard
colourblind-safe "good vs bad" pair) chosen for visibility on QuPath's
default dialog background. If they still do not separate cleanly on
your display, open the Advanced section and pick alternative colours
via the two ColorPicker controls. The picker choice persists between
sessions.

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
- No CSV / PNG export in v0.1.0.
- No scripting API in v0.1.0.
- Per-class thresholds, length-only / area-only / combined toggles, and a
  companion bar-chart view are all on the nice-to-have list.

</details>
