package com.example.picasso;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

public class PackagLoader {


    public static List<PackageInfo> getInstalledPackagesNoThrow(PackageManager pm, int flags) {
        List<PackageInfo> pkgList = null;
        try {
            pkgList = pm.getInstalledPackages(flags);
        } catch (Throwable e) {
        }
        if (pkgList != null) {
        }
        return pkgList;
    }

    private static final String SCHEME_PATH = "package://";

    public static List<String> getUserPkgInfoList(Context context) {
        PackageManager mPM = context.getPackageManager();
        List<PackageInfo> allPkgs = getInstalledPackagesNoThrow(mPM, 0);
        if (allPkgs == null) {
            return null;
        }
        List<String> list = new ArrayList<String>();
        for (PackageInfo packageInfo : allPkgs) {
            if (isUserApp(packageInfo.applicationInfo)) {
                list.add(SCHEME_PATH.concat(packageInfo.packageName));
            }
        }
        return list;
    }

    public static boolean isUserApp(ApplicationInfo info) {
        if (info == null) {
            return false;
        }
        return !((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
    }

}
