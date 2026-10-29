package com.example.chatapp;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** A photo of a conversation, full screen; tap anywhere to close. */
public class ImageViewerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Photo");
        root.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ProgressBar progress = new ProgressBar(this);
        root.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER));
        setContentView(root);
        root.setOnClickListener(v -> finish());

        String attachment = getIntent().getStringExtra("attachment");
        if (attachment == null) {
            finish();
            return;
        }
        Media.image(this, attachment, 2048, bitmap -> {
            progress.setVisibility(android.view.View.GONE);
            if (bitmap == null) {
                Toast.makeText(this, "Could not load the photo", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                image.setImageBitmap(bitmap);
            }
        });
    }
}
