package com.example.picasso;

import com.squareup.picasso.Picasso;

import android.content.Context;
import android.widget.AbsListView;

public class SampleScrollListener implements AbsListView.OnScrollListener {
  private final Context context;

  public SampleScrollListener(Context context) {
    this.context = context;
  }

  @Override
  public void onScrollStateChanged(AbsListView view, int scrollState) {
    final Picasso picasso = Picasso.get();
    picasso.cancelTag(context);
//    picasso.cancelRequest(new ImageView(context));

    if (scrollState == SCROLL_STATE_IDLE || scrollState == SCROLL_STATE_TOUCH_SCROLL) {
      picasso.resumeTag(context);
    } else {
      picasso.pauseTag(context);

//      picasso.cancelTag(context);
    }
  }

  @Override
  public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                       int totalItemCount) {
    // Do nothing.
  }
}
