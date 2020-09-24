package com.squareup.picasso3

import android.graphics.Bitmap.Config
import com.squareup.picasso3.Request.Builder

class Rendering(builder: Builder) {
  /** Target image width for resizing. */
  @JvmField val targetWidth: Int = builder.targetWidth

  /** Target image height for resizing. */
  @JvmField val targetHeight: Int = builder.targetHeight

  /**
   * True if the final image should use the 'centerCrop' scale technique.
   *
   * This is mutually exclusive with [.centerInside].
   */
  @JvmField val centerCrop: Boolean = builder.centerCrop

  /** If centerCrop is set, controls alignment of centered image */
  @JvmField val centerCropGravity: Int = builder.centerCropGravity

  /**
   * True if the final image should use the 'centerInside' scale technique.
   *
   * This is mutually exclusive with [.centerCrop].
   */
  @JvmField val centerInside: Boolean = builder.centerInside

  @JvmField val onlyScaleDown: Boolean = builder.onlyScaleDown

  /** Amount to rotate the image in degrees. */
  @JvmField val rotationDegrees: Float = builder.rotationDegrees

  /** Rotation pivot on the X axis. */
  @JvmField val rotationPivotX: Float = builder.rotationPivotX

  /** Rotation pivot on the Y axis. */
  @JvmField val rotationPivotY: Float = builder.rotationPivotY

  /** Whether or not [.rotationPivotX] and [.rotationPivotY] are set. */
  @JvmField val hasRotationPivot: Boolean = builder.hasRotationPivot

  /** True if image should be decoded with inPurgeable and inInputShareable. */
  @JvmField val purgeable: Boolean = builder.purgeable

  /** Target image config for decoding. */
  @JvmField val config: Config? = builder.config
}