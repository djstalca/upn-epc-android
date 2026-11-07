package si.upnepc.offline;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline converter: official 20-field Slovenian UPN QR into EPC069-12 v3.1, version 002.
 * Does not initiate a payment. SI references cannot be represented as EPC structured creditor references.
 */
public final class PaymentConverter {
    private static final Charset LATIN2 = Charset.forName("ISO-8859-2");

    private PaymentConverter() { }

    public static final class Payment {
        public final String recipient;
        public final String iban;
        public final String reference;
        public final String purpose;
        public final String purposeCode;
        public final String dueDate;
        public final BigDecimal amount;
        public final String epcText;
        public final String warning;

        private Payment(String recipient, String iban, String reference, String purpose,
                        String purposeCode, String dueDate, BigDecimal amount,
                        String epcText, String warning) {
            this.recipient = recipient;
            this.iban = iban;
            this.reference = reference;
            this.purpose = purpose;
            this.purposeCode = purposeCode;
            this.dueDate = dueDate;
            this.amount = amount;
            this.epcText = epcText;
            this.warning = warning;
        }

        public String displayAmount() {
            return amount.setScale(2).toPlainString().replace('.', ',') + " €";
        }
    }

    public static Payment convert(String scanned) {
        if (scanned == null) throw new IllegalArgumentException("QR koda nima vsebine.");
        String raw = scanned.replace("\r\n", "\n").replace('\r', '\n');
        if (raw.startsWith("\uFEFF")) raw = raw.substring(1);
        String[] f = raw.split("\n", -1);
        if (f.length < 20 || !"UPNQR".equals(f[0])) {
            throw new IllegalArgumentException("To ni veljavna slovenska UPN QR koda.");
        }
        int calculatedLength = 0;
        for (int i = 0; i < 19; i++) {
            // UPN uses ISO-8859-2 (one byte per supported character) and LF separators.
            for (char c : f[i].toCharArray()) {
                if (!LATIN2.newEncoder().canEncode(c)) {
                    throw new IllegalArgumentException("Neveljaven znak v UPN QR kodi.");
                }
            }
            calculatedLength += f[i].getBytes(LATIN2).length + 1;
        }
        if (!f[19].trim().matches("\\d{1,3}") ||
                Integer.parseInt(f[19].trim()) != calculatedLength) {
            throw new IllegalArgumentException("Kontrolna vsota UPN QR ni pravilna. Plačilo je ustavljeno.");
        }
        if ("X".equalsIgnoreCase(f[2].trim()) || "X".equalsIgnoreCase(f[3].trim())) {
            throw new IllegalArgumentException("Polog ali dvig gotovine ni SEPA nakazilo.");
        }

        String rawCents = f[8].trim();
        if (!rawCents.matches("[0-9]{1,11}")) {
            throw new IllegalArgumentException("Znesek UPN QR ni veljaven.");
        }
        BigDecimal amount = new BigDecimal(new BigInteger(rawCents), 2);
        if (amount.compareTo(new BigDecimal("999999999.99")) > 0) {
            throw new IllegalArgumentException("Znesek presega največjo vrednost EPC QR.");
        }
        String recipient = f[16].trim();
        String iban = f[14].replace(" ", "").toUpperCase(Locale.ROOT).trim();
        String reference = f[15].replace(" ", "").trim().toUpperCase(Locale.ROOT);
        String purpose = f[12].trim();
        String purposeCode = f[11].trim().toUpperCase(Locale.ROOT);
        String dueDate = f[13].trim();
        if (recipient.isEmpty() || recipient.length() > 70) {
            throw new IllegalArgumentException("Manjka ime prejemnika ali je predolgo.");
        }
        if (!isValidIban(iban)) {
            throw new IllegalArgumentException("IBAN prejemnika nima veljavnih kontrolnih številk.");
        }
        if (!purposeCode.isEmpty() && !purposeCode.matches("[A-Z]{4}")) {
            throw new IllegalArgumentException("Koda namena plačila ni veljavna.");
        }

        String structured = "";
        String unstructured = "";
        List<String> warnings = new ArrayList<>();
        if (!reference.isEmpty() && reference.startsWith("RF") && isValidRf(reference)) {
            structured = reference;
            // An RF reference occupies the single EPC remittance field.
            if (!purpose.isEmpty()) warnings.add("Pri referenci RF je namen prikazan v aplikaciji, ne pa v EPC opisu nakazila.");
        } else if (!reference.isEmpty()) {
            unstructured = reference + (purpose.isEmpty() ? "" : " / " + purpose);
            if (reference.startsWith("SI")) {
                warnings.add("Slovenski sklic " + reference + " je v EPC zapisan v besedilu nakazila. Revolut ga lahko prikaže drugače. Preveri sklic pred plačilom; za FURS uporabite banko z neposrednim UPN.");
            } else {
                warnings.add("Referenca ni veljavna RF referenca; prenesena je v besedilo nakazila.");
            }
        } else {
            unstructured = purpose;
        }
        if (unstructured.length() > 140) {
            throw new IllegalArgumentException("Sklic in namen skupaj presegata 140 znakov EPC.");
        }
        if (amount.signum() == 0) {
            warnings.add("UPN nima določenega zneska. V bančni aplikaciji ga vpiši ročno.");
        }
        if (!dueDate.isEmpty()) {
            warnings.add("Rok plačila " + dueDate + " ni del EPC QR. Pri plačilu ga upoštevaj ročno.");
        }

        ArrayList<String> lines = new ArrayList<>();
        lines.add("BCD");
        lines.add("002");
        lines.add("1");              // UTF-8
        lines.add("SCT");
        lines.add("");               // BIC optional for EEA SEPA participants
        lines.add(recipient);
        lines.add(iban);
        lines.add(amount.signum() == 0 ? "" : "EUR" + amount.setScale(2).toPlainString());
        lines.add(purposeCode);
        lines.add(structured);
        lines.add(unstructured);
        // EPC beneficiary-to-originator information omitted (it is NOT the payment reference).
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        String epc = String.join("\n", lines);
        if (epc.getBytes(StandardCharsets.UTF_8).length > 331) {
            throw new IllegalArgumentException("EPC QR presega 331 bajtov.");
        }
        return new Payment(recipient, iban, reference, purpose, purposeCode, dueDate,
                amount, epc, String.join("\n\n", warnings));
    }

    private static boolean isValidIban(String iban) {
        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}") || iban.length() > 34) return false;
        return checkMod97(iban.substring(4) + iban.substring(0, 4));
    }

    private static boolean isValidRf(String reference) {
        if (!reference.matches("RF[0-9]{2}[A-Z0-9]{1,21}") || reference.length() > 25) return false;
        return checkMod97(reference.substring(4) + reference.substring(0, 4));
    }

    private static boolean checkMod97(String input) {
        int remainder = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                remainder = (remainder * 10 + (c - '0')) % 97;
            } else if (c >= 'A' && c <= 'Z') {
                int v = c - 'A' + 10;
                remainder = (remainder * 100 + v) % 97;
            } else return false;
        }
        return remainder == 1;
    }
}
