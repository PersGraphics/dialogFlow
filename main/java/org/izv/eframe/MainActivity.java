package org.izv.eframe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity /*implements TextToSpeech.OnInitListener*/{

    ActivityResultLauncher<Intent> sttLauncher;
    TextView tvDF;
    Intent sttIntent;
    EditText etDF;
    Button btDF;
    SessionName sessionName;
    SessionsClient sessionClient;
    TextToSpeech tts;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sttLauncher = getSttLauncher();
        sttIntent = getSttIntent();
        init();
    }
    boolean ttsReady = false;
    private void init(){
        etDF = findViewById(R.id.et);
        tvDF = findViewById(R.id.tv);
        btDF = findViewById(R.id.bt);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true;
                    tts.setLanguage(new Locale("spa", "ES"));
                }
            }
        });

        if (setupDialogflowClient()) {
            btDF.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendDialogFlow();
                }
            });
        }

    }

    private Boolean setupDialogflowClient() {
        boolean isSetup = false;
        try {
            InputStream stream = getResources().openRawResource(R.raw.clients);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
                    FixedCredentialsProvider.create(credentials)).build();
            sessionClient = SessionsClient.create(sessionsSettings);
            sessionName = SessionName.of(projectId, UUID.randomUUID().toString());
            isSetup = true;
        } catch (Exception e) {
            showMessage("\nError " + e.getMessage() + "\n");
        }
        return isSetup;
    }

     void showMessage(String message) {
        runOnUiThread(() -> {
            tvDF.append(message + tvDF.getText().toString() + ".\n");
        });
    }

     void sendDialogFlow(){
         String inputText = etDF.getText().toString();
         etDF.setText("");
         if(inputText.isEmpty()){
             sttLauncher.launch(sttIntent);
         }else {
             sendMessageToBot(inputText);
         }
    }

     void sendMessageToBot(String message) {
         QueryInput input = QueryInput.newBuilder()
                 .setText(TextInput.newBuilder().setText(message).setLanguageCode("es-ES"))
                 .build();

         new Thread(() -> {
             try {
                 DetectIntentRequest detectIntentRequest = DetectIntentRequest.newBuilder()
                         .setSession(sessionName.toString())
                         .setQueryInput(input)
                         .build();
                 DetectIntentResponse detectIntentResponse = sessionClient.detectIntent(detectIntentRequest);

                 if (detectIntentResponse != null) {
                     String botReply = detectIntentResponse.getQueryResult().getFulfillmentText();
                     if (!botReply.isEmpty()) {
                         showSpeakResult(botReply + "\n");
                     } else {
                         showMessage("Error\n");
                     }
                 } else {
                     showMessage("Error en la conexión\n");
                 }
             } catch (Exception e) {
                 showMessage("Error: " + e.getMessage() + "\n");
             }
         }).start();


    }
    private Intent getSttIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Por favor, habla ahora");
        return intent;

    }

    private ActivityResultLauncher<Intent> getSttLauncher() {
        ActivityResultLauncher<Intent> launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        String text = "Ups...";
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null) {
                                List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                                if (results != null && !results.isEmpty()) {
                                    text = results.get(0);
                                }
                            }
                        } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                            text = "¡Error!";
                        }
                        sendMessageToBot(text);
                    }
                }
        );
        return launcher;

    }
    private void showSpeakResult(String result) {
        runOnUiThread(()->{
            tvDF.append(result + "------------------------\n");
            if(ttsReady){
                tts.speak(result,TextToSpeech.QUEUE_ADD,null,null);
            }
        });

    }

}