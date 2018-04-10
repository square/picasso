package com.example.picasso;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RemoteViews;
import android.widget.TextView;
import com.squareup.picasso3.provider.PicassoProvider;
import java.util.Random;

final class PicassoSampleAdapter extends BaseAdapter {

  private static final int NOTIFICATION_ID = 666;
  private static final String CHANNEL_ID = "channel-id";

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

        Notification notification =
            new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(PendingIntent.getActivity(activity, -1, intent, 0))
                .setContent(remoteViews)
                .setAutoCancel(true)
                .setChannelId(CHANNEL_ID)
                .build();

        NotificationManager notificationManager =
            (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          NotificationChannel channel =
              new NotificationChannel(CHANNEL_ID, "Picasso Notification Channel",
                  NotificationManager.IMPORTANCE_DEFAULT);
          notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(NOTIFICATION_ID, notification);

        // Now load an image for this notification.
        PicassoProvider.get() //
            .load(Data.URLS[new Random().nextInt(Data.URLS.length)]) //
            .resizeDimen(R.dimen.notification_icon_width_height,
                R.dimen.notification_icon_width_height) //
            .into(remoteViews, R.id.photo, NOTIFICATION_ID, notification);
      }
    };

    private final Class<? extends Activity> activityClass;
    final String name;

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
