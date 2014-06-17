package com.squareup.picasso.scrolling;

import android.content.Context;
import android.widget.AbsListView;

import com.squareup.picasso.Picasso;

/**
 * A simple scroll listener that disables fading / setting image drawable while the AbsListView is flinging
 * @author Hannes Dorfmann
 */
public class PicassoScrollListener implements AbsListView.OnScrollListener {


    protected AbsListView.OnScrollListener delegate;
    protected Picasso picasso;

    public PicassoScrollListener(Picasso picasso, AbsListView.OnScrollListener delegate){
        this.delegate = delegate;
        this.picasso = picasso;
    }

    public PicassoScrollListener(Picasso picasso){
        this(picasso, null);
    }

    public PicassoScrollListener(Context context){
       this(context, null);
    }

    public PicassoScrollListener(Context context, AbsListView.OnScrollListener delegate){
        this(Picasso.with(context), delegate);
    }


    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

        // TO the picasso staff
        if (SCROLL_STATE_IDLE == scrollState ){
            picasso.continueDispatching();
        }

        if (SCROLL_STATE_FLING == scrollState){
            picasso.interruptDispatching();
        }

        // Forwart to the delegate
        if (delegate != null){
            delegate.onScrollStateChanged(view, scrollState);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {


        // Forward to the delegate
        if (delegate != null){
            delegate.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }

    }
}
