package com.example.picasso;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import com.squareup.picasso.PlaceholderTransformation;

public class CropRoundTransformation implements PlaceholderTransformation {

  @Override public Bitmap transform(Bitmap source) {
    int size = Math.min(source.getWidth(), source.getHeight());
    int x = (source.getWidth() - size) / 2;
    int y = (source.getHeight() - size) / 2;
    Bitmap result = Bitmap.createBitmap(size, size, source.getConfig());

    final Canvas canvas = new Canvas(result);

    final int color = Color.RED;
    final Paint paint = new Paint();
    final Rect rect = new Rect(0, 0, result.getWidth(), result.getHeight());
    final RectF rectF = new RectF(rect);

    paint.setAntiAlias(true);
    canvas.drawARGB(0, 0, 0, 0);
    paint.setColor(color);
    canvas.drawOval(rectF, paint);

    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(source, rect, rect, paint);

    source.recycle();

    return result;
  }

  @Override public String key() {
    return "square()";
  }
}
