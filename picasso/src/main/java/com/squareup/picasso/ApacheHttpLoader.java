package com.squareup.picasso;

import android.net.http.AndroidHttpClient;
import java.io.IOException;
import java.io.InputStream;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

public class ApacheHttpLoader implements Loader {

  private final HttpClient client;

  public ApacheHttpLoader() {
    this.client = AndroidHttpClient.newInstance("picasso");
  }

  @Override public InputStream load(String path) throws IOException {
    return client.execute(new HttpGet(path)).getEntity().getContent();
  }
}