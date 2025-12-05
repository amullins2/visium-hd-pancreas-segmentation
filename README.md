# Visium HD pancreas TMA – H&E nuclear segmentation

This repository documents optimisation of H&E nuclear segmentation for a
Visium HD pancreas TMA dataset using QuPath v0.5.1 and the StarDist2D
`he_heavy_augment.pb` model. The aim is to obtain nuclear segmentations
suitable for near single-cell resolution mapping of Visium HD bins.

---

## 1. TMA overview and selected core

**Figure 1A. Whole Visium HD TMA**

![TMA overview](tma_overview.png)

**Figure 1B. Selected pancreas core**

![Core zoom](core_zoom.png)

---

## 2. High-magnification region of interest

**Figure 1C. H&E ROI (no segmentation)**

![ROI raw H&E](roi_raw.png)

---

## 3. StarDist threshold optimisation (single-pass)

All segmentations in this section use:

- QuPath v0.5.1  
- StarDist2D `he_heavy_augment.pb`  
- Image pixel size: 1.06 µm / pixel  
- StarDist analysis scale: 0.5 µm / pixel  

Only the detection threshold is varied.

**Figure 1D. Initial threshold (`probThreshold = 0.50`)**

![ROI StarDist threshold 0.50](roi_seg_thresh0_50.png)

**Figure 1E. Optimised threshold (`probThreshold = 0.30`)**

![ROI StarDist threshold 0.30](roi_seg_thresh0_30.png)

The optimised threshold captures dense, well-separated nuclei across endocrine
and exocrine regions and is used as the basis for subsequent near single-cell
Visium HD analysis.

The full optimisation figure is also available as:

[VisiumSegmentationOptimisation.pdf](VisiumSegmentationOptimisation.pdf)

---

## 4. Two-pass StarDist segmentation with islet-optimised ROIs

To obtain more accurate nuclear counts within endocrine islets while retaining
robust segmentation in surrounding exocrine tissue and ducts, a two-pass
StarDist workflow was implemented in QuPath.

### 4.1. Rationale

- Exocrine/duct nuclei stain strongly and are well segmented with moderately
  conservative StarDist settings.
- Islet nuclei are often paler and more heterogeneous, and benefit from a more
  sensitive configuration (lower detection threshold, relaxed size limits).
- Exact per-islet cell counts are required for downstream Visium HD analysis,
  so islets are handled separately via dedicated ROIs.

### 4.2. Workflow

1. In QuPath, draw one **core annotation** per tissue core  
   (e.g. `TMA1_core01`).
2. Within each core, draw one or more **islet annotations** whose names contain
   the string `islet` (case-insensitive), e.g.:  
   `TMA1_core01_islet01`, `TMA1_core01_islet02`, …
3. Run the two-pass script:

   - **Pass 1 – cores (exocrine + ducts):**
     - Runs StarDist on all non-islet annotations with moderately sensitive
       parameters.
     - Applies size filtering and an optional hematoxylin OD filter to remove
       clear non-nuclear detections.
   - **Pass 2 – islets (endocrine):**
     - Re-runs StarDist only inside islet annotations using a lower probability
       threshold and relaxed size limits to better capture faint and edge
       nuclei.
     - Clears any previous detections in those islet ROIs before re-segmentation.

4. For all nuclei, the script sets the **Classification** to the parent
   annotation name (e.g. `TMA1_core01` or `TMA1_core01_islet02`), enabling
   straightforward per-core and per-islet counts from the exported
   measurements.

### 4.3. Example output

**Figure 2. Two-pass segmentation of a Visium HD pancreas core**

![Optimised islet nuclear segmentation](roi_seg_isletoptimised.png)

- Yellow outlines: nuclei detected in the core (exocrine + ducts).  
- Blue / green outlines: nuclei detected within islet ROIs using
  islet-optimised StarDist settings.

This configuration provides high-recall islet nuclear segmentation suitable for
per-islet cell counting while maintaining good performance in exocrine tissue.

---
````markdown
## 5. QuPath StarDist scripts

Two Groovy scripts are included in this repository:

- `H&EAnnotationOptimised.groovy` – single-pass StarDist segmentation for all
  annotations (global “best compromise” settings).
- `H&EAnnotation_isletoptimised.groovy` – two-pass StarDist segmentation with
  different settings for cores vs islets, tuned for accurate per-islet counts.

---

### 5.1. Single-pass segmentation (`H&EAnnotationOptimised.groovy`)

This script applies one StarDist configuration to all selected parent objects
(or all annotations if none are selected). It corresponds to the global,
single-pass optimisation described in Sections 3–4.

**Script:** [`H&EAnnotationOptimised.groovy`](H&EAnnotationOptimised.groovy)

**Key parameters:**

```groovy
// Path to StarDist model
def modelPath = "/Users/alanamullins/Downloads/he_heavy_augment.pb"

// Image calibration
double imagePixelSizeMicrons    = 1.06   // µm / pixel

// StarDist analysis scale
double stardistPixelSizeMicrons = 0.5    // µm / pixel

// Detection threshold (lower = more nuclei, higher = fewer nuclei)
double probThreshold            = 0.28
````

**Behaviour:**

* Overrides the pixel size of the current image for consistent measurements.
* Uses percentile normalisation (`normalizePercentiles(1, 99)`).
* Runs StarDist on:

  * all selected annotations, or
  * all annotations if nothing is selected.
* Measures nuclear shape and intensity.
* Does **not** apply extra size or intensity filtering.
* Does **not** distinguish between exocrine and islet regions or modify
  `PathClass` for detections.

Use this script when a single global configuration is sufficient across the
whole core and per-islet optimisation is not required.

---

### 5.2. Two-pass islet-optimised segmentation (`H&EAnnotation_isletoptimised.groovy`)

This script implements a two-pass workflow where cores (exocrine + ducts) and
islets are segmented separately with distinct StarDist settings. It is used to
obtain higher recall in endocrine islets while maintaining good segmentation
in surrounding exocrine tissue.

**Script:** [`H&EAnnotation_isletoptimised.groovy`](H&EAnnotation_isletoptimised.groovy)

#### General settings

```groovy
// Path to StarDist model
def modelPath = "/Users/alanamullins/Downloads/he_heavy_augment.pb"

// Image calibration
double imagePixelSizeMicrons = 1.06   // µm / pixel

// StarDist analysis scales
double stardistPixelSizeMicronsCores  = 0.55  // cores: exocrine + ducts
double stardistPixelSizeMicronsIslets = 0.50  // islets: slightly finer
```

Annotations are split based on their **name**:

* **Islet annotations:** name contains `"islet"` (case-insensitive), e.g.
  `TMA1_core01_islet01`.
* **Core annotations:** all other annotations (e.g. `TMA1_core01`).

Existing detections under these annotations are removed before each pass.

#### Pass 1 – cores (exocrine + ducts)

```groovy
// Core (non-islet) parameters
double probThresholdCores = 0.18     // more sensitive than single-pass
double minAreaCores       = 6.0      // µm²
double maxAreaCores       = 400.0    // µm²
double minHemaODCores     = 0.06     // optional Hematoxylin OD filter
```

* Runs StarDist on all **non-islet** annotations using moderately sensitive
  parameters to capture exocrine and ductal nuclei.
* Applies size filtering (`minAreaCores`–`maxAreaCores`) to remove tiny debris
  and very large merged objects.
* Optionally removes low–hematoxylin detections (`minHemaODCores`) to clean up
  non-nuclear artefacts in exocrine/ductal regions.

#### Pass 2 – islets (endocrine ROIs)

```groovy
// Islet parameters (more sensitive)
double probThresholdIslets = 0.10    // very low to capture faint/edge nuclei
double minAreaIslets       = 2.0     // µm² (allow small partial nuclei)
double maxAreaIslets       = 500.0   // µm²
double minHemaODIslets     = 0.0     // OD filter OFF for islets
```

* Runs StarDist **only** on annotations whose name contains `"islet"`.
* Clears and re-segments nuclei within those islet ROIs using more sensitive
  settings (lower threshold, relaxed area limits).
* Leaves hematoxylin OD filtering disabled (`minHemaODIslets = 0.0`) to avoid
  discarding pale endocrine nuclei.

#### Classification and downstream counting

For both passes:

* Each detection is assigned a `PathClass` equal to its parent annotation name
  (e.g. `TMA1_core01` or `TMA1_core01_islet02`).
* Final nucleus counts per pass are printed to the QuPath log.

Exported detection measurements can then be grouped by the `Classification`
column to obtain per-core and per-islet nuclear counts for downstream Visium
HD analyses.

```
```


