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

final class ColorMatrixes {
  public static final int ROW_RED = 0;
  public static final int ROW_GREEN = 5;
  public static final int ROW_BLUE = 10;
  public static final int ROW_ALPHA = 15;

  public static final int COLUMN_RED = 0;
  public static final int COLUMN_GREEN = 1;
  public static final int COLUMN_BLUE = 2;
  public static final int COLUMN_ALPHA = 3;
  public static final int COLUMN_TRANSLATE = 4;

  static void setContrast(ColorMatrix colorMatrix, float contrast) {
    float translate = (1f - contrast) / 2f;

    float[] array = colorMatrix.getArray();
    array[ROW_RED + COLUMN_RED] = contrast;
    array[ROW_GREEN + COLUMN_GREEN] = contrast;
    array[ROW_BLUE + COLUMN_BLUE] = contrast;

    array[ROW_RED + COLUMN_TRANSLATE] = translate;
    array[ROW_GREEN + COLUMN_TRANSLATE] = translate;
    array[ROW_BLUE + COLUMN_TRANSLATE] = translate;
  }

  private ColorMatrixes() {
  }
}
