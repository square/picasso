package com.example.picasso

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.picasso.provider.PicassoProvider
import java.util.Random

internal class PicassoSampleAdapter(context: Context?) : BaseAdapter() {
  internal enum class Sample(
    val label: String,
    private val activityClass: Class<out Activity>?
  ) {
    GRID_VIEW("Image Grid View", SampleGridViewActivity::class.java),
    GALLERY("Load from Gallery", SampleGalleryActivity::class.java),
    CONTACTS("Contact Photos", SampleContactsActivity::class.java),
    LIST_DETAIL("List / Detail View", SampleListDetailActivity::class.java),
    SHOW_NOTIFICATION("Sample Notification", null) {
      override fun launch(activity: Activity) {
        val remoteViews = RemoteViews(activity.packageName, R.layout.notification_view)

        val intent = Intent(activity, SampleGridViewActivity::class.java)

        val notification =
          NotificationCompat.Builder(activity, CHANNEL_ID)
              .setSmallIcon(R.drawable.icon)
              .setContentIntent(PendingIntent.getActivity(activity, -1, intent, 0))
              .setContent(remoteViews)
              .setAutoCancel(true)
              .setChannelId(CHANNEL_ID)
              .build()

        val notificationManager =
          activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (VERSION.SDK_INT >= VERSION_CODES.O) {
          val channel = NotificationChannel(
              CHANNEL_ID, "Picasso Notification Channel",
              NotificationManager.IMPORTANCE_DEFAULT
          )
          notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(NOTIFICATION_ID, notification)

        // Now load an image for this notification.
        PicassoProvider.get()
            .load(Data.URLS[Random().nextInt(Data.URLS.size)])
            .resizeDimen(
                R.dimen.notification_icon_width_height,
                R.dimen.notification_icon_width_height
            )
            .into(remoteViews, R.id.photo, NOTIFICATION_ID, notification)
      }
    };

    open fun launch(activity: Activity) {
      activity.startActivity(Intent(activity, activityClass))
      activity.finish()
    }

  }

  private val inflater: LayoutInflater = LayoutInflater.from(context)

  override fun getCount(): Int = Sample.values().size

  override fun getItem(position: Int): Sample = Sample.values()[position]

  override fun getItemId(position: Int): Long = position.toLong()

  override fun getView(
    position: Int,
    convertView: View?,
    parent: ViewGroup
  ): View {
    val view = if (convertView == null) {
      inflater.inflate(R.layout.picasso_sample_activity_item, parent, false) as TextView
    } else
      convertView as TextView

    view.text = getItem(position).label
    return view
  }

  companion object {
    private const val NOTIFICATION_ID = 666
    private const val CHANNEL_ID = "channel-id"
  }
}
