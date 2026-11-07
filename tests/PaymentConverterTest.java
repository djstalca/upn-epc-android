import si.upnepc.offline.PaymentConverter;

public class PaymentConverterTest {
    static String sample(String amount, String reference, String purpose) {
        String[] fields = {
            "UPNQR", "", "", "", "", "Janez Novak", "Dunajska 1", "1000 Ljubljana",
            amount, "", "", "RENT", purpose, "15.11.2016", "SI56051008010486080",
            reference, "Novo podjetje d.o.o.", "Lepa cesta 15", "3698 Loški Potok"
        };
        int sum = 0;
        StringBuilder s = new StringBuilder();
        for (String f: fields) { s.append(f).append('\n'); sum += f.getBytes(java.nio.charset.Charset.forName("ISO-8859-2")).length + 1; }
        return s.append(String.format("%03d", sum)).append('\n').toString();
    }

    static void check(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }

    static void rejected(String raw, String error) {
        try { PaymentConverter.convert(raw); throw new AssertionError("Should reject: " + error); }
        catch (IllegalArgumentException okay) { check(okay.getMessage().contains(error), okay.getMessage()); }
    }

    public static void main(String[] args) {
        PaymentConverter.Payment si = PaymentConverter.convert(
                sample("00000008105", "SI129990000359896", "Najemnina 11/2016"));
        check(si.displayAmount().equals("81,05 €"), "wrong amount");
        check(si.iban.equals("SI56051008010486080"), "wrong IBAN");
        check(si.epcText.startsWith("BCD\n002\n1\nSCT\n\nNovo podjetje d.o.o."), "EPC fields");
        check(si.epcText.contains("\nSI129990000359896 / Najemnina 11/2016"), "SI sklic lost");
        check(si.warning.contains("Slovenski sklic"), "missing SI warning");

        PaymentConverter.Payment rf = PaymentConverter.convert(
                sample("00000126874", "RF18539007547034", "Racun 2026-11"));
        check(rf.epcText.contains("\nRF18539007547034"), "RF sklic not structured");
        check(rf.epcText.contains("EUR1268.74"), "amount encoding error");

        PaymentConverter.Payment noAmount = PaymentConverter.convert(
                sample("00000000000", "SI00123", "Racun"));
        check(noAmount.epcText.contains("\nSI56051008010486080\n\nRENT"), "zero amount must be blank");
        check(noAmount.warning.contains("nima določenega zneska"), "amount warning missing");

        String correct = sample("00000008105", "SI00123", "Test");
        rejected(correct.replace("SI00123", "SI991234"), "Kontrolna vsota");
        rejected(correct.replace("SI56051008010486080", "SI56051008010486081"), "IBAN");
        rejected("not-a-qr", "UPN QR");
        PaymentConverter.Payment accents = PaymentConverter.convert(
                sample("00000000123", "SI0027-5", "Plačilo št. 5"));
        check(accents.epcText.contains("Plačilo št. 5"), "Slovenian character lost");
        rejected(sample("abc", "SI00", "Test"), "Znesek");
        String withdrawal = sample("00000001234", "SI00", "Test");
        rejected(withdrawal.replace("UPNQR\n\n\n", "UPNQR\n\nX\n"), "Kontrolna vsota");
        System.out.println("9 conversion/security checks PASSED");
    }
}
