package com.billzone.biopay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Layout;

import com.zcs.sdk.DriverManager;
import com.zcs.sdk.Printer;
import com.zcs.sdk.SdkResult;
import com.zcs.sdk.Sys;
import com.zcs.sdk.print.PrnStrFormat;
import com.zcs.sdk.print.PrnTextFont;
import com.zcs.sdk.print.PrnTextStyle;

public class ReceiptPrinter {
    private Context context;
    private DriverManager driverManager;
    private Sys sys;
    private Printer printer;

    public interface PrintCallback {
        void onSuccess();
        void onError(String error);
    }

    public ReceiptPrinter(Context context) {
        this.context = context;
        this.driverManager = DriverManager.getInstance();
        this.sys = driverManager.getBaseSysDevice();
        this.printer = driverManager.getPrinter();
    }

    public void printReceipt(String amount, String date, boolean isSuccessful, String terminalNo,
                             String reference, boolean isBiometric, boolean isCustomerReceipt,
                             String fullName, String bankName, PrintCallback callback) {
        new Thread(() -> {
            initSdk();

            try {
                if (printer.getPrinterStatus() == SdkResult.SDK_PRN_STATUS_PAPEROUT) {
                    callback.onError("Out of paper!");
                    return;
                }

                Bitmap logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon);
                if (logo != null) {
                    printer.setPrintAppendBitmap(logo, Layout.Alignment.ALIGN_CENTER);
                }

                PrnStrFormat centerBig = new PrnStrFormat();
                centerBig.setAli(Layout.Alignment.ALIGN_CENTER);
                centerBig.setTextSize(30);
                centerBig.setStyle(PrnTextStyle.BOLD);
                centerBig.setFont(PrnTextFont.DEFAULT);

                printer.setPrintAppendString("-----------------------------", centerBig);
                printer.setPrintAppendString(
                        isCustomerReceipt ? "*** Customer Copy ***" : "*** Agent/Merchant Copy ***", centerBig);
                printer.setPrintAppendString("-----------------------------", centerBig);

                PrnStrFormat leftNormal = new PrnStrFormat();
                leftNormal.setAli(Layout.Alignment.ALIGN_NORMAL);
                leftNormal.setTextSize(22);
                leftNormal.setStyle(PrnTextStyle.NORMAL);

                printKeyValue("To", safe(valueOrEmpty(fullName)), leftNormal);
                printKeyValue("Bank", safe(valueOrEmpty(bankName)), leftNormal);
                printKeyValue("Payment Method", isBiometric ? "Biometric" : "Card", leftNormal);
                printKeyValue("Terminal No", safe(terminalNo), leftNormal);
                printKeyValue("Reference", safe(reference), leftNormal);
                printKeyValue("Date", safe(date), leftNormal);

                printer.setPrintAppendString(" ", leftNormal);

                PrnStrFormat centerAmount = new PrnStrFormat();
                centerAmount.setAli(Layout.Alignment.ALIGN_CENTER);
                centerAmount.setTextSize(28);
                centerAmount.setStyle(PrnTextStyle.NORMAL);

                printer.setPrintAppendString("Payment of N" + safe(amount) +
                        (isSuccessful ? " Successful" : " Failed"), centerAmount);

                PrnStrFormat centerStatus = new PrnStrFormat();
                centerStatus.setAli(Layout.Alignment.ALIGN_CENTER);
                centerStatus.setTextSize(40);
                centerStatus.setStyle(PrnTextStyle.BOLD);
                printer.setPrintAppendString(isSuccessful ? "SUCCESSFUL" : "FAILED", centerStatus);

                PrnStrFormat sep = new PrnStrFormat();
                sep.setAli(Layout.Alignment.ALIGN_CENTER);
                sep.setTextSize(22);
                sep.setStyle(PrnTextStyle.NORMAL);
                printer.setPrintAppendString("-----------------------------\n\n", sep);

                leftNormal.setTextSize(22);
                printer.setPrintAppendString("Amount: N" + safe(amount), leftNormal);

                leftNormal.setTextSize(20);
                printer.setPrintAppendString("\nPlease retain your receipt", leftNormal);
                printer.setPrintAppendString("Thank You", leftNormal);
                printer.setPrintAppendString("\n\n", leftNormal);

                PrnStrFormat rightSmall = new PrnStrFormat();
                rightSmall.setAli(Layout.Alignment.ALIGN_OPPOSITE);
                rightSmall.setTextSize(18);
                rightSmall.setStyle(PrnTextStyle.NORMAL);
                printer.setPrintAppendString("Powered by BioPay", rightSmall);

                int printStatus = printer.setPrintStart();
                if (printStatus == SdkResult.SDK_PRN_STATUS_PAPEROUT) {
                    callback.onError("Out of paper!");
                } else {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                callback.onError("Print error: " + e.getMessage());
            }
        }).start();
    }

    private void initSdk() {
        int status = sys.sdkInit();
        if (status != SdkResult.SDK_OK) {
            sys.sysPowerOn();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            sys.sdkInit();
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String valueOrEmpty(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private void printKeyValue(String key, String value, PrnStrFormat baseFormat) {
        PrnStrFormat left = new PrnStrFormat();
        left.setAli(Layout.Alignment.ALIGN_NORMAL);
        left.setTextSize(baseFormat.getTextSize());
        left.setStyle(baseFormat.getStyle());
        printer.setPrintAppendString(key + ": ", left);

        PrnStrFormat right = new PrnStrFormat();
        right.setAli(Layout.Alignment.ALIGN_OPPOSITE);
        right.setTextSize(baseFormat.getTextSize());
        right.setStyle(baseFormat.getStyle());
        printer.setPrintAppendString(value + "\n", right);
    }
}
