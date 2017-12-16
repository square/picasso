package com.example.picasso;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.squareup.picasso.Picasso;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SampleListDetailAdapter extends BaseAdapter {
  private final Context context;
  private final List<String> urls = new ArrayList<>();

  SampleListDetailAdapter(Context context) {
    this.context = context;
    Collections.addAll(urls, Data.URLS);
  }

  @Override public View getView(int position, View view, ViewGroup parent) {
    ViewHolder holder;
    if (view == null) {
      view = LayoutInflater.from(context).inflate(R.layout.sample_list_detail_item, parent, false);
      holder = new ViewHolder();
      holder.image = (ImageView) view.findViewById(R.id.photo);
      holder.text = (TextView) view.findViewById(R.id.url);
      view.setTag(holder);
    } else {
      holder = (ViewHolder) view.getTag();
    }

    // Get the image URL for the current position.
    String url = getItem(position);

    holder.text.setText(url);

    // Trigger the download of the URL asynchronously into the image view.
    Picasso.get()
        .load(url)
        .placeholder(R.drawable.placeholder)
        .error(R.drawable.error)
        .resizeDimen(R.dimen.list_detail_image_size, R.dimen.list_detail_image_size)
        .centerInside()
        .tag(context)
        .into(holder.image);

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

  static class ViewHolder {
    ImageView image;
    TextView text;
  }
}
