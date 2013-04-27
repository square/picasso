package com.example.picasso;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import com.squareup.picasso.Picasso;

import static android.content.Intent.ACTION_PICK;
import static android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

public class SampleGalleryActivity extends PicassoSampleActivity {
  private static final int GALLERY_REQUEST = 9391;
  private static final String KEY_IMAGE = "com.example.picasso:image";

  private ImageView imageView;
  private String image;

  private void loadImage() {
    Picasso.with(this).load(image).into(imageView);
  }

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.sample_gallery_activity);
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
}
