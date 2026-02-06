package com.billzone.biopay;

import android.app.Activity;
import android.content.Intent;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomPinPadActivity extends Activity {
    private static final String EXTRA_CARD_NUMBER = "card_number";
    private static final String EXTRA_MIN_LENGTH = "min_length";
    private static final String EXTRA_MAX_LENGTH = "max_length";
    private static final String EXTRA_RETRY_COUNT = "retry_count";
    public static final String EXTRA_PIN = "pin";
    public static final int RESULT_CANCELLED = RESULT_CANCELED;
    public static final int RESULT_TIMEOUT = 2;

    private StringBuilder pinBuilder = new StringBuilder();
    private TextView pinDisplay;
    private TextView tvRetryMessage;
    private int minLength = 4;
    private int maxLength = 12;
    private int retryCount = 0;
    private Button[] digitButtons = new Button[10];
    private Button btnConfirm;
    private Button btnClear;
    private Button btnCancel;
    private ToneGenerator toneGenerator;

    public static Intent createIntent(Activity activity, String cardNumber, int minLength, int maxLength,
            int retryCount) {
        Intent intent = new Intent(activity, CustomPinPadActivity.class);
        intent.putExtra(EXTRA_CARD_NUMBER, cardNumber);
        intent.putExtra(EXTRA_MIN_LENGTH, minLength);
        intent.putExtra(EXTRA_MAX_LENGTH, maxLength);
        intent.putExtra(EXTRA_RETRY_COUNT, retryCount);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_pinpad);

        minLength = getIntent().getIntExtra(EXTRA_MIN_LENGTH, 4);
        maxLength = getIntent().getIntExtra(EXTRA_MAX_LENGTH, 12);
        retryCount = getIntent().getIntExtra(EXTRA_RETRY_COUNT, 0);
        String cardNumber = getIntent().getStringExtra(EXTRA_CARD_NUMBER);

        pinDisplay = findViewById(R.id.pinDisplay);
        tvRetryMessage = findViewById(R.id.tvRetryMessage);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnClear = findViewById(R.id.btnClear);
        btnCancel = findViewById(R.id.btnCancel);

        digitButtons[0] = findViewById(R.id.btn0);
        digitButtons[1] = findViewById(R.id.btn1);
        digitButtons[2] = findViewById(R.id.btn2);
        digitButtons[3] = findViewById(R.id.btn3);
        digitButtons[4] = findViewById(R.id.btn4);
        digitButtons[5] = findViewById(R.id.btn5);
        digitButtons[6] = findViewById(R.id.btn6);
        digitButtons[7] = findViewById(R.id.btn7);
        digitButtons[8] = findViewById(R.id.btn8);
        digitButtons[9] = findViewById(R.id.btn9);

        toneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);

        if (retryCount > 0) {
            tvRetryMessage.setVisibility(View.VISIBLE);
            tvRetryMessage.setText("Incorrect PIN. Attempt " + (retryCount + 1) + " of 3");
        } else {
            tvRetryMessage.setVisibility(View.GONE);
        }

        randomizeKeypad();
        setupListeners();
        updateConfirmButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }

    private void randomizeKeypad() {
        List<Integer> digits = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            digits.add(i);
        }
        Collections.shuffle(digits);

        for (int i = 0; i < 10; i++) {
            final int digit = digits.get(i);
            digitButtons[i].setText(String.valueOf(digit));
            digitButtons[i].setOnClickListener(v -> onDigitClick(digit));
        }
    }

    private void setupListeners() {
        btnConfirm.setOnClickListener(v -> {
            if (pinBuilder.length() >= minLength) {
                playBeep();
                Intent result = new Intent();
                result.putExtra(EXTRA_PIN, pinBuilder.toString());
                setResult(RESULT_OK, result);
                finish();
            }
        });

        btnClear.setOnClickListener(v -> {
            if (pinBuilder.length() > 0) {
                playBeep();
                pinBuilder.deleteCharAt(pinBuilder.length() - 1);
                updatePinDisplay();
                updateConfirmButton();
            }
        });

        btnCancel.setOnClickListener(v -> {
            playBeep();
            setResult(RESULT_CANCELLED);
            finish();
        });
    }

    private void onDigitClick(int digit) {
        if (pinBuilder.length() < maxLength) {
            playBeep();
            pinBuilder.append(digit);
            updatePinDisplay();
            updateConfirmButton();
        }
    }

    private void playBeep() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_9, 100);
        }
    }

    private void updatePinDisplay() {
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < pinBuilder.length(); i++) {
            display.append("●");
        }
        pinDisplay.setText(display.toString());
    }

    private void updateConfirmButton() {
        btnConfirm.setEnabled(pinBuilder.length() >= minLength);
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELLED);
        super.onBackPressed();
    }
}
