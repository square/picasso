package com.example.picasso;

import android.app.Application;
import com.squareup.picasso.BitmapPoolImpl;
import com.squareup.picasso.Picasso;

public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Picasso.setSingletonInstance(
            new Picasso.Builder(this).bitmapPool(new BitmapPoolImpl()).build());
    }
}
