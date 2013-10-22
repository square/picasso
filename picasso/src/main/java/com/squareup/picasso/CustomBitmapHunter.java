package com.squareup.picasso;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

import java.io.IOException;

import android.graphics.Bitmap;

public class CustomBitmapHunter extends BitmapHunter {

	CustomBitmapHunter ( Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats, Action<?> action ) {
		super( picasso, dispatcher, cache, stats, action );
	}

	@Override
	Bitmap decode( Request data ) throws IOException {
		return decodeContent( data );
	}

	@Override
	Picasso.LoadedFrom getLoadedFrom() {
		return DISK;
	}
	
	Bitmap decodeContent( Request data ) throws IOException {
		if( !data.hasCustomGenerator() ) {
			throw new IllegalStateException( "Custom Uri can be used only with a Generator" );
		}
		return data.customGenerator.decode( data.uri );
	}
}
