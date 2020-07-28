package com.jurebevc.picturehunter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

public class MainActivity extends Activity {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    private Uri photoURI;
    private List<String> labels = new ArrayList<>();
    private String currentTopic;
    final float[] topicMatch = {0};
    int streak = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.streak_text).setVisibility(View.INVISIBLE);
        findViewById(R.id.topic_match_text).setVisibility(View.INVISIBLE);
        loadLabels();
        generateTopic();
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        FloatingActionButton refreshTopicButton = (FloatingActionButton) findViewById(R.id.refreshTopicButton);
        FloatingActionButton continueButton = (FloatingActionButton) findViewById(R.id.continue_button);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                //        .setAction("Action", null).show();
            }
        });
        refreshTopicButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streak = 0;
                updateStreakText();
                generateTopic();
                Snackbar.make(view, "Your streak has been reset.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                generateTopic();
                streak+=1;
                updateStreakText();
                ((ImageView)findViewById(R.id.last_captured_image)).setImageBitmap(null);
                disableContinue();
            }
        });
    }

    private void updateStreakText() {
        ((TextView)findViewById(R.id.streak_text)).setText("Streak: " + streak);
    }

    private void generateTopic() {
        do {
            currentTopic = labels.get((int) (Math.random() * labels.size()));
        } while (currentTopic.equals("???"));
        ((TextView) findViewById(R.id.current_topic)).setText(currentTopic);
        topicMatch[0] = 0;
        refreshMatchText();
    }

    private void refreshMatchText() {
        Log.w("DETECTED LABEL", "Setting match text " + topicMatch[0]);
        ((TextView)findViewById(R.id.topic_match_text)).setText("Topic match: " + (int)(topicMatch[0] * 100) + "%");
    }

    private void enableContinue(){
        findViewById(R.id.continue_button).setVisibility(View.VISIBLE);
    }

    private void disableContinue(){
        findViewById(R.id.continue_button).setVisibility(View.INVISIBLE);
    }

    private void loadLabels() {
        labels.clear();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(getAssets().open("labels_224.txt")));

            String mLine;
            while ((mLine = reader.readLine()) != null) {
                labels.add(mLine.trim());
            }
        } catch (IOException e) {
            //log the exception
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    //log the exception
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bitmap bitmap;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoURI);
                Log.i("BITMAP SIZE", "" + bitmap.getWidth() + " " + bitmap.getHeight());
                //objectDetector(bitmap);
                customDetector(bitmap);
                findViewById(R.id.streak_text).setVisibility(View.VISIBLE);
                findViewById(R.id.topic_match_text).setVisibility(View.VISIBLE);
                ((ImageView)findViewById(R.id.last_captured_image)).setImageBitmap(bitmap);

                //((ImageView) findViewById(R.id.capturedImage)).setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void customDetector(Bitmap bitmap) {
        LocalModel localModel =
                new LocalModel.Builder()
                        .setAssetFilePath("mobilenet_224.tflite")
                        .build();
        CustomObjectDetectorOptions customObjectDetectorOptions =
                new CustomObjectDetectorOptions.Builder(localModel)
                        .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()
                        .setClassificationConfidenceThreshold(0.0f)
                        .setMaxPerObjectLabelCount(5)
                        .build();
        ObjectDetector objectDetector =
                ObjectDetection.getClient(customObjectDetectorOptions);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        objectDetector.process(image)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("DETECTION FAILED", "Image detector failed!");
                        e.printStackTrace();
                    }
                })
                .addOnSuccessListener(new OnSuccessListener<List<DetectedObject>>() {
                    @Override
                    public void onSuccess(List<DetectedObject> results) {
                        Log.i("DETECTED", "Objects detected: " + results.size());
                        for (DetectedObject detectedObject : results) {
                            Log.i("DETECTED", "Object labels: " + detectedObject.getLabels().size());
                            for (DetectedObject.Label label : detectedObject.getLabels()) {
                                String labelText = "";
                                if (labels.size() > label.getIndex())
                                    labelText = labels.get(label.getIndex());
                                Log.i("DETECTED LABEL", labelText + "(" + label.getIndex() + ") - " + label.getConfidence());
                                Log.i("DETECTED LABEL", "Comparisson " + (currentTopic.equals(labelText)));
                                if(currentTopic.equals(labelText) && label.getConfidence() > topicMatch[0])
                                    topicMatch[0] = label.getConfidence();
                                Log.i("DETECTED LABEL", "Match value " + topicMatch[0]);
                            }
                        }
                        refreshMatchText();
                        if(topicMatch[0] >= 0.4)
                            enableContinue();
                    }
                });
    }

    private void objectDetector(Bitmap bitmap) {
        ObjectDetectorOptions options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()  // Optional
                        .build();
        ObjectDetector objectDetector = ObjectDetection.getClient(options);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        objectDetector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<DetectedObject>>() {
                            @Override
                            public void onSuccess(List<DetectedObject> detectedObjects) {
                                Log.i("DETECTED OBJECTS", "Detected list size: " + detectedObjects.size());
                                for (DetectedObject detectedObject : detectedObjects) {
                                    //Rect boundingBox = detectedObject.getBoundingBox();
                                    //Integer trackingId = detectedObject.getTrackingId();
                                    Log.i("DETECTED LABELS", "Number of labels: " + detectedObject.getLabels().size());
                                    for (DetectedObject.Label label : detectedObject.getLabels()) {
                                        String text = label.getText();
                                        float confidence = label.getConfidence();
                                        Log.i("DETECTED LABEL", text + ": " + confidence);
                                    }
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.i("DETECTION FAIL", "Detection failed!");
                            }
                        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e("FILE ERROR", "Error creating file for picture intent!");
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        "com.jurebevc.picturehunter.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        return image;
    }
}
