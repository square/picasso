package com.example.picasso;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

final class PicassoSampleAdapter extends BaseAdapter {
  enum Sample {
    GRID_VIEW("Image Grid View", SampleGridViewActivity.class),
    GALLERY("Load from Gallery", SampleGalleryActivity.class),
    CONTACTS("Contact Photos", SampleContactsActivity.class),
    LIST_DETAIL("List / Detail View", SampleListDetailActivity.class);

    private final Class<?> activityClass;

    private final String name;

    Sample(String name, Class<?> activityClass) {
      this.activityClass = activityClass;
      this.name = name;
    }

    public void launch(Activity activity) {
      activity.startActivity(new Intent(activity, activityClass));
      activity.finish();
    }
  }

  private final LayoutInflater inflater;

  public PicassoSampleAdapter(Context context) {
    inflater = LayoutInflater.from(context);
  }

  @Override public int getCount() {
    return Sample.values().length;
  }

  @Override public Sample getItem(int position) {
    return Sample.values()[position];
  }

  @Override public long getItemId(int position) {
    return position;
  }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    TextView view = (TextView) convertView;
    if (view == null) {
      view = (TextView) inflater.inflate(R.layout.picasso_sample_activity_item, parent, false);
    }

    view.setText(getItem(position).name);

    return view;
  }
}
