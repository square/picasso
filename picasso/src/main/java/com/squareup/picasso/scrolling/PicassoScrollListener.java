package com.squareup.picasso.scrolling;

import android.content.Context;
import android.util.Log;
import android.widget.AbsListView;
import com.squareup.picasso.Picasso;

/**
 * A simple scroll listener that disables fading / setting image drawable while the AbsListView is
 * flinging
 *
 * @author Hannes Dorfmann
 */
public class PicassoScrollListener implements AbsListView.OnScrollListener {

  protected AbsListView.OnScrollListener delegate;
  protected Picasso picasso;
  private int previousScrollState = SCROLL_STATE_IDLE;

  public PicassoScrollListener(Picasso picasso, AbsListView.OnScrollListener delegate) {
    this.delegate = delegate;
    this.picasso = picasso;
  }

  public PicassoScrollListener(Picasso picasso) {
    this(picasso, null);
  }

  public PicassoScrollListener(Context context) {
    this(context, null);
  }

  public PicassoScrollListener(Context context, AbsListView.OnScrollListener delegate) {
    this(Picasso.with(context), delegate);
  }

  @Override
  public void onScrollStateChanged(AbsListView view, int scrollState) {

    Log.d("Test", "state " + scrollState);
    // TO the picasso staff
    if (!isScrolling(scrollState) && isScrolling(previousScrollState)) {
      picasso.continueDispatching();
      Log.d("Test", " continue");
    }

    if (isScrolling(scrollState) && !isScrolling(previousScrollState)) {
      picasso.interruptDispatching();
      Log.d("Test", " interrupt");
    }

    previousScrollState = scrollState;

    // Forwart to the delegate
    if (delegate != null) {
      delegate.onScrollStateChanged(view, scrollState);
    }
  }

  protected boolean isScrolling(int scrollState) {
    return scrollState == SCROLL_STATE_FLING || scrollState == SCROLL_STATE_TOUCH_SCROLL;
  }

  @Override
  public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
      int totalItemCount) {

    // Forward to the delegate
    if (delegate != null) {
      delegate.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
    }
  }
}
