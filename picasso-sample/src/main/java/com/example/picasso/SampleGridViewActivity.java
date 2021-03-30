package com.example.picasso;

import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import android.os.Bundle;
import android.widget.GridView;
import android.widget.ImageView;

public class SampleGridViewActivity extends PicassoSampleActivity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_gridview_activity);

    GridView gv = findViewById(R.id.grid_view);


    gv.setAdapter(new SampleGridViewAdapter(this));
    gv.setOnScrollListener(new SampleScrollListener(this));

      Picasso.get() //
              .setIndicatorsEnabled(true);//显示指示器

    ImageView tv = findViewById(R.id.tv);

    Picasso.get()
            .load("https://ss0.bdstatic.com/94oJfD_bAAcT8t7mm9GUKT-xh_/timg?image&quality=100&size=b4000_4000&sec=1541759137&di=7deb18a089b3cba9e25300ba9f616009&src=http://pic27.nipic.com/20130227/8786105_153051386191_2.jpg") //
            .placeholder(R.drawable.placeholder)
//            .noPlaceholder()
            .error(R.drawable.error)
//            .noFade()
//            .resize(1,1)
            .transform(new GrayscaleTransformation(Picasso.get()))
            .fit()
//            .tag(this)
            .centerCrop()
//            .rotate(180).
            .networkPolicy(NetworkPolicy.NO_CACHE, NetworkPolicy.NO_STORE)
            .priority(Picasso.Priority.HIGH)
            .into(tv, new Callback() {
              @Override
              public void onSuccess() {

              }

              @Override
              public void onError(Exception e) {

              }
            });







//      Picasso.get()
//              .load("https://ss0.bdstatic.com/94oJfD_bAAcT8t7mm9GUKT-xh_/timg?image&quality=100&size=b4000_4000&sec=1541759137&di=7deb18a089b3cba9e25300ba9f616009&src=http://pic27.nipic.com/20130227/8786105_153051386191_2.jpg") //
//              .placeholder(R.drawable.placeholder)
//              .rotate(360)
////              .rotate(180,200,100)
//              .priority(Picasso.Priority.HIGH)
//              .into(tv);

//      Picasso.Builder builder = new Picasso.Builder(this);
//      //配置下载器
////      builder.downloader(new CustomDownloader());
//      // 配置缓存
//       LruCache cache = new LruCache(5*1024*1024);
//      // 设置缓存大小
//       builder.memoryCache(cache);
//      // 配置线程池
//      ExecutorService executorService = Executors.newFixedThreadPool(8);
//       builder.executor(executorService);
//      // 构造一个Picasso
//      Picasso  picasso = builder.build();
//      // 设置全局单列instanc
//      Picasso.setSingletonInstance(picasso);


  }
}
