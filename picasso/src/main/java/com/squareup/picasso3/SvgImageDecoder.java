package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import java.io.IOException;
import okio.BufferedSource;

class SvgImageDecoder implements ImageDecoder {

  @Override public boolean canHandleSource(BufferedSource source) {
    SourceBufferingInputStream wrapped = new SourceBufferingInputStream(source);
    try {
      SVG svg = SVG.getFromInputStream(wrapped);
      return true;
    } catch (SVGParseException e) {
      Log.e("Test", "Failed to parse SVG: " + e.getMessage(), e);
      return false;
    }
  }

  @Override public Image decodeImage(BufferedSource source, Request request) throws IOException {
    SourceBufferingInputStream wrapped = new SourceBufferingInputStream(source);
    try {
      SVG svg = SVG.getFromInputStream(wrapped);
      if (request.hasSize()) {
        if (request.targetWidth != 0) {
          svg.setDocumentWidth(request.targetWidth);
        }
        if (request.targetHeight != 0) {
          svg.setDocumentHeight(request.targetHeight);
        }
      }

      final int width = (int) svg.getDocumentWidth();
      final int height = (int) svg.getDocumentHeight();
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      svg.renderToCanvas(canvas);

      return new Image(bitmap);
    } catch (SVGParseException e) {
      throw new IOException(e);
    }
  }
}
