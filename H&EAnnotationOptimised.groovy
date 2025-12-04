/**
 * StarDist nucleus detection for Visium H&E PNGs
 * - Uses he_heavy_augment.pb
 * - Image calibration: ~1.06 µm / pixel
 * - StarDist analysis scale: 0.5 µm (works better with this model)
 */

import qupath.ext.stardist.StarDist2D
import qupath.lib.scripting.QP

// -------------------------
// PARAMETERS – EDIT THESE
// -------------------------

// Path to your StarDist model
def modelPath = "/Users/alanamullins/Downloads/he_heavy_augment.pb"

// Image calibration (PNG) in microns per pixel
double imagePixelSizeMicrons = 1.06

// StarDist analysis scale in microns per pixel
// (keep ~0.5 because this is what gave good nuclei)
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
// - If there are selected objects, use those
// - Otherwise, use all annotations
def pathObjects = QP.getSelectedObjects()
if (pathObjects.isEmpty())
    pathObjects = QP.getAnnotationObjects()

if (pathObjects.isEmpty()) {
    QP.getLogger().error("No annotations or selected parent objects found!")
    return
}
println "Running StarDist in ${pathObjects.size()} parent object(s)"


// -------------------------
// 2. Configure StarDist
// -------------------------

def stardist = StarDist2D
        .builder(modelPath)
        .normalizePercentiles(1, 99)
        .threshold(probThreshold)
        .pixelSize(stardistPixelSizeMicrons)   // analysis scale ~0.5 µm
        .measureShape()
        .measureIntensity()
        .build()

// -------------------------
// 3. Run detection
// -------------------------

stardist.detectObjects(imageData, pathObjects)
stardist.close()
println "StarDist detection finished."
println "All done!"


