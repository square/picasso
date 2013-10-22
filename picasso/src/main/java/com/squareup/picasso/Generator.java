package com.squareup.picasso;

import java.io.IOException;

import android.graphics.Bitmap;
import android.net.Uri;

public interface Generator {
	Bitmap decode( Uri uri ) throws IOException;
}
