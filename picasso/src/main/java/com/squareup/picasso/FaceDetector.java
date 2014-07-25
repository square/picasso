package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.Point;
import java.util.List;

public interface FaceDetector {
  /**
   * Find faces within bitmap
   *
   * @param source input bitmap, would not be recycled in function
   * @param maxFaces max faces for face detector in bitmap
   * @return face detected result
   */
  List<Face> findFaces(Bitmap source, int maxFaces);

  /**
   * A generic Face definition class for {@link FaceDetector}
   */
  public static class Face {
    public final Point leftTopPoint;
    public final int width;
    public final int height;

    public Face(int width, int height, Point leftTopPoint) {
      this.width = width;
      this.height = height;
      this.leftTopPoint = leftTopPoint;
    }
  }
}
