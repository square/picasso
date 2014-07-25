package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import java.util.ArrayList;
import java.util.List;

public class AndroidFaceDetector implements FaceDetector {

  @Override
  public List<Face> findFaces(Bitmap source, int maxFaces) {
    int faceCount;
    List<Face> outFaces = new ArrayList<Face>();
    if (source == null) {
      return outFaces;
    }

    int w = source.getWidth();
    int h = source.getHeight();
    final float faceUpperRatio = 1.2f;
    final float faceWidthFactor = 2.2f;
    final float faceHeightFactor = 2.7f;

    android.media.FaceDetector faceDetector = new android.media.FaceDetector(w, h, maxFaces);
    android.media.FaceDetector.Face[] faces = new android.media.FaceDetector.Face[maxFaces];
    faceCount = faceDetector.findFaces(source, faces);
    PointF holder = new PointF();
    float eyeDistance;
    for (int i = 0; i < faceCount; i++) {
      if (faces[i] == null) {
        continue;
      }
      eyeDistance = faces[i].eyesDistance();
      faces[i].getMidPoint(holder);
      Point leftTopPoint =
          new Point(Math.max(0, (int) holder.x - ((int) (eyeDistance * faceWidthFactor) / 2)),
              Math.max(0, (int) holder.y - (int) (eyeDistance * faceUpperRatio)));
      outFaces.add(
          new Face((int) (eyeDistance * faceWidthFactor), (int) (eyeDistance * faceHeightFactor),
              leftTopPoint)
      );
    }
    return outFaces;
  }
}
