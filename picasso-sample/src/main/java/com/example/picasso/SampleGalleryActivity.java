package com.example.picasso;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.squareup.picasso.Picasso;

import static android.content.Intent.ACTION_PICK;
import static android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
import static com.squareup.picasso.Callback.EmptyCallback;

public class SampleGalleryActivity extends PicassoSampleActivity {
  private static final int GALLERY_REQUEST = 9391;
  private static final int READ_STORAGE_REQUEST = 9392;
  private static final String KEY_IMAGE = "com.example.picasso:image";

  private ImageView imageView;
  private ProgressBar progressBar;
  private String image;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.sample_gallery_activity);

    progressBar = (ProgressBar) findViewById(R.id.progress);
    imageView = (ImageView) findViewById(R.id.image);

    findViewById(R.id.go).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        Intent gallery = new Intent(ACTION_PICK, EXTERNAL_CONTENT_URI);
        startActivityForResult(gallery, GALLERY_REQUEST);
      }
    });

    if (savedInstanceState != null) {
      image = savedInstanceState.getString(KEY_IMAGE);
      if (image != null) {
        loadImage();
      }
    }
  }

  @Override protected void onPause() {
    super.onPause();
    if (isFinishing()) {
      // Always cancel the request here, this is safe to call even if the image has been loaded.
      // This ensures that the anonymous callback we have does not prevent the activity from
      // being garbage collected. It also prevents our callback from getting invoked even after the
      // activity has finished.
      Picasso.with(this).cancelRequest(imageView);
    }
  }

  @Override protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(KEY_IMAGE, image);
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == GALLERY_REQUEST && resultCode == RESULT_OK && data != null) {
      image = data.getData().toString();
      loadImage();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override public void onRequestPermissionsResult(int requestCode,
                                                   @NonNull String[] permissions,
                                                   @NonNull int[] grantResults) {
    if (requestCode == READ_STORAGE_REQUEST) {
      if (grantResults.length > 0
          && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        loadImage();
      }
    }
  }

  private void loadImage() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
          new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_STORAGE_REQUEST);
      return;
    }
    progressBar.setVisibility(View.VISIBLE);
    Picasso.with(this).load(image).fit().centerInside().into(imageView, new EmptyCallback() {
      @Override public void onSuccess() {
        progressBar.setVisibility(View.GONE);
      }
    });
  }
}
