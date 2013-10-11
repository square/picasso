package com.squareup.picasso;

import java.io.IOException;

import android.graphics.Bitmap;

public interface Generator {
	Bitmap decode( String path ) throws IOException;
}
