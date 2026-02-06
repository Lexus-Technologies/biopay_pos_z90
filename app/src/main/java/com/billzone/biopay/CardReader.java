package com.billzone.biopay;

import android.content.Context;
import android.util.Log;

import com.zcs.sdk.DriverManager;
import com.zcs.sdk.SdkResult;
import com.zcs.sdk.card.CardInfoEntity;
import com.zcs.sdk.card.CardReaderManager;
import com.zcs.sdk.card.CardReaderTypeEnum;
import com.zcs.sdk.card.CardSlotNoEnum;
import com.zcs.sdk.card.ICCard;
import com.zcs.sdk.emv.EmvApp;
import com.zcs.sdk.emv.EmvCapk;
import com.zcs.sdk.emv.EmvData;
import com.zcs.sdk.emv.EmvHandler;
import com.zcs.sdk.emv.EmvResult;
import com.zcs.sdk.emv.EmvTermParam;
import com.zcs.sdk.emv.EmvTransParam;
import com.zcs.sdk.emv.OnEmvListener;
import com.zcs.sdk.listener.OnSearchCardListener;
import com.zcs.sdk.pin.PinAlgorithmMode;
import com.zcs.sdk.pin.pinpad.PinPadManager;
import com.zcs.sdk.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class CardReader {
    private static final String TAG = "CardReader";
    private Context context;
    private DriverManager driverManager;
    private CardReaderManager cardReaderManager;
    private PinPadManager pinPadManager;
    private EmvHandler emvHandler;
    private ICCard icCard;

    private String cardPan = "";
    private String track2Data = "";
    private String expiryDate = "";
    private String iccData = "";
    private String cardSequenceNumber = "";
    private String posDataCode = "";
    private String clearPin = "";
    private int keyIndex = 0;
    private byte[] pinBlock = new byte[8];
    private CountDownLatch pinLatch;
    private int pinResult = EmvResult.EMV_NO_PASSWORD;
    private int pinRetryCount = 0;
    private static final int MAX_PIN_RETRIES = 3;

    public interface CardDataCallback {
        void onCardData(Map<String, String> data);

        void onError(String error);

        void onEvent(String event);
    }

    public CardReader(Context context) {
        this.context = context;
        initialize();
        setupKeys();
    }

    private void initialize() {
        try {
            driverManager = DriverManager.getInstance();
            cardReaderManager = driverManager.getCardReadManager();
            pinPadManager = driverManager.getPadManager();
            emvHandler = EmvHandler.getInstance();
            icCard = cardReaderManager.getICCard();

            int status = driverManager.getBaseSysDevice().sdkInit();
            if (status != SdkResult.SDK_OK) {
                driverManager.getBaseSysDevice().sysPowerOn();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                driverManager.getBaseSysDevice().sdkInit();
            }

            Log.d(TAG, "CardReader initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Initialization error: " + e.getMessage());
        }
    }

    private void loadAids() {
    }

    private void setupKeys() {
        // Keys managed by backend/payment processor
    }

    private volatile boolean callbackInvoked = false;

    public void readCard(String amount, CardDataCallback callback) {
        callbackInvoked = false;
        pinRetryCount = 0;

        new Thread(() -> {
            try {
                callback.onEvent("INSERT_CARD");

                Log.d(TAG, "Cancelling any previous search...");
                cardReaderManager.cancelSearchCard();
                Log.d(TAG, "Closing card...");
                cardReaderManager.closeCard();

                Log.d(TAG, "Waiting 2 seconds...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }

                Log.d(TAG, "Starting card search with 0 timeout (infinite)...");
                cardReaderManager.searchCard(
                        CardReaderTypeEnum.MAG_IC_RF_CARD,
                        0,
                        new OnSearchCardListener() {
                            @Override
                            public void onCardInfo(CardInfoEntity cardInfo) {
                                Log.d(TAG, "Card detected: " + cardInfo.getCardExistslot());
                                if (callbackInvoked)
                                    return;
                                if (cardInfo.getCardExistslot() == CardReaderTypeEnum.IC_CARD) {
                                    callback.onEvent("PROCESSING");
                                    processIcCard(amount, callback);
                                } else {
                                    callbackInvoked = true;
                                    callback.onError("Only IC cards supported");
                                }
                            }

                            @Override
                            public void onError(int code) {
                                Log.d(TAG, "Card search error: " + code);
                                if (callbackInvoked)
                                    return;
                                callbackInvoked = true;
                                callback.onError("Card read error: " + code);
                            }

                            @Override
                            public void onNoCard(CardReaderTypeEnum type, boolean b) {
                                Log.d(TAG, "No card detected, type: " + type + ", b: " + b);
                            }
                        });
                Log.d(TAG, "Search initiated");
            } catch (Exception e) {
                Log.e(TAG, "Card read exception: " + e.getMessage());
                if (!callbackInvoked) {
                    callbackInvoked = true;
                    callback.onError("Card reading failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void processIcCard(String amount, CardDataCallback callback) {
        try {
            Log.d(TAG, "Resetting IC card...");
            int ret = icCard.icCardReset(CardSlotNoEnum.SDK_ICC_USERCARD);
            Log.d(TAG, "IC card reset result: " + ret);
            if (ret != 0) {
                if (!callbackInvoked) {
                    callbackInvoked = true;
                    callback.onError("Card reset failed (" + ret + "). Try removing and reinserting the card.");
                }
                return;
            }

            EmvTransParam transParam = new EmvTransParam();
            transParam.setTransKernalType(EmvData.KERNAL_EMV_PBOC);
            transParam.setAmountAuth(String.format("%012d", Long.parseLong(amount)));
            transParam.setAmountOther("000000000000");
            emvHandler.transParamInit(transParam);

            EmvTermParam termParam = new EmvTermParam();
            termParam.emvParamFilePath = context.getFilesDir().getPath() + "/emv/";
            emvHandler.kernelInit(termParam);

            emvHandler.setTlvData(0x5F2A, StringUtils.convertHexToBytes("0566"));
            emvHandler.setTlvData(0x9F1A, StringUtils.convertHexToBytes("0566"));

            String[] aids = { "A0000000031010", "A0000000041010", "A0000000032010", "A0000003330101",
                    "A000000333010101", "A000000333010102", "A000000333010103", "A0000000038010",
                    "A0000000032020", "A0000000033010", "A0000003710001" };
            for (String aid : aids) {
                EmvApp ea = new EmvApp();
                ea.setAid(aid);
                ea.setSelFlag((byte) 0);
                ea.setFloorLimit(1000);
                ea.setOnLinePINFlag((byte) 1);
                ea.setThreshold(0);
                ea.setTacDefault("0000000000");
                ea.setTacDenial("0000000000");
                ea.setTacOnline("0000000000");
                ea.settDOL("0F9F02065F2A029A039C0195059F3704");
                ea.setdDOL("039F3704");
                ea.setVersion("008C");
                emvHandler.addApp(ea);
            }

            EmvCapk mastercardCapk = new EmvCapk();
            mastercardCapk.setKeyID((byte) 0x05);
            mastercardCapk.setRID("A000000004");
            mastercardCapk.setModul(
                    "B8048ABC30C90D976336543E3FD7091C8FE4800DF820ED55E7E94813ED00555B573FECA3D84AF6131A651D66CFF4284FB13B635EDD0EE40176D8BF04B7FD1C7BACF9AC7327DFAA8AA72D10DB3B8E70B2DDD811CB4196525EA386ACC33C0D9D4575916469C4E4F53E8E1C912CC618CB22DDE7C3568E90022E6BBA770202E4522A2DD623D180E215BD1D1507FE3DC90CA310D27B3EFCCD8F83DE3052CAD1E48938C68D095AAC91B5F37E28BB49EC7ED597");
            mastercardCapk.setCheckSum("EBFA0D5D06D8CE702DA3EAE890701D45E274C845");
            mastercardCapk.setExpDate("20211231");
            emvHandler.addCapk(mastercardCapk);

            EmvCapk visaCapk = new EmvCapk();
            visaCapk.setKeyID((byte) 0x08);
            visaCapk.setRID("A000000003");
            visaCapk.setModul(
                    "D9FD6ED75D51D0E30664BD157023EAA1FFA871E4DA65672B863D255E81E137A51DE4F72BCC9E44ACE12127F87E263D3AF9DD9CF35CA4A7B01E907000BA85D24954C2FCA3074825DDD4C0C8F186CB020F683E02F2DEAD3969133F06F7845166ACEB57CA0FC2603445469811D293BFEFBAFAB57631B3DD91E796BF850A25012F1AE38F05AA5C4D6D03B1DC2E568612785938BBC9B3CD3A910C1DA55A5A9218ACE0F7A21287752682F15832A678D6E1ED0B");
            visaCapk.setCheckSum("20D213126955DE205ADC2FD2822BD22DE21CF9A8");
            visaCapk.setExpDate("20241231");
            emvHandler.addCapk(visaCapk);

            EmvCapk verveCapk = new EmvCapk();
            verveCapk.setKeyID((byte) 0x14);
            verveCapk.setRID("A0000003710001");
            verveCapk.setModul(
                    "A3767ABD1B6AA69D7F3FBF28C092DE9ED1E658BA5F0909AF7A1CCD907373B7210FDEB16287BA8E78E1529F443976FD27F991EC67D95E5F4E96B127CAB2396A94D6E45CDA44CA4C4867570D6B07542F8D4BF9FF97975DB9891515E66F525D2B3CBEB6D662BFB6C3F338E93B02142BFC44173A3764C56AADD202075B26DC2F9F7D7AE74BD7D00FD05EE430032663D27A57");
            verveCapk.setCheckSum("F6C206E2C177EC1DF6D6289EF40ECAD0DB88BF5F");
            verveCapk.setExpDate("20251231");
            emvHandler.addCapk(verveCapk);

            Log.d(TAG, "Loaded Mastercard, Visa, and Verve CAPKs");

            byte[] isEcTrans = new byte[1];
            byte[] balance = new byte[6];
            byte[] transResult = new byte[1];

            int emvRet = emvHandler.emvTrans(
                    transParam,
                    new OnEmvListener() {
                        @Override
                        public int onSelApp(String[] appLabels) {
                            return 0;
                        }

                        @Override
                        public int onConfirmCardNo(String cardNo) {
                            String[] track2 = new String[1];
                            String[] pan = new String[1];
                            emvHandler.getTrack2AndPAN(track2, pan);

                            cardPan = pan[0];
                            // Ensure track2 uses 'D' separator instead of '='
                            track2Data = track2[0].replace('=', 'D');

                            int index = track2[0].indexOf("D");
                            if (index == -1)
                                index = track2[0].indexOf("=");
                            if (index != -1 && track2[0].length() >= index + 5) {
                                expiryDate = track2[0].substring(index + 1, index + 5);
                            }

                            return 0;
                        }

                        @Override
                        public int onInputPIN(byte pinType) {
                            Log.d(TAG, "PIN input attempt: " + (pinRetryCount + 1) + " of " + MAX_PIN_RETRIES);

                            if (pinRetryCount >= MAX_PIN_RETRIES) {
                                Log.d(TAG, "Max PIN retries exceeded");
                                return EmvResult.EMV_USER_CANCEL;
                            }

                            pinRetryCount++;
                            return inputCustomPin();
                        }

                        @Override
                        public int onCertVerify(int certType, String certNo) {
                            return 0;
                        }

                        @Override
                        public byte[] onExchangeApdu(byte[] send) {
                            return icCard.icExchangeAPDU(CardSlotNoEnum.SDK_ICC_USERCARD, send);
                        }

                        @Override
                        public int onlineProc() {
                            Log.d(TAG, "onlineProc called - processing ICC data");
                            int[] tags = { 0x9F26, 0x9F27, 0x9F10, 0x9F37, 0x9F36, 0x95,
                                    0x9A, 0x9C, 0x9F02, 0x5F2A, 0x82, 0x9F1A, 0x84, 0x5F24,
                                    0x9F03, 0x9F33, 0x9F34, 0x9F35, 0x9F1E, 0x9F09, 0x9F41, 0x9F63, 0x5F34 };
                            byte[] field55 = emvHandler.packageTlvList(tags);
                            iccData = StringUtils.convertBytesToHex(field55);
                            Log.d(TAG, "ICC data packaged: " + (iccData != null ? iccData.length() : 0) + " chars");

                            int[] seqTags = { 0x5F34 };
                            byte[] seqData = emvHandler.packageTlvList(seqTags);
                            if (seqData != null && seqData.length > 0) {
                                String hexSeq = StringUtils.convertBytesToHex(seqData);
                                cardSequenceNumber = String.valueOf(hexStringToInt(hexSeq));
                            }

                            posDataCode = "510101511344101";
                            Log.d(TAG, "onlineProc completed successfully");
                            return 0;
                        }
                    },
                    isEcTrans,
                    balance,
                    transResult);

            cardReaderManager.closeCard();

            Log.d(TAG, "EMV transaction completed with result: " + emvRet);
            if (emvRet == 0) {
                if (callbackInvoked) {
                    Log.d(TAG, "Callback already invoked, skipping");
                    return;
                }
                callbackInvoked = true;

                // Get clear PIN from custom PIN pad
                Log.d(TAG, "Clear PIN: " + clearPin);

                Log.d(TAG, "Preparing card data for callback");
                Map<String, String> data = new HashMap<>();
                data.put("pan", cardPan);
                data.put("track2", track2Data);
                data.put("expiry", expiryDate);
                data.put("iccData", iccData);
                data.put("pin", clearPin);
                data.put("cardSequenceNumber", cardSequenceNumber.isEmpty() ? "001" : cardSequenceNumber);
                data.put("posDataCode", posDataCode);
                Log.d(TAG, "Invoking callback with card data");
                callback.onCardData(data);
                Log.d(TAG, "Callback invoked successfully");
            } else {
                if (!callbackInvoked) {
                    callbackInvoked = true;
                    callback.onError("EMV transaction failed");
                }
            }
        } catch (Exception e) {
            if (!callbackInvoked) {
                callbackInvoked = true;
                callback.onError("Processing error: " + e.getMessage());
            }
        }
    }

    private int inputCustomPin() {
        pinLatch = new CountDownLatch(1);

        try {
            String[] track2 = new String[1];
            String[] pan = new String[1];
            emvHandler.getTrack2AndPAN(track2, pan);

            // Launch custom PIN pad activity
            android.app.Activity activity = (android.app.Activity) context;
            activity.runOnUiThread(() -> {
                android.content.Intent intent = CustomPinPadActivity.createIntent(activity, pan[0], 4, 12, pinRetryCount - 1);
                activity.startActivityForResult(intent, 1001);
            });

            pinLatch.await();
            Log.d(TAG, "PIN latch released, returning result: " + pinResult);
        } catch (Exception e) {
            Log.e(TAG, "PIN input error: " + e.getMessage());
        }

        return pinResult;
    }

    public void onPinPadResult(int resultCode, String pin) {
        if (resultCode == android.app.Activity.RESULT_OK && pin != null) {
            clearPin = pin;
            pinResult = EmvResult.EMV_OK;
        } else {
            pinResult = EmvResult.EMV_USER_CANCEL;
        }
        pinLatch.countDown();
    }

    public void cancelRead() {
        try {
            cardReaderManager.cancelSearchCard();
            cardReaderManager.closeCard();
        } catch (Exception e) {
            Log.e(TAG, "Cancel error: " + e.getMessage());
        }
    }

    public String getSerialNumber() {
        String[] sn = new String[1];
        int ret = driverManager.getBaseSysDevice().getSN(sn);
        return (ret == SdkResult.SDK_OK) ? sn[0] : "";
    }

    private int hexStringToInt(String hexString) {
        if (hexString.length() <= 1)
            return 1;
        return Integer.parseInt(hexString.substring(4, 8), 16);
    }

    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
