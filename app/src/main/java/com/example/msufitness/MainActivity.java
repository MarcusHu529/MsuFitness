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
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private TextView tvStepCount;
    private LinearLayout layoutRecentActivities;
    private ImageView imgPreviewMain;
    private boolean isSensorPresent = false;

    // Variables for Persistence and Reset Logic
    private float stepsAtReset = -1;
    private String lastSavedDate = ""; // To track the day

    // Keys for Persistence
    public static final String PREFS_NAME = "MsuFitnessPrefs";
    public static final String KEY_WORKOUTS = "workout_list";
    private static final String KEY_STEP_BASELINE = "steps_baseline";
    private static final String KEY_LAST_DATE = "steps_last_date"; // NEW KEY

    private static final int PERMISSION_REQUEST_CODE = 100;

    // Single executor to avoid leaks (Better Practice)
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        // 3. Retrieve saved data
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        stepsAtReset = prefs.getFloat(KEY_STEP_BASELINE, -1);
        lastSavedDate = prefs.getString(KEY_LAST_DATE, "");

        // 4. Check Permissions
        checkSensorPermissions();

        // 5. Button Logic
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Fix: Add null check to prevent crash on Emulator
        if (isSensorPresent && stepSensor != null &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
        loadRecentActivities();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isSensorPresent && sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Fix: Shut down executor to prevent memory leaks
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // --- Sensor Logic ---
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            float totalStepsOnDevice = event.values[0];

            // Get the current date (e.g., "2023-11-19")
            String todayDate = LocalDate.now().toString();

            boolean needToSave = false;

            // LOGIC 1: First run ever
            if (stepsAtReset == -1) {
                stepsAtReset = totalStepsOnDevice;
                lastSavedDate = todayDate;
                needToSave = true;
            }
            // LOGIC 2: New Day Detected -> Reset counter for today
            else if (!todayDate.equals(lastSavedDate)) {
                stepsAtReset = totalStepsOnDevice; // Current total becomes the new zero
                lastSavedDate = todayDate;
                needToSave = true;
            }
            // LOGIC 3: Device Reboot (Sensor reset to 0)
            else if (totalStepsOnDevice < stepsAtReset) {
                stepsAtReset = totalStepsOnDevice;
                needToSave = true;
            }

            if (needToSave) {
                saveStepData();
            }

            // Calculate steps for today
            int currentSessionSteps = (int)(totalStepsOnDevice - stepsAtReset);

            // Prevent negative numbers (just in case of weird sensor glitches)
            if (currentSessionSteps < 0) currentSessionSteps = 0;

            tvStepCount.setText(String.valueOf(currentSessionSteps));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    // Helper method to save baseline AND date
    private void saveStepData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat(KEY_STEP_BASELINE, stepsAtReset);
        editor.putString(KEY_LAST_DATE, lastSavedDate);
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
            for (int i = 0; i < array.length() && i < 5; i++) {
                JSONObject obj = array.getJSONObject(i);
                String type = obj.getString("type");
                String duration = obj.getString("duration");
                String imgUrl = obj.optString("url", "");

                TextView itemView = new TextView(this);
                itemView.setText(String.format("• %s (%s mins)", type, duration));
                itemView.setTextSize(18f);
                itemView.setPadding(16, 16, 16, 16);

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
            e.printStackTrace();
        }
    }

    private void fetchImageForPreview(String urlString) {
        Toast.makeText(this, "Loading image...", Toast.LENGTH_SHORT).show();
        Handler handler = new Handler(Looper.getMainLooper());

        // Uses the class-level executor (Fixing the thread leak)
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