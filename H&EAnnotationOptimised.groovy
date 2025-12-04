/**
 * StarDist nucleus detection + core labelling for Visium H&E PNG TMAs
 *
 * - Uses he_heavy_augment.pb
 * - Image calibration: ~1.06 µm / pixel
 * - StarDist analysis scale: 0.5 µm
 *
 * Workflow per TMA image:
 *  1) Draw one rectangular annotation per tissue core (or ROI inside each core)
 *  2) Rename each annotation to a meaningful core ID (e.g. "TMA1_WT_core01")
 *  3) Run this script:
 *       - Runs StarDist in all (or selected) core annotations
 *       - Clears old detections in those cores (if any)
 *       - Sets each nucleus Classification = parent annotation name
 *  4) Export detection measurements (Classification column will have core ID)
 */

import qupath.ext.stardist.StarDist2D
import qupath.lib.scripting.QP
import qupath.lib.objects.classes.PathClassFactory

// -------------------------
// PARAMETERS – EDIT THESE
// -------------------------

// Path to your StarDist model
def modelPath = "/Users/alanamullins/Downloads/he_heavy_augment.pb"

// Image calibration (Visium PNG) in microns per pixel
double imagePixelSizeMicrons = 1.06

// StarDist analysis scale in microns per pixel
double stardistPixelSizeMicrons = 0.5

// StarDist detection threshold (lower = more nuclei, higher = fewer nuclei)
double probThreshold = 0.28



// -------------------------
// 1. Basic checks & setup
// -------------------------

def imageData = QP.getCurrentImageData()
if (imageData == null) {
    QP.getLogger().error("No image open!")
    return
}

// Set / override pixel calibration for this image (for measurements)
QP.setPixelSizeMicrons(imagePixelSizeMicrons, imagePixelSizeMicrons)
println "Image pixel size set to ${imagePixelSizeMicrons} µm"

// Decide where to run StarDist:
//
// - If any annotations are selected, use those (run only on selected cores/ROIs)
// - Otherwise, use ALL annotation objects in the image (all cores/ROIs)
def selected = QP.getSelectedObjects().findAll { it.isAnnotation() }
def pathObjects = selected.isEmpty() ? QP.getAnnotationObjects() : selected

if (pathObjects.isEmpty()) {
    QP.getLogger().error("No annotations found! Draw and rename core/ROI annotations, then try again.")
    return
}
println "Running StarDist in " + pathObjects.size() + " parent annotation(s)"

// OPTIONAL: clear existing detections whose parent is one of these annotations
def hierarchy = imageData.getHierarchy()
def allDetections = QP.getDetectionObjects()
def toDelete = allDetections.findAll { det ->
    pathObjects.contains(det.getParent())
}
if (!toDelete.isEmpty()) {
    hierarchy.removeObjects(toDelete, true)
    println "Removed " + toDelete.size() + " existing detections before re-running StarDist."
}



// -------------------------
// 2. Configure StarDist
// -------------------------

def stardist = StarDist2D
        .builder(modelPath)
        .normalizePercentiles(1, 99)            // comment out if you really want to avoid warnings
        .threshold(probThreshold)
        .pixelSize(stardistPixelSizeMicrons)    // analysis scale
        .measureShape()
        .measureIntensity()
        .build()



// -------------------------
// 3. Run detection
// -------------------------

stardist.detectObjects(imageData, pathObjects)
stardist.close()
println "StarDist detection finished."


// -------------------------
// 4. Set Classification = parent core/ROI name
// -------------------------

def detections = QP.getDetectionObjects()
if (detections.isEmpty()) {
    QP.getLogger().warn("No detections found after StarDist – nothing to label.")
} else {
    int nUpdated = 0

    detections.each { det ->
        def parent = det.getParent()
        // Only touch detections whose parent is one of the annotations we used
        if (parent != null && parent.isAnnotation() && pathObjects.contains(parent)) {
            def coreName = parent.getName()
            if (coreName != null) {
                det.setPathClass(PathClassFactory.getPathClass(coreName))
                nUpdated++
            }
        }
    }

    println "Updated Classification for " + nUpdated + " detections (set to parent annotation name)."
}

println "All done!"


