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

- Red outlines: nuclei detected in the core (exocrine + ducts).  
- Blue / green outlines: nuclei detected within islet ROIs using
  islet-optimised StarDist settings.

This configuration provides high-recall islet nuclear segmentation suitable for
per-islet cell counting while maintaining good performance in exocrine tissue.

---

## 5. QuPath StarDist scripts

Two Groovy scripts are included in this repository:

### 5.1. Single-pass segmentation script

The original single-pass H&E nuclear segmentation script is stored as:

- [`stardist_visium_nuclei.groovy`](stardist_visium_nuclei.groovy)

Key parameters:

```groovy
def modelPath = "/path/to/he_heavy_augment.pb"
double imagePixelSizeMicrons    = 1.06
double stardistPixelSizeMicrons = 0.5
double probThreshold            = 0.30

