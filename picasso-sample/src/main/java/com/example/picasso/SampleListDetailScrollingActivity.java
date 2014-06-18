package com.example.picasso;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.scrolling.PicassoFlingScrollListener;

public class SampleListDetailScrollingActivity extends PicassoSampleActivity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState == null) {
      getSupportFragmentManager().beginTransaction()
          .add(R.id.sample_content, ListFragment.newInstance())
          .commit();
    }
  }

  void showDetails(String url) {
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.sample_content, DetailFragment.newInstance(url))
        .addToBackStack(null)
        .commit();
  }

  public static class ListFragment extends Fragment {
    public static ListFragment newInstance() {
      return new ListFragment();
    }

      private Button skipMemoryCacheButton;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
      boolean skipMemory = true;
      final SampleListDetailScrollingActivity activity = (SampleListDetailScrollingActivity) getActivity();
      final SampleListDetailAdapter adapter = new SampleListDetailAdapter(activity, skipMemory);

       View v =  LayoutInflater.from(activity)
                .inflate(R.layout.sample_list_detail_list_scrolling, container, false);
      
      ListView listView = (ListView) v.findViewById(R.id.listView);
      listView.setAdapter(adapter);
      listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
          String url = adapter.getItem(position);
          activity.showDetails(url);
        }
      });

      listView.setOnScrollListener(new PicassoFlingScrollListener(activity));
      Toast.makeText(activity, "Fades the loaded images only if ListView is NOT flinging (scrolling will do so)", Toast.LENGTH_LONG).show();

      skipMemoryCacheButton = (Button) v.findViewById(R.id.skipMemoryCacheButton);
      updateButton(skipMemory);
      skipMemoryCacheButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
             boolean skip = adapter.toggleSkipMemoryCache();
              updateButton(skip);
          }
      });

      return v;
    }

      private void updateButton(boolean skip ){
          skipMemoryCacheButton.setText("skip memory cache = "+skip);
      }
  }

  public static class DetailFragment extends Fragment {
    private static final String KEY_URL = "picasso:url";

    public static DetailFragment newInstance(String url) {
      Bundle arguments = new Bundle();
      arguments.putString(KEY_URL, url);

      DetailFragment fragment = new DetailFragment();
      fragment.setArguments(arguments);
      return fragment;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
      Activity activity = getActivity();

      View view = LayoutInflater.from(activity)
          .inflate(R.layout.sample_list_detail_detail, container, false);

      TextView urlView = (TextView) view.findViewById(R.id.url);
      ImageView imageView = (ImageView) view.findViewById(R.id.photo);

      Bundle arguments = getArguments();
      String url = arguments.getString(KEY_URL);

      urlView.setText(url);
      Picasso.with(activity)
          .load(url)
          .fit()
          .into(imageView);

      return view;
    }
  }
}
