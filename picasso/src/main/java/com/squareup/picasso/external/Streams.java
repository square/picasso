package com.squareup.picasso.external;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

/** From libcore.io.Streams */
class Streams {
  static String readFully(Reader reader) throws IOException {
    try {
      StringWriter writer = new StringWriter();
      char[] buffer = new char[1024];
      int count;
      while ((count = reader.read(buffer)) != -1) {
        writer.write(buffer, 0, count);
      }
      return writer.toString();
    } finally {
      reader.close();
    }
  }
}
