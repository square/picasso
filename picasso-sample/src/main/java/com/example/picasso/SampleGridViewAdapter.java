package com.example.picasso;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.widget.ImageView.ScaleType.CENTER_CROP;

final class SampleGridViewAdapter extends BaseAdapter {
  private final Context context;
  private final List<String> urls = new ArrayList<String>();
  private boolean roundEffect = false;

  public SampleGridViewAdapter(Context context) {
    this.context = context;

    // Ensure we get a different ordering of images on each run.
    Collections.addAll(urls, Data.URLS);
    Collections.shuffle(urls);

    // Triple up the list.
    ArrayList<String> copy = new ArrayList<String>(urls);
    urls.addAll(copy);
    urls.addAll(copy);
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

    if (roundEffect) {
      creator.transform(new CropRoundTransformation());
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

  public void setRoundEffect(boolean roundEffect) {
    this.roundEffect = roundEffect;
  }
}
