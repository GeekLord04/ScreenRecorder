package com.prasunpersonal.screenrecorder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.PermissionListener;
import com.prasunpersonal.screenrecorder.databinding.ActivityMainBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    // Declaring all necessary variables
    ActivityMainBinding binding;

    private DisplayMetrics metrics;
    private MediaRecorder mediaRecorder;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;

    private boolean isRecording;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private long seconds;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final Map<String, Integer> resolutionsValues = new HashMap<>();
    private static final String[] resolutions = new String[]{"2160p", "1440p", "1080p", "720p"};
    private static final String[] fps = new String[]{"60", "30", "24", "15"};

    // Initialising necessary static variables
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);

        resolutionsValues.put("720p", 9500000);
        resolutionsValues.put("1080p", 15000000);
        resolutionsValues.put("1440p", 30000000);
        resolutionsValues.put("2160p", 85000000);
    }

    // ActivityResultLauncher is used for seeking permission to record screen
    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                // Initializing the media projector. This will used to create virtual display
                mediaProjection = mediaProjectionManager.getMediaProjection(result.getResultCode(), result.getData());
                startRecording();
            }
        }
    });

    // onCreate() is a default method of activity class. It is called at the time of initialisation of the page
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater()); // Initializing the binding
        setContentView(binding.getRoot());

        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics); // Initializing the display metrics

        mediaRecorder = new MediaRecorder(); // Initializing the media recorder
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE); // Initializing the media project manager

        isRecording = false;

        // Creating and attaching the adapter for the resolution Dropdown
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, resolutions);
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.resolution.setAdapter(resolutionAdapter);

        // Creating and attaching the adapter for the FPS Dropdown
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fps);
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.fps.setAdapter(fpsAdapter);

        // This is the switch to toggle the screen recording
        binding.recordSwitch.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording(); // Stopping the recording
                stopService(new Intent(MainActivity.this, ForegroundService.class)); // Stopping the foreground notification service
            } else {
                // Checking if the permission is granted or not.
                    // If yes, starting the screen record
                    // Otherwise requesting the permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        startService(new Intent(MainActivity.this, ForegroundService.class));
                        startRecording();
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setIcon(R.drawable.ic_warning);
                        builder.setTitle("Need Permission");
                        builder.setMessage("This app needs all files access permission. You can enable it in app settings.");
                        builder.setCancelable(false);
                        builder.setPositiveButton("Settings", (dialog, which) -> {
                            dialog.cancel();
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            intent.addCategory(Intent.CATEGORY_DEFAULT);
                            startActivityForResult(intent, 100);
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> {
                            dialog.cancel();
                        });
                        builder.create().show();
                    }
                } else {
                    // Requesting the permission
                    Dexter.withContext(this).withPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE).withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                            if (multiplePermissionsReport.isAnyPermissionPermanentlyDenied()) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                builder.setIcon(R.drawable.ic_warning);
                                builder.setTitle("Need Permission");
                                builder.setMessage("This app needs all files access permission. You can enable it in app settings.");
                                builder.setCancelable(false);
                                builder.setPositiveButton("Settings", (dialog, which) -> {
                                    dialog.cancel();
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                                    startActivityForResult(intent, 100);
                                });
                                builder.setNegativeButton("Cancel", (dialog, which) -> {
                                    dialog.cancel();
                                });
                                builder.create().show();
                            } else {
                                startService(new Intent(MainActivity.this, ForegroundService.class));
                                startRecording();
                            }
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
                            permissionToken.continuePermissionRequest();
                        }
                    }).onSameThread().check();
                }
            }
        });

        // Audio switch will be enabled by default if the audio permission is granted
        binding.audioSwitch.setChecked(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);

        // Checking the permission on changing the switch state
        binding.audioSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Checking if the permission is granted or not.
                // If yes, making the audio button enable
                // Otherwise requesting the permission

            if (isChecked) {
                Dexter.withContext(this).withPermission(Manifest.permission.RECORD_AUDIO).withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        buttonView.setChecked(true);
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        buttonView.setChecked(false);
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setIcon(R.drawable.ic_warning);
                        builder.setTitle("Need Permission");
                        builder.setMessage("The audio recording permission has been denied. You can grant the permission from settings.");
                        builder.setCancelable(false);
                        builder.setPositiveButton("Settings", (dialog, which) -> {
                            dialog.cancel();
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.addCategory(Intent.CATEGORY_DEFAULT);
                            intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                            startActivityForResult(intent, 100);
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> {
                            dialog.cancel();
                        });
                        builder.create().show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).onSameThread().check();
            }
        });
    }

    // This function will initialize the recorder
    private void initializeRecorder() {
        File folder = new File(Environment.getExternalStorageDirectory(), getString(R.string.app_name));
        // Creating the folder if not exists
        if (folder.exists() || folder.mkdirs()) {
            // Creating the output file
            File file = new File(folder, new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date()) + ".mp4");
            try {
                if (binding.audioSwitch.isChecked()) mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT); // Setting the audio source if the record audio button is enabled
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE); // Setting the video source
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); //  Setting the output format (.mp4)
                if (binding.audioSwitch.isChecked()) mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB); // Setting the audio encoder if the record audio button is enabled
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264); // Setting the video encoder
                mediaRecorder.setVideoSize((metrics.widthPixels % 2 == 0 ? metrics.widthPixels : metrics.widthPixels + 1), (metrics.heightPixels % 2 == 0 ? metrics.heightPixels : metrics.heightPixels + 1)); // Setting video resolution
                mediaRecorder.setVideoFrameRate(Integer.parseInt(binding.fps.getSelectedItem().toString())); // Setting the user selected FPS
                if (binding.audioSwitch.isChecked()) mediaRecorder.setAudioEncodingBitRate(resolutionsValues.get(binding.resolution.getSelectedItem().toString()) * 3 / 4); // Setting the audio Bitrate based on user selected video Bitrate  if the record audio button is enabled
                mediaRecorder.setVideoEncodingBitRate(resolutionsValues.get(binding.resolution.getSelectedItem().toString())); // Setting the user selected video Bitrate
                mediaRecorder.setOrientationHint(ORIENTATIONS.get(getWindowManager().getDefaultDisplay().getRotation() + 90)); // Setting the screen orientation
                mediaRecorder.setOutputFile(file); // Setting the output file to the media recorder
                mediaRecorder.prepare(); // preparing the media recorder
            } catch (IOException e) {
                // handling the exception
                e.printStackTrace();
            }
        }
    }

    // This function will start the recording
    private void startRecording() {
        if (mediaProjection == null) {
            launcher.launch(mediaProjectionManager.createScreenCaptureIntent()); // Opening the screen capture intent
            return;
        }

        isRecording = true;
        binding.recordSwitch.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_pause));
        binding.audioSwitch.setEnabled(false);
        binding.resolution.setEnabled(false);
        binding.fps.setEnabled(false);
        binding.progressArea.setVisibility(View.VISIBLE);

        initializeRecorder(); // Initialising the recorder
        virtualDisplay = createVirtualDisplay(); // Initialising the virtual display
        mediaRecorder.start(); // starting the media recorder
        timeHandler = new Handler(); // Initialising the time stamp handler
        timeHandler.post(timeRunnable = new Runnable() {
            // Initialising the time stamp runnable and assign it to time stamp handler
            @Override
            public void run() {
                seconds++; // Increasing the time by second
                binding.timeStamp.setText(String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)); // Setting the time stamp
                timeHandler.postDelayed(this, 1000); // Making the handler by second
            }
        });
    }

    // This function will stop the screen recording
    private void stopRecording() {
        mediaRecorder.stop(); // stopping the media recorder
        mediaRecorder.reset(); // resetting the media recorder
        virtualDisplay.release(); // releasing the media recorder
        timeHandler.removeCallbacks(timeRunnable); // stopping the time stamp
        seconds = 0;

        isRecording = false;
        binding.recordSwitch.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play));
        binding.audioSwitch.setEnabled(true);
        binding.resolution.setEnabled(true);
        binding.fps.setEnabled(true);
        binding.progressArea.setVisibility(View.GONE);
    }

    // This function will create a virtual display. This virtual display will be recorded
    private VirtualDisplay createVirtualDisplay() {
        return mediaProjection.createVirtualDisplay(getString(R.string.app_name),
                (metrics.widthPixels % 2 == 0 ? metrics.widthPixels : metrics.widthPixels + 1),
                (metrics.heightPixels % 2 == 0 ? metrics.heightPixels : metrics.heightPixels + 1),
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);
    }
}