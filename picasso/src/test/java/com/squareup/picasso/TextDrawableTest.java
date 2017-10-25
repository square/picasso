package com.squareup.picasso;

import android.graphics.Paint;
import android.graphics.PointF;
import android.view.Gravity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricGradleTestRunner;

@RunWith(RobolectricGradleTestRunner.class)
public class TextDrawableTest {

  private final int WIDTH = 10;

  private final int HEIGHT = 16;

  private TextDrawable textDrawable;

  @Mock
  Paint paint;

  @Before
  public void init() {
    textDrawable = new TextDrawable.Builder("").build();
    paint = Mockito.mock(Paint.class);
  }

  @Test
  public void getTextTopLeftStartByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT,
        Gravity.TOP | Gravity.LEFT);
    checkPoint(point, new PointF(0, 0));
    verifyPaintTextAlign(Paint.Align.LEFT);
  }

  @Test
  public void getTextTopCenterByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT,
        Gravity.TOP | Gravity.CENTER);
    checkPoint(point, new PointF(WIDTH / 2, 0));
    verifyPaintTextAlign(Paint.Align.CENTER);
  }

  @Test
  public void getTextTopRightByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT,
        Gravity.TOP | Gravity.RIGHT);
    checkPoint(point, new PointF(WIDTH, 0));
    verifyPaintTextAlign(Paint.Align.RIGHT);
  }

  @Test
  public void getTextCenterLeftStartByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT,
        Gravity.CENTER | Gravity.LEFT);
    checkPoint(point, new PointF(0, HEIGHT / 2));
    verifyPaintTextAlign(Paint.Align.LEFT);
  }

  @Test
  public void getTextCenterByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT, Gravity.CENTER);
    checkPoint(point, new PointF(WIDTH / 2, HEIGHT / 2));
    verifyPaintTextAlign(Paint.Align.CENTER);
  }

  @Test
  public void getTextCenterRightByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT,
        Gravity.CENTER | Gravity.RIGHT);
    checkPoint(point, new PointF(WIDTH, HEIGHT / 2));
    verifyPaintTextAlign(Paint.Align.RIGHT);
  }

  @Test
  public void getTextBottomLeftStartByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT,
        Gravity.BOTTOM | Gravity.LEFT);
    checkPoint(point, new PointF(0, HEIGHT));
    verifyPaintTextAlign(Paint.Align.LEFT);
  }

  @Test
  public void getTextBottomCenterByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT,
        Gravity.BOTTOM | Gravity.CENTER);
    checkPoint(point, new PointF(WIDTH / 2, HEIGHT));
    verifyPaintTextAlign(Paint.Align.CENTER);
  }

  @Test
  public void getTextBottomRightByGravityTest() {
    PointF point = textDrawable.initParamByGravity(paint, WIDTH, HEIGHT,
        Gravity.BOTTOM | Gravity.RIGHT);
    checkPoint(point, new PointF(WIDTH, HEIGHT));
    verifyPaintTextAlign(Paint.Align.RIGHT);
  }

  private void checkPoint(PointF expected, PointF actual) {
    Assert.assertEquals(expected.x, actual.x, 0);
    Assert.assertEquals(expected.y, actual.y, 0);
  }

  private void verifyPaintTextAlign(Paint.Align align) {
    Mockito.verify(paint).setTextAlign(align);
  }

}