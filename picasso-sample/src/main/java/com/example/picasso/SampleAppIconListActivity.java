package com.example.picasso;

import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.io.File;
import java.util.List;

public class SampleAppIconListActivity extends PicassoSampleActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.sample_content, ListFragment.newInstance())
                    .commit();
        }
    }

    private static final String SCHEME_PATH = "path://";

    public static class ListFragment extends Fragment {
        public static ListFragment newInstance() {
            return new ListFragment();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final SampleAppIconListActivity activity = (SampleAppIconListActivity) getActivity();
            List<String> userPkgInfoList = PackagLoader.getUserPkgInfoList(getActivity());
            userPkgInfoList.add(SCHEME_PATH.concat(getSDPath().concat("/test_apk_icon.apk")));
            final SampleAppIconListAdapter adapter = new SampleAppIconListAdapter(activity, userPkgInfoList);

            ListView listView = (ListView) LayoutInflater.from(activity)
                    .inflate(R.layout.sample_list_detail_list, container, false);
            listView.setAdapter(adapter);
            listView.setOnScrollListener(new SampleScrollListener(activity));
            return listView;
        }
    }

    public static String getSDPath() {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();
        }
        return sdDir.toString();
    }

}
