package com.squareup.picasso3.decoder.svg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.squareup.picasso3.ImageDecoder;
import com.squareup.picasso3.Request;
import java.io.IOException;
import okio.BufferedSource;

class SvgImageDecoder implements ImageDecoder {

  @Override public boolean canHandleSource(BufferedSource source) {
    try {
      SVG.getFromInputStream(source.peek().inputStream());
      return true;
    } catch (SVGParseException e) {
      return false;
    }
  }

  @Override public Image decodeImage(BufferedSource source, Request request) throws IOException {
    try {
      SVG svg = SVG.getFromInputStream(source.inputStream());
      if (request.hasSize()) {
        if (request.targetWidth != 0) {
          svg.setDocumentWidth(request.targetWidth);
        }
        if (request.targetHeight != 0) {
          svg.setDocumentHeight(request.targetHeight);
        }
      }

      int width = (int) svg.getDocumentWidth();
      if (width == -1) {
        width = (int) svg.getDocumentViewBox().width();
      }
      int height = (int) svg.getDocumentHeight();
      if (height == -1) {
        height = (int) svg.getDocumentViewBox().height();
      }
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      svg.renderToCanvas(canvas);

      return new Image(bitmap);
    } catch (SVGParseException e) {
      throw new IOException(e);
    }
  }
}
