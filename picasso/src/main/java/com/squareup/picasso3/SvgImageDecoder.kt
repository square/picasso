package com.squareup.picasso3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import okio.BufferedSource
import java.io.BufferedInputStream
import java.io.IOException


internal class SvgImageDecoder : ImageDecoder {
    override fun canHandleSource(source: BufferedSource?): Boolean {
        val source = source!!.peek()
        return try {
            SVG.getFromInputStream(BufferedInputStream(source.inputStream()))
            true
        } catch (e: SVGParseException) {
            Log.e("Test", "Failed to parse SVG: " + e.message, e)
            false
        }
    }

    @Throws(IOException::class)
    override fun decodeImage(source: BufferedSource?, request: Request?): ImageDecoder.Image? {
        val source = source!!.peek()
        return try {
            val svg: SVG = SVG.getFromInputStream(BufferedInputStream(source.inputStream()))
            if (request!!.hasSize()) {
                if (request.targetWidth != 0) {
                    svg.documentWidth = request.targetWidth.toFloat()
                    svg.documentHeight = request.targetHeight.toFloat()
                }
                if (request.targetHeight != 0) {
                    svg.documentHeight = request.targetHeight.toFloat()
                }
            }
            val width = svg.documentWidth.toInt()
            val height = svg.documentHeight.toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            svg.renderToCanvas(canvas)
            ImageDecoder.Image(bitmap)
        } catch (e: SVGParseException) {
            throw IOException(e)
        }
    }
}