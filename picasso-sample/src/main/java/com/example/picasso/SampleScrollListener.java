package com.example.picasso;

import android.content.Context;
import android.widget.AbsListView;
import com.example.picasso.provider.PicassoProvider;
import com.squareup.picasso3.Picasso;

public class SampleScrollListener implements AbsListView.OnScrollListener {
  private final Context context;

  public SampleScrollListener(Context context) {
    this.context = context;
  }

  @Override
  public void onScrollStateChanged(AbsListView view, int scrollState) {
    Picasso picasso = PicassoProvider.get();
    if (scrollState == SCROLL_STATE_IDLE || scrollState == SCROLL_STATE_TOUCH_SCROLL) {
      picasso.resumeTag(context);
    } else {
      picasso.pauseTag(context);
    }
  }

  @Override
  public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                       int totalItemCount) {
    // Do nothing.
  }
}
