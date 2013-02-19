package com.squareup.picasso.external;

import java.lang.reflect.Array;

/* From java.util.Arrays */
class Arrays {
  @SuppressWarnings("unchecked")
  static <T> T[] copyOfRange(T[] original, int start, int end) {
    int originalLength = original.length; // For exception priority compatibility.
    if (start > end) {
      throw new IllegalArgumentException();
    }
    if (start < 0 || start > originalLength) {
      throw new ArrayIndexOutOfBoundsException();
    }
    int resultLength = end - start;
    int copyLength = Math.min(resultLength, originalLength - start);
    T[] result = (T[]) Array.newInstance(original.getClass().getComponentType(), resultLength);
    System.arraycopy(original, start, result, 0, copyLength);
    return result;
  }
}
