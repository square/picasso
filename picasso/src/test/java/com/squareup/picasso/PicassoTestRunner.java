package com.squareup.picasso;

import java.io.File;
import org.junit.runners.model.InitializationError;
import org.robolectric.AndroidManifest;
import org.robolectric.RobolectricTestRunner;

public class PicassoTestRunner extends RobolectricTestRunner {
  public PicassoTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
  }

  @Override protected AndroidManifest createAppManifest(File baseDir) {
    return null;
  }
}
