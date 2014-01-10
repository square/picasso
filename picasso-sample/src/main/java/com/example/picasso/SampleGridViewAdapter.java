package com.example.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import com.squareup.picasso.DrawableFactory;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.widget.ImageView.ScaleType.CENTER_CROP;

final class SampleGridViewAdapter extends BaseAdapter {
  private final Context context;
  private final List<String> urls = new ArrayList<String>();
  private final boolean useShaderEffects;
  private final DrawableFactory drawableFactory;

  public SampleGridViewAdapter(Context context, boolean useShaderEffects) {
    this.context = context;
    this.useShaderEffects = useShaderEffects;

    // Ensure we get a different ordering of images on each run.
    Collections.addAll(urls, Data.URLS);
    Collections.shuffle(urls);

    // Triple up the list.
    ArrayList<String> copy = new ArrayList<String>(urls);
    urls.addAll(copy);
    urls.addAll(copy);

    drawableFactory = new DrawableFactory() {
      @Override
      public Drawable createDrawable(Bitmap source) {
        return new ShaderEffectsDrawable(source);
      }
    };
  }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    SquaredImageView view = (SquaredImageView) convertView;
    if (view == null) {
      view = new SquaredImageView(context);
      view.setScaleType(CENTER_CROP);
    }

    // Get the image URL for the current position.
    String url = getItem(position);

    // Trigger the download of the URL asynchronously into the image view.
    RequestCreator creator = Picasso.with(context) //
        .load(url) //
        .placeholder(R.drawable.placeholder) //
        .error(R.drawable.error) //
        .fit();
    if (useShaderEffects) {
      creator.drawableFactory(drawableFactory);
    }
    creator.into(view);

    return view;
  }

  @Override public int getCount() {
    return urls.size();
  }

  @Override public String getItem(int position) {
    return urls.get(position);
  }

  @Override public long getItemId(int position) {
    return position;
  }
}
