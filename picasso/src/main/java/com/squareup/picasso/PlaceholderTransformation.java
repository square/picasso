package com.squareup.picasso;

/**
 * With this interface you mark that this Transformation must be apply to Placeholders too.
 * Warning! the first time, the transformation will be resolved in the UI thread.
 */
public interface PlaceholderTransformation extends Transformation {
}
