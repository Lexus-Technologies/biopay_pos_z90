package com.billzone.biopay;

import android.content.Context;

import com.zcs.sdk.DriverManager;
import com.zcs.sdk.SdkData;
import com.zcs.sdk.SdkResult;
import com.zcs.sdk.Sys;
import com.zcs.sdk.card.CardInfoEntity;
import com.zcs.sdk.card.CardReaderManager;
import com.zcs.sdk.card.CardReaderTypeEnum;
import com.zcs.sdk.card.RfCard;
import com.zcs.sdk.listener.OnSearchCardListener;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NfcReader {
    private static final int READ_TIMEOUT = 60 * 1000;
    private Context context;
    private DriverManager driverManager;
    private Sys sys;
    private CardReaderManager cardReadManager;
    private RfCard rfCard;

    public interface NfcCallback {
        void onSuccess(String data);
        void onError(String error);
    }

    public NfcReader(Context context) {
        this.context = context;
        this.driverManager = DriverManager.getInstance();
        this.sys = driverManager.getBaseSysDevice();
        this.cardReadManager = driverManager.getCardReadManager();
        this.rfCard = cardReadManager.getRFCard();
    }

    public void startScan(NfcCallback callback) {
        initSdk(callback);
        searchRfCard(callback);
    }

    private void initSdk(NfcCallback callback) {
        int status = sys.sdkInit();
        if (status != SdkResult.SDK_OK) {
            sys.sysPowerOn();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            status = sys.sdkInit();
            if (status != SdkResult.SDK_OK) {
                callback.onError("SDK init failed");
            }
        }
    }

    private void searchRfCard(NfcCallback callback) {
        cardReadManager.searchCard(CardReaderTypeEnum.RF_CARD, READ_TIMEOUT, new OnSearchCardListener() {
            @Override
            public void onCardInfo(CardInfoEntity cardInfoEntity) {
                if (searchCard(callback) == SdkResult.SDK_OK) {
                    readRfCardPage(callback);
                }
            }

            @Override
            public void onError(int i) {
                callback.onError("Card error: " + i);
            }

            @Override
            public void onNoCard(CardReaderTypeEnum t, boolean b) {}
        });
    }

    private int searchCard(NfcCallback callback) {
        byte[] outType = new byte[1];
        byte[] uid = new byte[300];
        int ret = rfCard.rfSearchCard(SdkData.RF_TYPE_A, outType, uid);
        if (ret != SdkResult.SDK_OK) {
            callback.onError("Can't read card");
        }
        return ret;
    }

    private void readRfCardPage(NfcCallback callback) {
        try {
            StringBuilder res = new StringBuilder();

            byte[] outData = new byte[68];
            if (rfCard.ntagFastRead((byte) 0x00, (byte) 0x10, outData) == SdkResult.SDK_OK) {
                res.append(convertBytesToString(outData));
            } else {
                callback.onError("Read failed");
                return;
            }

            outData = new byte[112];
            if (rfCard.ntagFastRead((byte) 0x11, (byte) 0x2c, outData) == SdkResult.SDK_OK) {
                res.append(convertBytesToString(outData));
            } else {
                callback.onError("Read failed");
                return;
            }

            rfCard.rfCardPowerDown();

            String[] split = res.toString().split("en");
            if (split.length < 2) {
                callback.onError("Parse failed");
                return;
            }

            String nfcContent = extractBase64Substring(split[1]);
            if (nfcContent == null) {
                callback.onError("No data");
                return;
            }

            String actualValue = FlutterEncryptionUtils.decrypt(nfcContent);
            callback.onSuccess(actualValue);
        } catch (Exception e) {
            callback.onError("Read error");
        }
    }

    private String convertBytesToString(byte[] bytes) {
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private String extractBase64Substring(String input) {
        Pattern pattern = Pattern.compile("(?<=^|[^A-Za-z0-9+/])[A-Za-z0-9+/]{2,}(?:={1,2})?(?=[^A-Za-z0-9+/]|$)");
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group() : null;
    }
}
