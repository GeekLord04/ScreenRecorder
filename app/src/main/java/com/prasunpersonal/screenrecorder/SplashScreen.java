package com.prasunpersonal.screenrecorder;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.prasunpersonal.screenrecorder.databinding.ActivitySplashScreenBinding;

public class SplashScreen extends AppCompatActivity {
    ActivitySplashScreenBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashScreen.this, MainActivity.class));
            finish();
        }, 2000);
    }
}