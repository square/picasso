package com.squareup.picasso.pollexor;

import android.net.Uri;
import com.squareup.picasso.Request;
import com.squareup.pollexor.Pollexor;

import static com.squareup.picasso.Picasso.RequestTransformer;

/**
 * A {@link RequestTransformer} that changes requests to use Thumbor for some remote
 * transformations.
 */
public class PollexorRequestTransformer implements RequestTransformer {
  private final String host;
  private final String key;

  /** Create a transformer for the specified Thumbor host. This will not use URL encryption. */
  public PollexorRequestTransformer(String host) {
    this(host, null);
  }

  /** Create a transformer for the specified Thumbor host using the provided URL encryption key. */
  public PollexorRequestTransformer(String host, String key) {
    this.host = host;
    this.key = key;
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
    if (request.centerCrop) {
      return request; // Can't center crop yet. See: https://github.com/globocom/thumbor/issues/201
    }

    // Start building a new request for us to mutate.
    Request.Builder newRequest = request.buildUpon();

    // Start creating the Thumbor URL with the image and host. Add the encryption key, if present.
    Pollexor pollexor = Pollexor.image(uri.toString()).host(host);
    if (key != null) {
      pollexor.key(key);
    }

    // Resize the image to the target size.
    pollexor.resize(request.targetWidth, request.targetHeight);
    newRequest.clearResize();

    // If the center inside flag is set, perform that with Thumbor as well.
    if (request.centerInside) {
      pollexor.fitIn();
      newRequest.clearCenterInside();
    }

    // Update the request with the completed Thumbor URL.
    newRequest.setUri(Uri.parse(pollexor.toUrl()));

    return newRequest.build();
  }
}
