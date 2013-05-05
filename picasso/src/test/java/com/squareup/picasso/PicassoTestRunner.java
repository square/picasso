package com.squareup.picasso;

import org.junit.runners.model.InitializationError;
import org.robolectric.AndroidManifest;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.res.FsFile;

public class PicassoTestRunner extends RobolectricTestRunner {
  public PicassoTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
  }

  @Override protected AndroidManifest createAppManifest(FsFile manifestFile) {
    return null;
  }
}
