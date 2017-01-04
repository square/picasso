package com.squareup.picasso.pollexor;

import android.net.Uri;
import android.os.Build;
import com.squareup.picasso.Request;
import com.squareup.pollexor.Thumbor;
import com.squareup.pollexor.ThumborUrlBuilder;
import com.squareup.pollexor.ThumborUrlBuilder.ImageFormat;

import static com.squareup.picasso.Picasso.RequestTransformer;
import static com.squareup.pollexor.ThumborUrlBuilder.format;

/**
 * A {@link RequestTransformer} that changes requests to use {@link Thumbor} for some remote
 * transformations.
 */
public class PollexorRequestTransformer implements RequestTransformer {
  private final Thumbor thumbor;

  /** Create a transformer for the specified {@link Thumbor}. */
  public PollexorRequestTransformer(Thumbor thumbor) {
    this.thumbor = thumbor;
  }

  @Override public Request transformRequest(Request request) {
    if (request.resourceId != 0) {
      return request; // Don't transform resource requests.
    }
    Uri uri = request.uri;
    String scheme = uri.getScheme();
    if (!"https".equals(scheme) && !"http".equals(scheme)) {
      return request; // Thumbor only supports remote images.
    }
    if (!request.hasSize()) {
      return request; // Thumbor only works with resizing images.
    }

    // Start building a new request for us to mutate.
    Request.Builder newRequest = request.buildUpon();

    // Create the url builder to use.
    ThumborUrlBuilder urlBuilder = thumbor.buildImage(uri.toString());

    // Resize the image to the target size.
    urlBuilder.resize(request.targetWidth, request.targetHeight);
    newRequest.clearResize();

    // If the center inside flag is set, perform that with Thumbor as well.
    if (request.centerInside) {
      urlBuilder.fitIn();
      newRequest.clearCenterInside();
    }

    // If the Android version is modern enough use WebP for downloading.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      urlBuilder.filter(format(ImageFormat.WEBP));
    }

    // Update the request with the completed Thumbor URL.
    newRequest.setUri(Uri.parse(urlBuilder.toUrl()));

    return newRequest.build();
  }
}
