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

## 3. StarDist threshold optimisation

All segmentations use:

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
and exocrine regions and is used for subsequent near single-cell Visium HD analysis.

The full optimisation figure is also available as:

[VisiumSegmentationOptimisation.pdf](VisiumSegmentationOptimisation.pdf)

---

## 4. StarDist script

The Groovy script used in QuPath is stored as
[`stardist_visium_nuclei.groovy`](stardist_visium_nuclei.groovy).

Key parameters:

```groovy
def modelPath = "/path/to/he_heavy_augment.pb"
double imagePixelSizeMicrons    = 1.06
double stardistPixelSizeMicrons = 0.5
double probThreshold            = 0.30
