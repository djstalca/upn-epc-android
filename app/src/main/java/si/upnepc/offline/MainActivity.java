package si.upnepc.offline;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int OPEN_IMAGE = 701;
    private static final int SAVE_IMAGE = 702;
    private static final int INK = Color.rgb(20, 32, 50);
    private static final int BLUE = Color.rgb(23, 88, 187);
    private static final int LIGHT = Color.rgb(243, 246, 250);
    private static final int RED = Color.rgb(154, 47, 39);
    private static final int GREEN = Color.rgb(14, 112, 72);

    private LinearLayout content;
    private PaymentConverter.Payment current;
    private Bitmap qrBitmap;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        renderHome();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp((int) radius));
        return gd;
    }

    private void setup(String title, String sub) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(LIGHT);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(30), dp(20), dp(40));
        scroll.addView(content);
        setContentView(scroll);
        addText(title, 27, INK, true, 0);
        addText(sub, 15, Color.DKGRAY, false, 12);
    }

    private TextView addText(String text, int size, int color, boolean bold, int topMargin) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(2), 1f);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMargin);
        content.addView(tv, lp);
        return tv;
    }

    private void button(String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(primary ? Color.WHITE : BLUE);
        button.setBackground(bg(primary ? BLUE : Color.WHITE, 14));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        lp.topMargin = dp(12);
        content.addView(button, lp);
        button.setOnClickListener(listener);
    }

    private void detail(String label, String value) {
        addText(label.toUpperCase(), 12, Color.DKGRAY, true, 15);
        addText(value.isEmpty() ? "—" : value, 17, INK, false, 3);
    }

    private void renderHome() {
        setup("UPN → EPC QR", "Poskeniraj slovensko položnico. Ustvari evropsko QR kodo za bančno nakazilo.");
        button("Skeniraj UPN QR s kamero", true, v -> {
            IntentIntegrator scanner = new IntentIntegrator(this);
            scanner.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            scanner.setPrompt("Usmeri kamero v QR kodo na položnici");
            scanner.setBeepEnabled(false);
            scanner.setOrientationLocked(true);
            scanner.initiateScan();
        });
        button("Uvozi sliko položnice", false, v -> {
            Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            open.addCategory(Intent.CATEGORY_OPENABLE);
            open.setType("image/*");
            startActivityForResult(open, OPEN_IMAGE);
        });
        addText("Brez računa, reklam in povezave s strežnikom. Aplikacija ne izvrši plačila.",
                13, Color.GRAY, false, 24);
        addText("Pozor: SI sklici se pri pretvorbi prenesejo v besedilo nakazila. Preveri jih v Revolutu, predvsem pri davkih.",
                13, RED, false, 16);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scan != null) {
            if (scan.getContents() != null) convertAndShow(scan.getContents());
            return;
        }
        if (requestCode == OPEN_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), data.getData());
                Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    int divisor = 1;
                    while (Math.max(width / divisor, height / divisor) > 2400) divisor *= 2;
                    decoder.setTargetSampleSize(divisor);
                });
                int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0,
                        bitmap.getWidth(), bitmap.getHeight());
                RGBLuminanceSource luminance = new RGBLuminanceSource(
                        bitmap.getWidth(), bitmap.getHeight(), pixels);
                BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(luminance));
                Result decoded = new MultiFormatReader().decode(binary);
                convertAndShow(decoded.getText());
                bitmap.recycle();
            } catch (Exception ex) {
                error("QR kode na sliki ni mogoče prebrati: " + ex.getMessage());
            }
        } else if (requestCode == SAVE_IMAGE && resultCode == RESULT_OK && data != null && qrBitmap != null) {
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream == null || !qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw new IllegalStateException("Slike ni mogoče zapisati.");
                }
                Toast.makeText(this, "EPC QR shranjen.", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                error("Shranjevanje ni uspelo: " + ex.getMessage());
            }
        }
    }

    private void error(String message) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Napaka pri obdelavi")
                .setMessage(message)
                .setPositiveButton("V redu", null)
                .show();
    }

    private void convertAndShow(String raw) {
        try {
            current = PaymentConverter.convert(raw);
            qrBitmap = generateQr(current.epcText);
            renderResult();
        } catch (Exception ex) {
            current = null;
            qrBitmap = null;
            error(ex.getMessage() == null ? "Pretvorba ni uspela." : ex.getMessage());
        }
    }

    private Bitmap generateQr(String text) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 4);
        QRCode metadata = Encoder.encode(text, ErrorCorrectionLevel.M, hints);
        if (metadata.getVersion().getVersionNumber() > 13) {
            throw new IllegalArgumentException("EPC QR presega dovoljeno velikost (različica 13). ");
        }
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 900, 900, hints);
        int w = matrix.getWidth();
        int h = matrix.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
    }

    private void renderResult() {
        setup("Evropska EPC QR koda", "Pretvorba je uspela. Podatke primerjaj z originalno položnico.");
        ImageView qrView = new ImageView(this);
        qrView.setImageBitmap(qrBitmap);
        qrView.setBackground(bg(Color.WHITE, 16));
        qrView.setPadding(dp(10), dp(10), dp(10), dp(10));
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(340));
        imgParams.topMargin = dp(18);
        content.addView(qrView, imgParams);

        addText("EPC / SEPA QR", 14, GREEN, true, 12).setGravity(Gravity.CENTER);
        detail("Prejemnik", current.recipient);
        detail("IBAN", current.iban);
        detail("Znesek", current.displayAmount());
        detail("Sklic", current.reference);
        detail("Namen", current.purpose);
        detail("Rok plačila", current.dueDate);
        if (!current.warning.isEmpty()) {
            TextView note = addText("OPOZORILO\n" + current.warning,
                    14, RED, true, 20);
            note.setPadding(dp(14), dp(14), dp(14), dp(14));
            note.setBackground(bg(Color.rgb(255, 240, 237), 12));
        }
        button("Shrani EPC QR kot PNG", true, v -> {
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.setType("image/png");
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.putExtra(Intent.EXTRA_TITLE, "epc-qr.png");
            startActivityForResult(save, SAVE_IMAGE);
        });
        button("Kopiraj IBAN", false, v -> copy("IBAN", current.iban));
        button("Kopiraj sklic", false, v -> copy("Sklic", current.reference));
        button("Kopiraj EPC podatke", false, v -> copy("EPC QR", current.epcText));
        button("Skeniraj novo položnico", false, v -> {
            current = null;
            qrBitmap = null;
            renderHome();
        });
        addText("Plačilo ni izvršeno. Revolut mora podpirati branje EPC QR iz slike ali drugega zaslona.",
                13, Color.GRAY, false, 18);
    }

    private void copy(String title, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(title, value));
        Toast.makeText(this, title + " kopiran.", Toast.LENGTH_SHORT).show();
    }
}
