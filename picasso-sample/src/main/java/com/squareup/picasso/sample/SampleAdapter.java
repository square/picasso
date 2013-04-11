package com.squareup.picasso.sample;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import com.squareup.picasso.Picasso;

import static android.widget.ImageView.ScaleType.FIT_CENTER;

public class SampleAdapter extends BaseAdapter {
  private final Context context;

  public SampleAdapter(Context context) {
    this.context = context;
  }

  @Override public int getCount() {
    return URLS.length;
  }

  @Override public String getItem(int position) {
    return URLS[position];
  }

  @Override public long getItemId(int position) {
    return position;
  }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    SquaredImageView view = (SquaredImageView) convertView;
    if (view == null) {
      view = new SquaredImageView(context);
      view.setScaleType(FIT_CENTER);
    }

    // Get the image URL for the current position.
    String url = getItem(position);

    // Trigger the download of the URL asynchronously into the image view.
    Picasso.with(context) //
        .load(url) //
        .placeholder(R.drawable.placeholder) //
        .error(R.drawable.error) //
        .into(view);

    return view;
  }

  private static final String BASE = "http://upload.wikimedia.org/wikipedia/commons/thumb";
  private static final String[] URLS = {
      BASE + "/5/5c/Flag_of_Alabama.svg/200px-Flag_of_Alabama.svg.png",
      BASE + "/e/e6/Flag_of_Alaska.svg/200px-Flag_of_Alaska.svg.png",
      BASE + "/9/9d/Flag_of_Arizona.svg/200px-Flag_of_Arizona.svg.png",
      BASE + "/9/9d/Flag_of_Arkansas.svg/200px-Flag_of_Arkansas.svg.png",
      BASE + "/0/01/Flag_of_California.svg/200px-Flag_of_California.svg.png",
      BASE + "/4/46/Flag_of_Colorado.svg/200px-Flag_of_Colorado.svg.png",
      BASE + "/9/96/Flag_of_Connecticut.svg/200px-Flag_of_Connecticut.svg.png",
      BASE + "/c/c6/Flag_of_Delaware.svg/200px-Flag_of_Delaware.svg.png",
      BASE + "/f/f7/Flag_of_Florida.svg/200px-Flag_of_Florida.svg.png",
      BASE + "/e/ef/Flag_of_Hawaii.svg/200px-Flag_of_Hawaii.svg.png",
      BASE + "/a/a4/Flag_of_Idaho.svg/200px-Flag_of_Idaho.svg.png",
      BASE + "/0/01/Flag_of_Illinois.svg/200px-Flag_of_Illinois.svg.png",
      BASE + "/a/ac/Flag_of_Indiana.svg/200px-Flag_of_Indiana.svg.png",
      BASE + "/a/aa/Flag_of_Iowa.svg/200px-Flag_of_Iowa.svg.png",
      BASE + "/d/da/Flag_of_Kansas.svg/200px-Flag_of_Kansas.svg.png",
      BASE + "/8/8d/Flag_of_Kentucky.svg/200px-Flag_of_Kentucky.svg.png",
      BASE + "/e/e0/Flag_of_Louisiana.svg/200px-Flag_of_Louisiana.svg.png",
      BASE + "/3/35/Flag_of_Maine.svg/200px-Flag_of_Maine.svg.png",
      BASE + "/a/a0/Flag_of_Maryland.svg/200px-Flag_of_Maryland.svg.png",
      BASE + "/f/f2/Flag_of_Massachusetts.svg/200px-Flag_of_Massachusetts.svg.png",
      BASE + "/b/b5/Flag_of_Michigan.svg/200px-Flag_of_Michigan.svg.png",
      BASE + "/b/b9/Flag_of_Minnesota.svg/200px-Flag_of_Minnesota.svg.png",
      BASE + "/4/42/Flag_of_Mississippi.svg/200px-Flag_of_Mississippi.svg.png",
      BASE + "/5/5a/Flag_of_Missouri.svg/200px-Flag_of_Missouri.svg.png",
      BASE + "/c/cb/Flag_of_Montana.svg/200px-Flag_of_Montana.svg.png",
      BASE + "/4/4d/Flag_of_Nebraska.svg/200px-Flag_of_Nebraska.svg.png",
      BASE + "/f/f1/Flag_of_Nevada.svg/200px-Flag_of_Nevada.svg.png",
      BASE + "/2/28/Flag_of_New_Hampshire.svg/200px-Flag_of_New_Hampshire.svg.png",
      BASE + "/9/92/Flag_of_New_Jersey.svg/200px-Flag_of_New_Jersey.svg.png",
      BASE + "/c/c3/Flag_of_New_Mexico.svg/200px-Flag_of_New_Mexico.svg.png",
      BASE + "/1/1a/Flag_of_New_York.svg/200px-Flag_of_New_York.svg.png",
      BASE + "/b/bb/Flag_of_North_Carolina.svg/200px-Flag_of_North_Carolina.svg.png",
      BASE + "/e/ee/Flag_of_North_Dakota.svg/200px-Flag_of_North_Dakota.svg.png",
      BASE + "/4/4c/Flag_of_Ohio.svg/200px-Flag_of_Ohio.svg.png",
      BASE + "/6/6e/Flag_of_Oklahoma.svg/200px-Flag_of_Oklahoma.svg.png",
      BASE + "/b/b9/Flag_of_Oregon.svg/200px-Flag_of_Oregon.svg.png",
      BASE + "/f/f7/Flag_of_Pennsylvania.svg/200px-Flag_of_Pennsylvania.svg.png",
      BASE + "/f/f3/Flag_of_Rhode_Island.svg/200px-Flag_of_Rhode_Island.svg.png",
      BASE + "/6/69/Flag_of_South_Carolina.svg/200px-Flag_of_South_Carolina.svg.png",
      BASE + "/1/1a/Flag_of_South_Dakota.svg/200px-Flag_of_South_Dakota.svg.png",
      BASE + "/9/9e/Flag_of_Tennessee.svg/200px-Flag_of_Tennessee.svg.png",
      BASE + "/f/f7/Flag_of_Texas.svg/200px-Flag_of_Texas.svg.png",
      BASE + "/f/f6/Flag_of_Utah.svg/200px-Flag_of_Utah.svg.png",
      BASE + "/4/49/Flag_of_Vermont.svg/200px-Flag_of_Vermont.svg.png",
      BASE + "/4/47/Flag_of_Virginia.svg/200px-Flag_of_Virginia.svg.png",
      BASE + "/5/54/Flag_of_Washington.svg/200px-Flag_of_Washington.svg.png",
      BASE + "/2/22/Flag_of_West_Virginia.svg/200px-Flag_of_West_Virginia.svg.png",
      BASE + "/2/22/Flag_of_Wisconsin.svg/200px-Flag_of_Wisconsin.svg.png",
      BASE + "/b/bc/Flag_of_Wyoming.svg/200px-Flag_of_Wyoming.svg.png"
  };
}
