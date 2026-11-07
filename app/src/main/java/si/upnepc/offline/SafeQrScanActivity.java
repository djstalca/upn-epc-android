package si.upnepc.offline;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CameraPreview;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Collections;
import java.util.List;

/** Self-contained camera screen. Camera permission and failures are handled here. */
public final class SafeQrScanActivity extends Activity {
    public static final String EXTRA_RESULT = "si.upnepc.offline.QR_TEXT";
    public static final String EXTRA_ERROR = "si.upnepc.offline.QR_ERROR";
    private static final int CAMERA_PERMISSION = 804;

    private DecoratedBarcodeView scanner;
    private boolean permissionRequested;
    private boolean scanning;
    private boolean finishing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        try {
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            scanner = new DecoratedBarcodeView(this);
            scanner.setStatusText("Usmeri kamero v UPN QR na položnici");
            scanner.setDecoderFactory(new DefaultDecoderFactory(
                    Collections.singletonList(BarcodeFormat.QR_CODE)));
            root.addView(scanner, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            TextView back = new TextView(this);
            back.setText("‹  Nazaj");
            back.setTextSize(18);
            back.setTextColor(Color.WHITE);
            back.setGravity(Gravity.CENTER_VERTICAL);
            back.setPadding(dp(18), dp(12), dp(18), dp(12));
            back.setOnClickListener(v -> finish());
            FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            top.topMargin = dp(22);
            root.addView(back, top);

            scanner.getBarcodeView().addStateListener(new CameraPreview.StateListener() {
                @Override public void previewSized() { }
                @Override public void previewStarted() { }
                @Override public void previewStopped() { }
                @Override public void cameraClosed() { }
                @Override public void cameraError(Exception error) {
                    fail("Kamera ni na voljo. Preveri dovoljenje za kamero ali uporabi uvoz slike.");
                }
            });
            setContentView(root);
        } catch (RuntimeException ex) {
            fail("Zaslona kamere ni mogoče odpreti. Poskusi uvoziti sliko.");
        }
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scanner == null || finishing) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            resumeScanning();
        } else if (!permissionRequested) {
            permissionRequested = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void resumeScanning() {
        if (scanner == null || scanning || finishing) return;
        try {
            scanning = true;
            scanner.decodeSingle(new BarcodeCallback() {
                @Override public void barcodeResult(BarcodeResult result) {
                    if (result != null && result.getText() != null && !finishing) {
                        finishing = true;
                        Intent data = new Intent().putExtra(EXTRA_RESULT, result.getText());
                        setResult(RESULT_OK, data);
                        finish();
                    }
                }
                @Override public void possibleResultPoints(List<ResultPoint> points) { }
            });
            scanner.resume();
        } catch (RuntimeException ex) {
            scanning = false;
            fail("Kamere ni mogoče zagnati. Preveri dovoljenje ali uporabi uvoz slike.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != CAMERA_PERMISSION || finishing) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            resumeScanning();
        } else {
            fail("Aplikacija potrebuje dovoljenje za kamero. Dovoli ga v nastavitvah ali uporabi uvoz slike položnice.");
        }
    }

    @Override
    protected void onPause() {
        if (scanner != null && scanning) {
            scanning = false;
            scanner.pause();
        }
        super.onPause();
    }

    private void fail(String message) {
        if (finishing) return;
        finishing = true;
        setResult(RESULT_CANCELED, new Intent().putExtra(EXTRA_ERROR, message));
        finish();
    }
}
