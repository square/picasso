package com.example.picasso;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ViewAnimator;
import com.squareup.picasso.Picasso;

import static android.content.Intent.ACTION_PICK;
import static android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
import static com.squareup.picasso.Callback.EmptyCallback;

public class SampleGalleryActivity extends PicassoSampleActivity {
  private static final int GALLERY_REQUEST = 9391;
  private static final String KEY_IMAGE = "com.example.picasso:image";

  private ImageView imageView;
  private ViewAnimator animator;
  private String image;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.sample_gallery_activity);

    animator = (ViewAnimator) findViewById(R.id.animator);
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

  private void loadImage() {
    // Index 1 is the progress bar. Show it while we're loading the image.
    animator.setDisplayedChild(1);

    Picasso.with(this).load(image).into(imageView, new EmptyCallback() {
      @Override public void onSuccess() {
        // Index 0 is the image view.
        animator.setDisplayedChild(0);
      }
    });
  }
}
