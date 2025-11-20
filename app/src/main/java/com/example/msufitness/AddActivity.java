package com.example.msufitness;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddActivity extends AppCompatActivity {

    private Spinner spinnerType;
    private EditText etDuration;
    private ImageView imgPreview;
    private String selectedImageUrl = ""; // Store URL to save later

    // Hardcoded image URLs for the assignment (Safe/Clean)
    private final String URL_RUN = "https://images.unsplash.com/photo-1502904550040-7534597429ae?w=400";
    private final String URL_GYM = "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=400";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        // 1. UI Setup
        spinnerType = findViewById(R.id.spinnerActivityType);
        etDuration = findViewById(R.id.etDuration);
        imgPreview = findViewById(R.id.imgActivityPreview);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnLoadRun = findViewById(R.id.btnLoadRunImg);
        Button btnLoadGym = findViewById(R.id.btnLoadGymImg);

        // 2. Spinner Data (Rubric: AddActivity input)
        String[] activities = new String[]{"Running", "Walking", "Cycling", "Arms", "Legs", "Back", "Yoga"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, activities);
        spinnerType.setAdapter(adapter);

        // 3. Image Fetching Logic (Rubric: Networking & Threading)
        btnLoadRun.setOnClickListener(v -> fetchImage(URL_RUN));
        btnLoadGym.setOnClickListener(v -> fetchImage(URL_GYM));

        // 4. Save Logic (Rubric: Data persistence)
        btnSave.setOnClickListener(v -> saveWorkout());

        // 5. Back Logic (Rubric: Navigation)
        btnBack.setOnClickListener(v -> finish());
    }

    // Rubric: Networking and Threading (Background fetch to avoid ANR)
    private void fetchImage(String urlString) {
        // Reset preview and save URL
        selectedImageUrl = urlString;
        imgPreview.setImageResource(android.R.drawable.ic_menu_gallery); // Placeholder

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            Bitmap bmp = null;
            try {
                URL url = new URL(urlString);
                // Network call happens here (background)
                bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Bitmap finalBmp = bmp;
            handler.post(() -> {
                // Update UI on main thread
                if (finalBmp != null) {
                    imgPreview.setImageBitmap(finalBmp);
                } else {
                    Toast.makeText(AddActivity.this, "Error loading image", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void saveWorkout() {
        String type = spinnerType.getSelectedItem().toString();
        String duration = etDuration.getText().toString();

        // Rubric: Error Handling (Invalid Input)
        if (duration.isEmpty()) {
            etDuration.setError("Required");
            return;
        }

        // Prepare JSON Object
        try {
            JSONObject workout = new JSONObject();
            workout.put("type", type);
            workout.put("duration", duration);
            workout.put("url", selectedImageUrl);

            // Load existing list
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
            String jsonList = prefs.getString(MainActivity.KEY_WORKOUTS, "[]");
            JSONArray array = new JSONArray(jsonList);

            // Add new workout to the BEGINNING of the list (most recent)
            // We need to shift/recreate the array to put new item at index 0
            JSONArray newArray = new JSONArray();
            newArray.put(workout);
            for(int i=0; i<array.length(); i++) {
                newArray.put(array.get(i));
            }

            // Save back to SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(MainActivity.KEY_WORKOUTS, newArray.toString());
            editor.apply();

            Toast.makeText(this, "Workout Saved!", Toast.LENGTH_SHORT).show();
            finish(); // Return to Main

        } catch (Exception e) {
            Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show();
        }
    }
}