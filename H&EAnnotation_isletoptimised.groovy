/**
 * Two-pass StarDist nucleus detection + core / islet labelling for Visium H&E PNG TMAs
 *
 * - Pass 1: sensitive-but-filtered settings on cores (non-islet annotations)
 * - Pass 2: very sensitive settings on islets (annotations whose name contains "islet")
 *
 * - Uses he_heavy_augment.pb
 * - Image calibration: ~1.06 µm / pixel
 *
 * EXTRA:
 * - Each nucleus gets:
 *     Classification = parent annotation name (e.g. TMA1_core01 or TMA1_core01_islet02)
 *     CoreIndex      = core number parsed from name (core01 -> 1)
 *     IsletIndex     = islet number parsed from name (islet02 -> 2; NaN for non-islet)
 */

import qupath.ext.stardist.StarDist2D
import qupath.lib.scripting.QP
import qupath.lib.objects.classes.PathClassFactory

// -------------------------
// GENERAL PARAMETERS
// -------------------------

// Path to your StarDist model
def modelPath = "/Users/alanamullins/Downloads/he_heavy_augment.pb"

// Visium PNG calibration
double imagePixelSizeMicrons = 1.06

// StarDist analysis scale (separate for cores vs islets)
double stardistPixelSizeMicronsCores  = 0.55   // slightly finer for exocrine/ducts
double stardistPixelSizeMicronsIslets = 0.50   // more detail in islets

// -------------------------
// PASS-SPECIFIC PARAMETERS
// -------------------------

// Pass 1 (cores – exocrine, ducts)
double probThresholdCores = 0.18      // lower = more sensitive
double minAreaCores       = 6.0       // µm²
double maxAreaCores       = 400.0     // µm²
double minHemaODCores     = 0.06      // OD filter to remove junk; set 0.0 to disable

// Pass 2 (islets – very sensitive)
double probThresholdIslets = 0.10     // very low to catch faint/edge nuclei
double minAreaIslets       = 2.0      // µm² (allow small partial nuclei)
double maxAreaIslets       = 500.0    // µm²
double minHemaODIslets     = 0.0      // keep OFF so we don't lose pale endocrine nuclei

// -------------------------
// Helper: detect which annotations are "islets"
// (Name contains "islet", case-insensitive)
// -------------------------

boolean isIsletAnnotation(obj) {
    def n = obj.getName()
    return n != null && n.toLowerCase().contains("islet")
}

// -------------------------
// 1. Basic checks & setup
// -------------------------

def imageData = QP.getCurrentImageData()
if (imageData == null) {
    QP.getLogger().error("No image open!")
    return
}

QP.setPixelSizeMicrons(imagePixelSizeMicrons, imagePixelSizeMicrons)
println "Image pixel size set to ${imagePixelSizeMicrons} µm"

def allAnnotations = QP.getAnnotationObjects()
if (allAnnotations.isEmpty()) {
    QP.getLogger().error("No annotations found!")
    return
}

// Split annotations into cores vs islets
def isletAnnotations = allAnnotations.findAll { isIsletAnnotation(it) }
def coreAnnotations  = allAnnotations.findAll { !isIsletAnnotation(it) }

println "Found ${coreAnnotations.size()} core annotation(s) and ${isletAnnotations.size()} islet annotation(s)."

def hierarchy = imageData.getHierarchy()

// -------------------------
// Helper function: run StarDist on a set of annotations
// with given threshold, size filter, and optional hematoxylin filter
// -------------------------

void runStardistOnAnnotations(
        def imageData,
        def pathObjects,
        double probThreshold,
        double minAreaUm2,
        double maxAreaUm2,
        double analysisPixelSizeMicrons,
        double imagePixelSizeMicrons,
        String labelType,    // "core" or "islet" – only used for printouts
        String modelPath,
        double minHemaOD     // 0.0 to disable hematoxylin-based filtering
) {
    if (pathObjects == null || pathObjects.isEmpty()) {
        println "No ${labelType} annotations to process."
        return
    }

    def hierarchy = imageData.getHierarchy()

    // Clear existing detections under these parents
    def existingDetections = QP.getDetectionObjects().findAll { det ->
        pathObjects.contains(det.getParent())
    }
    if (!existingDetections.isEmpty()) {
        hierarchy.removeObjects(existingDetections, true)
        println "Removed ${existingDetections.size()} existing detections in ${labelType} annotation(s)."
    }

    // Configure StarDist
    def builder = StarDist2D
            .builder(modelPath)
            .normalizePercentiles(1, 99)
            .threshold(probThreshold)
            .pixelSize(analysisPixelSizeMicrons)
            .measureShape()
            .measureIntensity()

    // constrainToParent if available
    try {
        builder = builder.constrainToParent(true)
    } catch (Throwable t) {
        println "constrainToParent(true) not supported – continuing without it."
    }

    def stardist = builder.build()

    // Run detection
    stardist.detectObjects(imageData, pathObjects)
    stardist.close()
    println "StarDist detection finished for ${labelType} annotation(s) with probThreshold = ${probThreshold}, pixelSize = ${analysisPixelSizeMicrons} µm."

    // Get detections just created
    def detections = QP.getDetectionObjects().findAll { det ->
        pathObjects.contains(det.getParent())
    }
    println "Detected ${detections.size()} nuclei in ${labelType} annotation(s) before filtering."

    // -----------------------------------------
    // Label detections & add core/islet indices
    // -----------------------------------------
    int nLabeled = 0
    detections.each { det ->
        def parent = det.getParent()
        if (parent != null && pathObjects.contains(parent)) {
            def parentName = parent.getName()
            if (parentName != null) {
                // 1) Classification = parent annotation name (core or islet)
                det.setPathClass(PathClassFactory.getPathClass(parentName))
                nLabeled++

                // 2) Use Classification name to parse indices
                def cls = det.getPathClass()
                def clsName = cls == null ? null : cls.getName()
                double coreIndex  = Double.NaN
                double isletIndex = Double.NaN

                if (clsName != null) {
                    // Match patterns like:
                    //  TMA1_core01
                    //  TMA1_core01_islet02
                    //  TMA12_core03_islet01
                    def m = (clsName =~ /(?i)TMA\d+_core0*(\d+)(?:_islet0*(\d+))?/)
                    if (m.matches()) {
                        coreIndex = (m[0][1] as double)
                        if (m[0].size() > 2 && m[0][2] != null) {
                            isletIndex = (m[0][2] as double)
                        }
                    }
                }

                // 3) Store indices as measurements (numeric columns)
                det.getMeasurementList().putMeasurement("CoreIndex", coreIndex)
                det.getMeasurementList().putMeasurement("IsletIndex", isletIndex)
            }
        }
    }
    println "Set Classification and core/islet indices for ${nLabeled} ${labelType} nuclei."

    // Size filter
    def toRemoveSize = detections.findAll { det ->
        def areaPx = det.getROI().getArea()
        def areaUm2 = areaPx * imagePixelSizeMicrons * imagePixelSizeMicrons
        return (areaUm2 < minAreaUm2) || (areaUm2 > maxAreaUm2)
    }
    if (!toRemoveSize.isEmpty()) {
        hierarchy.removeObjects(toRemoveSize, true)
        println "Removed ${toRemoveSize.size()} ${labelType} detections outside area range ${minAreaUm2}–${maxAreaUm2} µm²."
    }

    // Optional hematoxylin OD filter
    if (minHemaOD > 0) {
        def remaining = QP.getDetectionObjects().findAll { det ->
            pathObjects.contains(det.getParent())
        }
        def toRemoveHema = remaining.findAll { det ->
            def parent = det.getParent()
            if (parent == null || !pathObjects.contains(parent))
                return false
            def h = det.getMeasurementList().getMeasurementValue("Hematoxylin OD mean")
            return !Double.isNaN(h) && h < minHemaOD
        }
        if (!toRemoveHema.isEmpty()) {
            hierarchy.removeObjects(toRemoveHema, true)
            println "Removed ${toRemoveHema.size()} ${labelType} detections with Hematoxylin OD mean < ${minHemaOD}."
        }
    }

    println "Final ${labelType} nuclei count: " +
            QP.getDetectionObjects().count { det -> pathObjects.contains(det.getParent()) }
}

// -------------------------
// 2. PASS 1 – cores
// -------------------------

runStardistOnAnnotations(
        imageData,
        coreAnnotations,
        probThresholdCores,
        minAreaCores,
        maxAreaCores,
        stardistPixelSizeMicronsCores,
        imagePixelSizeMicrons,
        "core",
        modelPath,
        minHemaODCores
)

// -------------------------
// 3. PASS 2 – islets
// -------------------------

runStardistOnAnnotations(
        imageData,
        isletAnnotations,
        probThresholdIslets,
        minAreaIslets,
        maxAreaIslets,
        stardistPixelSizeMicronsIslets,
        imagePixelSizeMicrons,
        "islet",
        modelPath,
        minHemaODIslets
)

println "All done!"
