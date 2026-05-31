package com.example.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.sqrt

object InpaintEngine {

    /**
     * Inpaints the masked regions of the [original] bitmap.
     * [mask] is a binary bitmap where pixels that match the mask condition (e.g. non-transparent or red)
     * indicate the regions to be removed and filled with surrounding context.
     */
    fun inpaint(original: Bitmap, mask: Bitmap): Bitmap {
        val width = original.width
        val height = original.height

        // Create a mutable copy of the original
        val result = original.copy(Bitmap.Config.ARGB_8888, true)

        // Find the bounding box of the mask to minimize processing area
        val bounds = findMaskBounds(mask, width, height) ?: return result

        // Expand bounds slightly to ensure we capture the boundary context
        val padding = 8
        val startX = (bounds.left - padding).coerceAtLeast(0)
        val startY = (bounds.top - padding).coerceAtLeast(0)
        val endX = (bounds.right + padding).coerceAtMost(width - 1)
        val endY = (bounds.bottom + padding).coerceAtMost(height - 1)

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskPixels = IntArray(width * height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        // Identify masked pixel coordinates within the expanded bounding box
        val maskedPoints = mutableListOf<Pair<Int, Int>>()
        val isMasked = BooleanArray(width * height)

        for (y in startY..endY) {
            for (x in startX..endX) {
                val index = y * width + x
                // Mask matches if it has a strong red element or is not fully transparent/black depending on color
                val mColor = maskPixels[index]
                val isMask = (Color.alpha(mColor) > 50 && (Color.red(mColor) > 100 || Color.green(mColor) > 100 || Color.blue(mColor) > 100))
                if (isMask) {
                    maskedPoints.add(Pair(x, y))
                    isMasked[index] = true
                }
            }
        }

        if (maskedPoints.isEmpty()) return result

        // Collect boundary (unmasked) pixels directly adjacent or close to the mask for interpolation
        val boundaryPixels = mutableListOf<BoundaryPoint>()
        val searchRadius = 16

        // To make search super fast, we look at the immediate boundaries of the masked area
        for (y in startY..endY) {
            for (x in startX..endX) {
                val idx = y * width + x
                if (!isMasked[idx]) {
                    // Check if it is adjacent to any masked pixel
                    var isNearMask = false
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            val ny = y + dy
                            val nx = x + dx
                            if (ny in 0 until height && nx in 0 until width) {
                                if (isMasked[ny * width + nx]) {
                                    isNearMask = true
                                    break
                                }
                            }
                        }
                        if (isNearMask) break
                    }
                    if (isNearMask) {
                        val col = pixels[idx]
                        boundaryPixels.add(
                            BoundaryPoint(
                                x = x,
                                y = y,
                                r = Color.red(col),
                                g = Color.green(col),
                                b = Color.blue(col)
                            )
                        )
                    }
                }
            }
        }

        // If no boundaries found, fall back to simple background matching
        if (boundaryPixels.isEmpty()) {
            return result
        }

        // Compute the average color of all boundary pixels for O(1) fast fallback when we don't find enough local boundaries
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        for (bp in boundaryPixels) {
            totalR += bp.r
            totalG += bp.g
            totalB += bp.b
        }
        val avgR = (totalR / boundaryPixels.size.coerceAtLeast(1)).toInt()
        val avgG = (totalG / boundaryPixels.size.coerceAtLeast(1)).toInt()
        val avgB = (totalB / boundaryPixels.size.coerceAtLeast(1)).toInt()

        // For each masked pixel, compute inverse-distance-weighted interpolation of nearby boundaries
        for (pt in maskedPoints) {
            val px = pt.first
            val py = pt.second

            var sumWeight = 0.0
            var sumR = 0.0
            var sumG = 0.0
            var sumB = 0.0
            var found = false

            // Scan a local window of size searchRadius around (px, py)
            val r = searchRadius
            for (dy in -r..r) {
                val ny = py + dy
                if (ny !in startY..endY) continue
                for (dx in -r..r) {
                    val nx = px + dx
                    if (nx !in startX..endX) continue

                    val idx = ny * width + nx
                    if (!isMasked[idx]) {
                        val distSq = dx * dx + dy * dy
                        if (distSq <= r * r) {
                            val d = sqrt(distSq.toDouble()).coerceAtLeast(0.5)
                            val weight = 1.0 / (d * d)
                            val col = pixels[idx]
                            sumWeight += weight
                            sumR += Color.red(col) * weight
                            sumG += Color.green(col) * weight
                            sumB += Color.blue(col) * weight
                            found = true
                        }
                    }
                }
            }

            if (found && sumWeight > 0.0) {
                val finalR = (sumR / sumWeight).toInt().coerceIn(0, 255)
                val finalG = (sumG / sumWeight).toInt().coerceIn(0, 255)
                val finalB = (sumB / sumWeight).toInt().coerceIn(0, 255)
                pixels[py * width + px] = Color.rgb(finalR, finalG, finalB)
            } else {
                pixels[py * width + px] = Color.rgb(avgR, avgG, avgB)
            }
        }

        // Apply a small selective Gaussian blur on the modified region to blend seamlessly
        blurModifiedRegion(pixels, width, height, maskedPoints)

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private data class BoundaryPoint(val x: Int, val y: Int, val r: Int, val g: Int, val b: Int)

    /**
     * Scans the mask to find physical constraints of binary active regions.
     */
    private fun findMaskBounds(mask: Bitmap, width: Int, height: Int): Rect? {
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (y in 0 until height) {
            for (x in 0 until width) {
                val mColor = pixels[y * width + x]
                // Active mask condition
                val isMask = (Color.alpha(mColor) > 50 && (Color.red(mColor) > 100 || Color.green(mColor) > 100 || Color.blue(mColor) > 100))
                if (isMask) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX == -1 || maxY == -1) return null
        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * Smooths out transition edges in the modified region using a localized box filter.
     */
    private fun blurModifiedRegion(pixels: IntArray, width: Int, height: Int, modifiedPoints: List<Pair<Int, Int>>) {
        val originalCopy = pixels.clone()
        val radius = 2

        for (pt in modifiedPoints) {
            val px = pt.first
            val py = pt.second

            var sumR = 0
            var sumG = 0
            var sumB = 0
            var count = 0

            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val ny = py + dy
                    val nx = px + dx
                    if (ny in 0 until height && nx in 0 until width) {
                        val col = originalCopy[ny * width + nx]
                        sumR += Color.red(col)
                        sumG += Color.green(col)
                        sumB += Color.blue(col)
                        count++
                    }
                }
            }

            if (count > 0) {
                pixels[py * width + px] = Color.rgb(sumR / count, sumG / count, sumB / count)
            }
        }
    }
}
