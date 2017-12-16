package com.example.picasso;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RemoteViews;
import android.widget.TextView;
import com.squareup.picasso.Picasso;
import java.util.Random;

final class PicassoSampleAdapter extends BaseAdapter {

  private static final int NOTIFICATION_ID = 666;

  enum Sample {
    GRID_VIEW("Image Grid View", SampleGridViewActivity.class),
    GALLERY("Load from Gallery", SampleGalleryActivity.class),
    CONTACTS("Contact Photos", SampleContactsActivity.class),
    LIST_DETAIL("List / Detail View", SampleListDetailActivity.class),
    SHOW_NOTIFICATION("Sample Notification", null) {
      @Override public void launch(Activity activity) {
        RemoteViews remoteViews =
            new RemoteViews(activity.getPackageName(), R.layout.notification_view);

        Intent intent = new Intent(activity, SampleGridViewActivity.class);

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(activity).setSmallIcon(R.drawable.icon)
                .setContentIntent(PendingIntent.getActivity(activity, -1, intent, 0))
                .setContent(remoteViews);

        Notification notification = builder.getNotification();

        NotificationManager notificationManager =
            (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);

        // Now load an image for this notification.
        Picasso.get() //
            .load(Data.URLS[new Random().nextInt(Data.URLS.length)]) //
            .resizeDimen(R.dimen.notification_icon_width_height,
                R.dimen.notification_icon_width_height) //
            .into(remoteViews, R.id.photo, NOTIFICATION_ID, notification);
      }
    };

    private final Class<? extends Activity> activityClass;
    private final String name;

    Sample(String name, Class<? extends Activity> activityClass) {
      this.activityClass = activityClass;
      this.name = name;
    }

    public void launch(Activity activity) {
      activity.startActivity(new Intent(activity, activityClass));
      activity.finish();
    }
  }

  private final LayoutInflater inflater;

  PicassoSampleAdapter(Context context) {
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
