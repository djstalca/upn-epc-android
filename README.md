# UPN → EPC QR (Android)

Aplikacija za Android, ki lokalno prebere slovenski **UPN QR** s kamero ali iz slike ter ustvari evropski **EPC / SEPA SCT QR**. Namenjena pripravi podatkov, **ne izvaja plačil**.

## Funkcije
- Skeniranje papirnate položnice s kamero (ZXing) ali uvoz fotografije/posnetka zaslona.
- Preverjanje UPN glave, dolžine polj (kontrolne vsote), IBAN-a, zneska in vrste plačila.
- Pretvorba po EPC069-12 v3.1, verzija 002, UTF-8.
- EPC QR slika, prikaz ključnih podatkov, shranjevanje PNG in kopiranje IBAN-a/sklica.
- Brez INTERNET dovoljenja, brez računa, brez shranjevanja položnic na strežniku.

## Kako ustvariš APK

1. Odpri mapo `UPN-EPC-Android` v **Android Studio** (novejša različica z JDK 17 in Android SDK 35).
2. Počakaj na **Gradle Sync** (odvisnosti se prvič prenesejo iz Google Maven in Maven Central; kasneje aplikacija za skeniranje ne potrebuje interneta).
3. Izberi **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. APK najdeš pod `app/build/outputs/apk/debug/app-debug.apk`.
5. Prenesi APK na telefon in ga namesti. Android mora dovoliti namestitev iz te izbrane aplikacije.

Projekt namenoma **nima že sestavljenega APK**. V okolju izdelave ni nameščenega Android SDK/Gradle in ni omrežnega dostopa za prenos orodij. Projekt vsebuje gradle skripte, izvorno kodo in samostojne preverjalne teste.

## Testiranje pretvorbe brez Android SDK

```sh
mkdir -p /tmp/upn-epc-tests
javac -d /tmp/upn-epc-tests app/src/main/java/si/upnepc/offline/PaymentConverter.java tests/PaymentConverterTest.java
java -cp /tmp/upn-epc-tests PaymentConverterTest
```

## Opozorila pri plačilih

- Slovenski sklici `SI00`, `SI12`, `SI99` **niso ISO 11649 RF sklici**. V EPC QR jih aplikacija lahko shrani le kot **nestrukturiran opis** (`SI... / namen`). Banka ne zagotavlja, da se iz tega ustvari pravilen domači UPN model/sklic. **Pred potrditvijo preveri dejanske podatke**. Za FURS in druge stroge sklice uporabi banko z neposrednim UPN, če ne moreš potrditi pravilnega prenosa.
- Veljavne reference `RF...` gredo v EPC strukturirano polje (po mod97 preverjanju).
- Rok plačila v EPC QR nima ekvivalentnega polja, zato ga aplikacija pokaže, ne prenese.
- Kadar UPN ne vsebuje zneska, ga EPC pusti praznega. Ne izmisli si zneska `0,01 €`.
- Delovanje uvoza EPC QR iz slike **je odvisno od funkcij in različice Revoluta**. Na enem telefonu shrani PNG in uporabi možnost uvoza iz galerije, če jo tvoja bančna aplikacija ponuja. Druga možnost je skeniranje QR z drugega zaslona.
- Prve bančne transakcije preizkusi z lastno testno položnico in primerjaj z originalnimi podatki. Pretvornik ne spreminja plačila in se ne povezuje z banko.

## Standardi

- [Združenje bank Slovenije: UPN QR tehnični standard](https://www.zbs-giz.si/wp-content/uploads/2021/10/EN_Tehnicni_standard_UPN_QR.pdf)
- [European Payments Council: EPC069-12 v3.1](https://www.europeanpaymentscouncil.eu/document-library/guidance-documents/quick-response-code-guidelines-enable-data-capture-initiation?documents_id=607)

## Sestava

- Java, Android Activity, ZXing Embedded za kamero in ZXing Core za dekodiranje slik ter risanje QR.
- `PaymentConverter` je navadna Java koda brez Android odvisnosti, testirana posebej.
- `INTERNET` dovoljenje ni prisotno v manifestu aplikacije (preveri manifest merger pred objavo).

## Omejitve

To je izvorni projekt, ne preverjena nameščena aplikacija. Izdelava APK in test skeniranja na fizičnem telefonu morata biti izvedena v Android Studio okolju.

## APK brez Android Studio: GitHub Actions

Če projekt objaviš v **novem, ločenem GitHub repozitoriju**, se ob potisku na `main`/`master` oziroma ročnem zagonu `Build Android APK` v zavihku Actions sproži sestavljanje. Prenesi artefakt `UPN-EPC-debug-apk`. Ta postopek ni bil izveden v trenutnem okolju.