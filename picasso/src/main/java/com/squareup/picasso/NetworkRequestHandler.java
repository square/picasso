/*
 * Copyright (C) 2013 Square, Inc.
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.NetworkInfo;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffTagConstants;

import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Downloader.Response;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso.Picasso.LoadedFrom.NETWORK;

class NetworkRequestHandler extends RequestHandler {
  static final int RETRY_COUNT = 2;
  private static final int MARKER = 65536;

  private static final String SCHEME_HTTP = "http";
  private static final String SCHEME_HTTPS = "https";

  // These constants are not defined in Sanselan
  static final int ORIENTATION_VALUE_ROTATE_180 = 3;
  static final int ORIENTATION_VALUE_ROTATE_90_CW = 6;
  static final int ORIENTATION_VALUE_ROTATE_270_CW = 8;

  private final Downloader downloader;
  private final Stats stats;
  private final boolean hasImaging;

  public NetworkRequestHandler(Downloader downloader, Stats stats) {
    this.downloader = downloader;
    this.stats = stats;
    this.hasImaging = isImagingOnClasspath();
  }

  @Override public boolean canHandleRequest(Request data) {
    String scheme = data.uri.getScheme();
    return (SCHEME_HTTP.equals(scheme) || SCHEME_HTTPS.equals(scheme));
  }

  @Override public Result load(Request data) throws IOException {
    Response response = downloader.load(data.uri, data.loadFromLocalCacheOnly);
    if (response == null) {
      return null;
    }

    Picasso.LoadedFrom loadedFrom = response.cached ? DISK : NETWORK;

    Bitmap bitmap = response.getBitmap();
    if (bitmap != null) {
      return new Result(bitmap, loadedFrom);
    }

    InputStream is = response.getInputStream();
    if (is == null) {
      return null;
    }
    // Sometimes response content length is zero when requests are being replayed. Haven't found
    // root cause to this but retrying the request seems safe to do so.
    if (response.getContentLength() == 0) {
      Utils.closeQuietly(is);
      throw new IOException("Received response with 0 content-length header.");
    }
    if (loadedFrom == NETWORK && response.getContentLength() > 0) {
      stats.dispatchDownloadFinished(response.getContentLength());
    }
    try {
      MarkableInputStream markStream = new MarkableInputStream(is);
      long mark = markStream.savePosition(MARKER);
      int orientation = getOrientation(markStream);
      markStream.reset(mark);
      return new Result(decodeStream(markStream, data), loadedFrom, orientation);
    } finally {
      Utils.closeQuietly(is);
    }
  }

  @Override int getRetryCount() {
    return RETRY_COUNT;
  }

  @Override boolean shouldRetry(boolean airplaneMode, NetworkInfo info) {
    return info == null || info.isConnected();
  }

  @Override boolean supportsReplay() {
    return true;
  }

  private Bitmap decodeStream(MarkableInputStream stream, Request data) throws IOException {
    long mark = stream.savePosition(MARKER);

    final BitmapFactory.Options options = createBitmapOptions(data);
    final boolean calculateSize = requiresInSampleSize(options);

    boolean isWebPFile = Utils.isWebPFile(stream);
    stream.reset(mark);
    // When decode WebP network stream, BitmapFactory throw JNI Exception and make app crash.
    // Decode byte array instead
    if (isWebPFile) {
      byte[] bytes = Utils.toByteArray(stream);
      if (calculateSize) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        calculateInSampleSize(data.targetWidth, data.targetHeight, options, data);
      }
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    } else {
      if (calculateSize) {
        BitmapFactory.decodeStream(stream, null, options);
        calculateInSampleSize(data.targetWidth, data.targetHeight, options, data);
        stream.reset(mark);
      }
      Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
      if (bitmap == null) {
        // Treat null as an IO exception, we will eventually retry.
        throw new IOException("Failed to decode stream.");
      }
      return bitmap;
    }
  }

  private int getOrientation(InputStream is) {
    if (!hasImaging) {
      return 0;
    }

    try {
      IImageMetadata metadata = Sanselan.getMetadata(is, null);
      TiffImageMetadata tiffMetaData;
      if (metadata instanceof JpegImageMetadata) {
        JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
        tiffMetaData = jpegMetadata.getExif();
      } else if (metadata instanceof TiffImageMetadata) {
        tiffMetaData = (TiffImageMetadata) metadata;
      } else {
        return 0;
      }
      TiffField orientationField = tiffMetaData.findField(TiffTagConstants.TIFF_TAG_ORIENTATION);
      if (orientationField != null) {
        int orientationValue = orientationField.getIntValue();
        switch (orientationValue) {
          case ORIENTATION_VALUE_ROTATE_180:
            return 180;
          case ORIENTATION_VALUE_ROTATE_90_CW:
            return 90;
          case ORIENTATION_VALUE_ROTATE_270_CW:
            return 270;
          default:
            return 0;
        }
      }
    } catch (IOException ignored) {
    } catch (ImageReadException ignored) {
    }
    return 0;
  }

  private Boolean isImagingOnClasspath() {
    try {
      Class.forName("org.apache.sanselan.Sanselan");
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }
}
