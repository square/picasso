package com.example.picasso;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Toast;
import com.squareup.picasso.scrolling.PicassoScrollListener;

public class SampleGridViewScrollingActivity extends PicassoSampleActivity {

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_gridview_activity_scrolling);

    ViewPager viewPager = (ViewPager) findViewById(R.id.viewPager);
    viewPager.setAdapter(new GridFragmentAdapter(getSupportFragmentManager()));
    viewPager.setPageMargin(30);

    Toast.makeText(this,
        "ViewPager with GridView\n"
            + "Fades loaded images only if GridView is NOT scrolling and NOT flinging",
        Toast.LENGTH_SHORT).show();
  }

  public static class GridFragmentAdapter extends FragmentPagerAdapter {

    public GridFragmentAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override public Fragment getItem(int position) {
      return new GridFragment();
    }

    @Override public int getCount() {
      return 10;
    }
  }

  public static class GridFragment extends Fragment {

    private Button skipMemoryCacheButton;

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
      View v = inflater.inflate(R.layout.sample_gridview_fragment_scrolling, container, false);

      boolean skipMemory = true;

      GridView gv = (GridView) v.findViewById(R.id.grid_view);
      final SampleGridViewAdapter adapter = new SampleGridViewAdapter(getActivity(), skipMemory);
      gv.setAdapter(adapter);
      gv.setOnScrollListener(new PicassoScrollListener(getActivity()));
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

    private void updateButton(boolean skip) {
      skipMemoryCacheButton.setText("skip memory cache = " + skip);
    }
  }
}
