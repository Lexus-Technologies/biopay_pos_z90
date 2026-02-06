package com.billzone.biopay;

import androidx.annotation.NonNull;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {
    private static final String FINGERPRINTCHANNEL = "com.example.biopay/fingerprintMethodChannel";
    private static final String NFCCHANNEL = "com.example.biopay/nfcMethodChannel";
    private static final String PRINTCHANNEL = "com.example.biopay/printMethodChannel";
    private static final String CARDCHANNEL = "com.example.biopay/cardMethodChannel";
    private static final int PIN_PAD_REQUEST_CODE = 1001;

    private FingerprintReader fingerprintReader;
    private NfcReader nfcReader;
    private ReceiptPrinter receiptPrinter;
    private CardReader cardReader;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine);

        fingerprintReader = new FingerprintReader(this);
        nfcReader = new NfcReader(this);
        receiptPrinter = new ReceiptPrinter(this);
        cardReader = new CardReader(this);

        setupFingerprintChannel(flutterEngine);
        setupNfcChannel(flutterEngine);
        setupPrintChannel(flutterEngine);
        setupCardChannel(flutterEngine);
    }

    private void setupFingerprintChannel(FlutterEngine flutterEngine) {
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), FINGERPRINTCHANNEL)
                .setMethodCallHandler((call, result) -> {
                    switch (call.method) {
                        case "checkPermission":
                            fingerprintReader.checkPermission(new FingerprintReader.FingerprintCallback() {
                                @Override
                                public void onSuccess(String data) {
                                    result.success(true);
                                }

                                @Override
                                public void onError(String error) {
                                    result.error("FAILED", error, null);
                                }
                            });
                            break;

                        case "captureFinger":
                            fingerprintReader.captureFinger(new FingerprintReader.FingerprintCallback() {
                                @Override
                                public void onSuccess(String data) {
                                    result.success(data);
                                }

                                @Override
                                public void onError(String error) {
                                    result.error("FAILED", error, null);
                                }
                            });
                            break;

                        case "captureFingerprintToString":
                            fingerprintReader.captureFingerprintToString(new FingerprintReader.FingerprintCallback() {
                                @Override
                                public void onSuccess(String data) {
                                    result.success(data);
                                }

                                @Override
                                public void onError(String error) {
                                    result.error("FAILED", error, null);
                                }
                            });
                            break;

                        case "verifyFingerprint":
                            String existingScan = call.argument("existingScan");
                            fingerprintReader.verifyFingerprint(existingScan, new FingerprintReader.VerifyCallback() {
                                @Override
                                public void onSuccess(boolean match) {
                                    result.success(match);
                                }

                                @Override
                                public void onError(String error) {
                                    result.error("FAILED", error, null);
                                }
                            });
                            break;

                        default:
                            result.notImplemented();
                            break;
                    }
                });
    }

    private void setupNfcChannel(FlutterEngine flutterEngine) {
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), NFCCHANNEL)
                .setMethodCallHandler((call, result) -> {
                    if (call.method.equals("startBackgroundScanWithoutAdapter")) {
                        nfcReader.startScan(new NfcReader.NfcCallback() {
                            @Override
                            public void onSuccess(String data) {
                                result.success(data);
                            }

                            @Override
                            public void onError(String error) {
                                result.error("Error", error, null);
                            }
                        });
                    } else {
                        result.notImplemented();
                    }
                });
    }

    private void setupPrintChannel(FlutterEngine flutterEngine) {
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), PRINTCHANNEL)
                .setMethodCallHandler((call, result) -> {
                    if (call.method.equals("printReceipt")) {
                        String amount = call.argument("amount");
                        String date = call.argument("date");
                        boolean isSuccessful = Boolean.TRUE.equals(call.argument("isSuccessful"));
                        String terminalNo = call.argument("terminalNo");
                        String reference = call.argument("reference");
                        boolean isBiometric = Boolean.TRUE.equals(call.argument("isBiometric"));
                        boolean isCustomerReceipt = Boolean.TRUE.equals(call.argument("isCustomerReceipt"));
                        String fullName = call.argument("fullName");
                        String bankName = call.argument("bankName");

                        receiptPrinter.printReceipt(amount, date, isSuccessful, terminalNo, reference,
                                isBiometric, isCustomerReceipt, fullName, bankName,
                                new ReceiptPrinter.PrintCallback() {
                                    @Override
                                    public void onSuccess() {
                                        result.success(true);
                                    }

                                    @Override
                                    public void onError(String error) {
                                        result.error("FAILED", error, null);
                                    }
                                });
                    } else {
                        result.notImplemented();
                    }
                });
    }

    private void setupCardChannel(FlutterEngine flutterEngine) {
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CARDCHANNEL)
                .setMethodCallHandler((call, result) -> {
                    switch (call.method) {
                        case "getSerialNumber":
                            result.success(cardReader.getSerialNumber());
                            break;

                        case "readCard":
                            String amount = call.argument("amount");
                            android.util.Log.d("MainActivity", "readCard called with amount: " + amount);
                            cardReader.readCard(amount, new CardReader.CardDataCallback() {
                                @Override
                                public void onCardData(java.util.Map<String, String> data) {
                                    android.util.Log.d("MainActivity", "onCardData callback received, returning to Flutter");
                                    result.success(data);
                                    android.util.Log.d("MainActivity", "Data sent to Flutter successfully");
                                }

                                @Override
                                public void onError(String error) {
                                    android.util.Log.d("MainActivity", "onError callback: " + error);
                                    result.error("CARD_ERROR", error, null);
                                }

                                @Override
                                public void onEvent(String event) {
                                    android.util.Log.d("MainActivity", "onEvent: " + event);
                                }
                            });
                            break;

                        case "cancelRead":
                            cardReader.cancelRead();
                            result.success(true);
                            break;

                        default:
                            result.notImplemented();
                            break;
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PIN_PAD_REQUEST_CODE && cardReader != null) {
            String pin = data != null ? data.getStringExtra(CustomPinPadActivity.EXTRA_PIN) : null;
            cardReader.onPinPadResult(resultCode, pin);
        }
    }
}
