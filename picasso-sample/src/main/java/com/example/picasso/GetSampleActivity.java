package com.example.picasso;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ImageView;
import com.squareup.picasso.Picasso;

import java.io.IOException;

public class GetSampleActivity extends Activity {
    private ImageView mImageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.picasso_sample_get_activity);
        mImageView = (ImageView) findViewById(R.id.picasso_sample_get_activity_imageview);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new GetImageAsyncTask().execute();
    }

    private class GetImageAsyncTask extends AsyncTask<Void, Void, Bitmap> {

        @Override
        protected Bitmap  doInBackground(Void... params) {
            Bitmap ret = null;
            try {
                ret = Picasso.with(GetSampleActivity.this).load(Data.URLS[0]).get();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return ret;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

            if ( bitmap != null ) {
                mImageView.setImageBitmap(bitmap);
            }

        }
    }
}
