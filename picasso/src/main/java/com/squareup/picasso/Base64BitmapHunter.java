/*
 * Copyright (C) 2014 Square, Inc.
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
import android.util.Base64;

import java.io.IOException;

import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;

class Base64BitmapHunter extends BitmapHunter {
  public static final String SCHEME_DATA = "data";

  private final String MIME_PREFIX = "image/";
  private final String SPLITTER = "base64,";

  Base64BitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache,
    Stats stats, Action action) {
    super(picasso, dispatcher, cache, stats, action);
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return MEMORY;
  }

  @Override Bitmap decode(Request data) throws IOException {
    String part = data.uri.getSchemeSpecificPart();
    if (!part.startsWith(MIME_PREFIX)) {
      throw new IOException("Malformed data uri");
    }
	
    part = part.substring(MIME_PREFIX.length());
    if (!part.matches("^(gif|jpe?g|png);base64,.[A-Za-z0-9+/=]*$")) {
      throw new IOException("Unsupported content type");
    }
	
    part = part.substring(part.indexOf(SPLITTER) + SPLITTER.length());
    return decodeBase64(part);
  }

  Bitmap decodeBase64(String body) throws IOException {
    try {
      byte[] decodedByte = Base64.decode(body, 0);
      return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length);
    } catch(Exception e) {
      throw new IOException("Malformed data");
    }
  }
}
