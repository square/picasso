/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.graphics.ColorMatrix;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import static com.squareup.picasso.ColorMatrixes.COLUMN_ALPHA;
import static com.squareup.picasso.ColorMatrixes.ROW_ALPHA;

/**
 * A transition filter that is based on the suggested loading treatment for Material Design. This
 * transition adjusts alpha, saturation, and contrast.
 */
final class MaterialTransition implements ColorMatrixTransition {
  private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();
  private static final long DURATION = 1000; // ms

  private final ColorMatrix alphaMatrix;
  private final ColorMatrix contrastMatrix;
  private final ColorMatrix saturationMatrix;

  MaterialTransition() {
    alphaMatrix = new ColorMatrix();
    contrastMatrix = new ColorMatrix();
    saturationMatrix = new ColorMatrix();
  }

  @Override
  public long getDuration() {
    return DURATION;
  }

  @Override
  public void apply(ColorMatrix colorMatrix, float fraction) {
    // Alpha fade from 0 to 1 for the first 2/3 of the transition.
    float alpha = INTERPOLATOR.getInterpolation(Math.min(1f, fraction / 0.33f));
    alphaMatrix.getArray()[ROW_ALPHA + COLUMN_ALPHA] = alpha;

    // Contrast fade from 0 to 1 for the first half of the transition.
    float contrast = INTERPOLATOR.getInterpolation(Math.min(1f, fraction / 0.5f));
    ColorMatrixes.setContrast(contrastMatrix, contrast);

    // Saturation fade from 0.2 to 1 for the last two thirds of the transition.
    float saturation =
        0.2f + 0.8f * INTERPOLATOR.getInterpolation(Math.max(0, fraction - 0.33f) / 0.67f);
    saturationMatrix.setSaturation(saturation);

    colorMatrix.postConcat(saturationMatrix);
    colorMatrix.postConcat(alphaMatrix);
    colorMatrix.postConcat(contrastMatrix);
  }
}
