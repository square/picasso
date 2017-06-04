/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.Nullable;

import java.io.IOException;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

public class AppIconRequestHandler extends RequestHandler {

    private static final String SCHEME_PACKAGE_NAME = "package";
    private static final String SCHEME_APK_PATH = "path";
    private static final int PACKAGE_PREFIX_LENGTH =
            (SCHEME_PACKAGE_NAME + "://").length();
    private static final int PATH_PREFIX_LENGTH =
            (SCHEME_APK_PATH + "://").length();

    private final Context context;
    private final Object lock = new Object();
    private PackageManager packageManager;

    public AppIconRequestHandler(Context context) {
        this.context = context;
    }

    @Override
    public boolean canHandleRequest(Request data) {
        Uri uri = data.uri;
        return SCHEME_PACKAGE_NAME.equals(uri.getScheme()) && !uri.getHost().isEmpty() || SCHEME_APK_PATH.equals(uri.getScheme());
    }

    @Nullable
    @Override
    public Result load(Request request, int networkPolicy) throws IOException {
        Uri uri = request.uri;
        if (null == packageManager) {
            synchronized (lock) {
                if (null == packageManager) {
                    packageManager = context.getPackageManager();
                }
            }
        }
        try {
            Drawable drawable = null;

            if (SCHEME_APK_PATH.equals(uri.getScheme())) {
                String apkPath = getApkPath(request);
                PackageInfo info = packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
                if (info != null) {
                    ApplicationInfo appInfo = info.applicationInfo;
                    appInfo.sourceDir = apkPath;
                    appInfo.publicSourceDir = apkPath;
                    drawable = appInfo.loadIcon(packageManager);
                }
            }

            if (SCHEME_PACKAGE_NAME.equals(uri.getScheme())) {
                drawable = packageManager.getApplicationIcon(getPackageName(request));
            }

            if (drawable != null && drawable instanceof BitmapDrawable) {
                return new Result(((BitmapDrawable) drawable).getBitmap(), DISK);
            }


        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getApkPath(Request request) {
        return request.uri.toString().substring(PATH_PREFIX_LENGTH);
    }

    private static String getPackageName(Request request) {
        return request.uri.toString().substring(PACKAGE_PREFIX_LENGTH);
    }

}
