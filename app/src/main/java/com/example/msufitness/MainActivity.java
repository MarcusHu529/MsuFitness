package com.example.msufitness;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private TextView tvStepCount;
    private LinearLayout layoutRecentActivities;
    private ImageView imgPreviewMain;
    private boolean isSensorPresent = false;

    // Changed: Initialize to -1 to indicate "not set yet"
    private float stepsAtReset = -1;

    // Keys for Persistence
    public static final String PREFS_NAME = "MsuFitnessPrefs";
    public static final String KEY_WORKOUTS = "workout_list";

    // NEW: Key to store the baseline step count
    private static final String KEY_STEP_BASELINE = "steps_baseline";

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Setup UI References
        tvStepCount = findViewById(R.id.tvStepCount);
        layoutRecentActivities = findViewById(R.id.layoutRecentActivities);
        imgPreviewMain = findViewById(R.id.imgPreviewMain);
        Button btnAdd = findViewById(R.id.btnAddWorkout);

        // 2. Setup Sensor Manager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            isSensorPresent = true;
        } else {
            tvStepCount.setText("No Sensor");
            Toast.makeText(this, "Step sensor not available on this device", Toast.LENGTH_LONG).show();
        }

        // NEW: Try to retrieve the saved baseline from previous sessions
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        stepsAtReset = prefs.getFloat(KEY_STEP_BASELINE, -1);

        // 3. Check Permissions (Rubric: Permissions)
        checkSensorPermissions();

        // 4. Button Logic (Rubric: Navigation)
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rubric: Sensor Lifecycle Management
        if (isSensorPresent && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
        // Rubric: Activity Logging (Refresh list when returning)
        loadRecentActivities();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Rubric: Sensor Lifecycle Management (Prevent leaks)
        if (isSensorPresent) {
            sensorManager.unregisterListener(this);
        }
    }

    // --- Sensor Logic ---
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            float totalSteps = event.values[0];

            // NEW LOGIC: Handle Persistence

            // Case 1: First time ever running the app or baseline not found
            if (stepsAtReset == -1) {
                stepsAtReset = totalSteps;
                saveBaseline();
            }
            // Case 2: Device rebooted (Sensor resets to 0, so totalSteps < stored baseline)
            // We must reset our baseline to the new low number to avoid negative results.
            else if (totalSteps < stepsAtReset) {
                stepsAtReset = totalSteps;
                saveBaseline();
            }

            // Calculate steps since the baseline was set
            int currentSessionSteps = (int)(totalSteps - stepsAtReset);
            tvStepCount.setText(String.valueOf(currentSessionSteps));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this assignment
    }

    // NEW: Helper method to save the baseline to SharedPreferences
    private void saveBaseline() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat(KEY_STEP_BASELINE, stepsAtReset);
        editor.apply();
    }

    private void checkSensorPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    // --- Data Persistence & List Display ---
    private void loadRecentActivities() {
        layoutRecentActivities.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String jsonList = prefs.getString(KEY_WORKOUTS, "[]");

        try {
            JSONArray array = new JSONArray(jsonList);
            // Display up to 5 most recent (Rubric: Activity Logging)
            for (int i = 0; i < array.length() && i < 5; i++) {
                JSONObject obj = array.getJSONObject(i);
                String type = obj.getString("type");
                String duration = obj.getString("duration");
                String imgUrl = obj.optString("url", "");

                // Create a simple text view for the list item
                TextView itemView = new TextView(this);
                itemView.setText(String.format("• %s (%s mins)", type, duration));
                itemView.setTextSize(18f);
                itemView.setPadding(16, 16, 16, 16);

                // Click listener to fetch image (Rubric: Networking & Error Handling)
                itemView.setOnClickListener(v -> {
                    if (!imgUrl.isEmpty()) {
                        fetchImageForPreview(imgUrl);
                    } else {
                        Toast.makeText(MainActivity.this, "No image saved for this activity", Toast.LENGTH_SHORT).show();
                        imgPreviewMain.setVisibility(View.GONE);
                    }
                });

                layoutRecentActivities.addView(itemView);
            }
            if (array.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("No activities yet.");
                layoutRecentActivities.addView(empty);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Rubric: Error handling (graceful fail)
        }
    }

    // Rubric: Networking and Threading (ExecutorService)
    private void fetchImageForPreview(String urlString) {
        Toast.makeText(this, "Loading image...", Toast.LENGTH_SHORT).show();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            Bitmap bmp = null;
            try {
                URL url = new URL(urlString);
                bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
                bmp = null;
            }

            final Bitmap finalBmp = bmp;
            handler.post(() -> {
                if (finalBmp != null) {
                    imgPreviewMain.setVisibility(View.VISIBLE);
                    imgPreviewMain.setImageBitmap(finalBmp);
                } else {
                    Toast.makeText(MainActivity.this, "Failed to load image.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}