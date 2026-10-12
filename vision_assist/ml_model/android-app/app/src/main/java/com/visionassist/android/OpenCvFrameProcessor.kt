package com.visionassist.android

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/** Processes frames supplied by a future MediaProjection capture pipeline. */
class OpenCvFrameProcessor {
    fun process(input: Bitmap, profile: VisualProfile): Bitmap {
        val source = Mat()
        val result = Mat()
        Utils.bitmapToMat(input, source)

        if (profile.clahe) {
            val rgb = Mat()
            val lab = Mat()

            // 1. Convert RGBA -> RGB
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)

            // 2. Convert RGB -> Lab
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)

            val channels = ArrayList<Mat>(3)
            org.opencv.core.Core.split(lab, channels)

            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(channels[0], channels[0])

            org.opencv.core.Core.merge(channels, lab)

            // 3. Convert Lab -> RGB
            Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)

            // 4. Convert RGB -> RGBA
            Imgproc.cvtColor(rgb, result, Imgproc.COLOR_RGB2RGBA)

            // Clean up temporary Mats
            rgb.release()
            lab.release()
            channels.forEach(Mat::release)
        } else {
            source.copyTo(result)
        }

        result.convertTo(result, -1, profile.contrast.toDouble(), profile.brightness.toDouble())
        if (profile.sharpness > 0f) {
            val blurred = Mat()
            Imgproc.GaussianBlur(result, blurred, Size(), 1.0)
            org.opencv.core.Core.addWeighted(
                result,
                1.0 + profile.sharpness,
                blurred,
                -profile.sharpness.toDouble(),
                0.0,
                result,
            )
            blurred.release()
        }

        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(result, output)
        source.release()
        result.release()
        return output
    }
}
