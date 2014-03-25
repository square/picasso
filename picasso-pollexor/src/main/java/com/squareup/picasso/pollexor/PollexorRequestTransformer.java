package com.squareup.picasso.pollexor;

import android.net.Uri;
import com.squareup.picasso.Request;
import com.squareup.pollexor.Thumbor;
import com.squareup.pollexor.ThumborUrlBuilder;

import static com.squareup.picasso.Picasso.RequestTransformer;

/**
 * A {@link RequestTransformer} that changes requests to use {@link Thumbor} for some remote
 * transformations.
 */
public class PollexorRequestTransformer implements RequestTransformer {
  private final Thumbor thumbor;

  /**
   * @deprecated Use {@link #PollexorRequestTransformer(Thumbor)} instead.
   * Create a transformer for the specified Thumbor host. This will not use URL encryption.
   */
  @Deprecated public PollexorRequestTransformer(String host) {
    this(Thumbor.create(host));
  }

  /**
   * @deprecated Use {@link #PollexorRequestTransformer(Thumbor)} instead.
   * Create a transformer for the specified Thumbor host using the provided URL encryption key.
   */
  @Deprecated public PollexorRequestTransformer(String host, String key) {
    this(Thumbor.create(host, key));
  }

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

    // Update the request with the completed Thumbor URL.
    newRequest.setUri(Uri.parse(urlBuilder.toUrl()));

    return newRequest.build();
  }
}
