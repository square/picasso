package com.example.picasso;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.squareup.picasso3.provider.PicassoProvider;

public class SampleListDetailActivity extends PicassoSampleActivity {
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

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
      final SampleListDetailActivity activity = (SampleListDetailActivity) getActivity();
      final SampleListDetailAdapter adapter = new SampleListDetailAdapter(activity);

      ListView listView = (ListView) LayoutInflater.from(activity)
          .inflate(R.layout.sample_list_detail_list, container, false);
      listView.setAdapter(adapter);
      listView.setOnScrollListener(new SampleScrollListener(activity));
      listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
          String url = adapter.getItem(position);
          activity.showDetails(url);
        }
      });
      return listView;
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
      SampleListDetailActivity activity = (SampleListDetailActivity) getActivity();

      View view = LayoutInflater.from(activity)
          .inflate(R.layout.sample_list_detail_detail, container, false);

      TextView urlView = view.findViewById(R.id.url);
      ImageView imageView = view.findViewById(R.id.photo);

      Bundle arguments = getArguments();
      String url = arguments.getString(KEY_URL);

      urlView.setText(url);
      PicassoProvider.get()
          .load(url)
          .fit()
          .tag(activity)
          .into(imageView);

      return view;
    }
  }
}
