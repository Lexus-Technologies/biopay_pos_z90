package com.billzone.biopay;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import cn.com.aratek.fp.Bione;
import cn.com.aratek.fp.FingerprintImage;
import cn.com.aratek.fp.FingerprintScanner;
import cn.com.aratek.util.Result;

public class FingerprintReader {
    private static final String TAG = "FingerprintReader";
    private Context context;
    private FingerprintScanner scanner;
    private String fpDbPath = "/sdcard/fp.db";

    public interface FingerprintCallback {
        void onSuccess(String data);

        void onError(String error);
    }

    public interface VerifyCallback {
        void onSuccess(boolean match);

        void onError(String error);
    }

    public FingerprintReader(Context context) {
        this.context = context;
        this.scanner = FingerprintScanner.getInstance(context);
    }

    public void checkPermission(FingerprintCallback callback) {
        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                    || !Environment.isExternalStorageRemovable()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    fpDbPath = context.getExternalFilesDir("").getAbsolutePath() + "/fp.db";
                } else {
                    fpDbPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/fp.db";
                }
                if (ContextCompat.checkSelfPermission(context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((MainActivity) context,
                            new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE },
                            10086);
                }
            } else {
                fpDbPath = context.getFilesDir().getPath() + "/fp.db";
            }
            callback.onSuccess("true");
        } catch (Exception e) {
            callback.onError("Permission check failed");
        }
    }

    public void captureFinger(FingerprintCallback callback) {
        new Thread(() -> {
            try {
                if (!initFingerprint(callback))
                    return;

                byte[] tpl = captureFeatureOnce();
                if (tpl == null) {
                    teardownFingerprint();
                    callback.onError("Failed to capture fingerprint");
                    return;
                }

                String base64;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    base64 = java.util.Base64.getEncoder().encodeToString(tpl);
                } else {
                    base64 = android.util.Base64.encodeToString(tpl, android.util.Base64.NO_WRAP);
                }

                teardownFingerprint();
                callback.onSuccess(base64);
            } catch (Exception e) {
                teardownFingerprint();
                callback.onError("Capture error: " + e.getMessage());
            }
        }).start();
    }

    public void captureFingerprintToString(FingerprintCallback callback) {
        new Thread(() -> {
            try {
                if (!initFingerprint(callback))
                    return;

                byte[] tpl = captureFeatureOnce();
                if (tpl == null) {
                    teardownFingerprint();
                    callback.onError("Failed to capture fingerprint");
                    return;
                }

                String base64 = android.util.Base64.encodeToString(tpl, android.util.Base64.NO_WRAP);
                teardownFingerprint();
                callback.onSuccess(base64);
            } catch (Exception e) {
                teardownFingerprint();
                callback.onError("Capture error: " + e.getMessage());
            }
        }).start();
    }

    public void verifyFingerprint(String existingScanBase64, VerifyCallback callback) {
        if (existingScanBase64 == null || existingScanBase64.trim().isEmpty()) {
            callback.onError("existingScan is required");
            return;
        }

        new Thread(() -> {
            try {
                if (!initFingerprint(new FingerprintCallback() {
                    @Override
                    public void onSuccess(String data) {
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                }))
                    return;

                byte[] existingTpl;
                try {
                    existingTpl = android.util.Base64.decode(existingScanBase64, android.util.Base64.NO_WRAP);
                } catch (Exception e) {
                    teardownFingerprint();
                    callback.onError("Invalid Base64");
                    return;
                }

                byte[] freshTpl = captureFeatureOnce();
                if (freshTpl == null) {
                    teardownFingerprint();
                    callback.onError("Failed to capture fingerprint");
                    return;
                }

                Result vr = Bione.verify(freshTpl, existingTpl);
                boolean match = (vr != null && vr.data instanceof Boolean) && (Boolean) vr.data;
                teardownFingerprint();
                callback.onSuccess(match);
            } catch (Exception e) {
                teardownFingerprint();
                callback.onError("Verification error: " + e.getMessage());
            }
        }).start();
    }

    private boolean initFingerprint(FingerprintCallback callback) {
        try {
            if (scanner.powerOn() != FingerprintScanner.RESULT_OK) {
                callback.onError("Power on failed");
                return false;
            }
            if (scanner.open() != FingerprintScanner.RESULT_OK) {
                scanner.powerOff();
                callback.onError("Device open failed");
                return false;
            }
            if (Bione.initialize(context, fpDbPath) != Bione.RESULT_OK) {
                scanner.close();
                scanner.powerOff();
                callback.onError("Algorithm init failed");
                return false;
            }
            return true;
        } catch (Exception e) {
            callback.onError("Init error: " + e.getMessage());
            return false;
        }
    }

    private void teardownFingerprint() {
        try {
            scanner.close();
        } catch (Exception ignored) {
        }
        try {
            scanner.powerOff();
        } catch (Exception ignored) {
        }
        try {
            Bione.exit();
        } catch (Exception ignored) {
        }
    }

    private byte[] captureFeatureOnce() {
        try {
            scanner.prepare();
            for (int i = 0; i <= 10; i++) {
                try {
                    Result res = scanner.capture();
                    if (res == null || res.data == null)
                        continue;

                    FingerprintImage fi = (FingerprintImage) res.data;
                    int quality = Bione.getFingerprintQuality(fi);
                    Log.d(TAG, "Fingerprint quality: " + quality);

                    if (fi != null && quality >= 95) {
                        Result feat = Bione.extractIsoFeature(fi);
                        if (feat != null && feat.error == Bione.RESULT_OK && feat.data instanceof byte[]) {
                            byte[] featureData = (byte[]) feat.data;
                            Log.d(TAG, "Feature data length: " + featureData.length);
                            if (featureData.length > 30) {
                                return featureData;
                            }
                            Log.d(TAG, "Feature data too small (" + featureData.length + " bytes), retrying...");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Capture iteration error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Capture error: " + e.getMessage());
        }
        return null;
    }
}
