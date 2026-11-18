# Hraza

**Hraza** est une application Android de suivi de la fr├®quence cardiaque et d'annonce des zones cardiaques.

## Version

Version actuelle : **3.6.0**.

Ce d├®p├┤t contient les sources, les ressources et les fichiers de configuration n├®cessaires ├á la compilation de l'application Hraza.

## Pr├®requis techniques

- Android Studio compatible avec Android Gradle Plugin 9.1.1 et Gradle 9.3.1.
- JDK 17 ou version ult├®rieure pour ex├®cuter Gradle.
- Android SDK 36.
- Appareil Android 12 (API 31) ou ult├®rieur pour installer l'application.

L'application utilise les fonctionnalit├®s Bluetooth et les permissions Android associ├®es.

## Compilation

1. Clonez ce d├®p├┤t.
2. Ouvrez le projet dans Android Studio et laissez-le synchroniser le projet Gradle.
3. Pour construire l'APK de d├®bogage depuis la racine du d├®p├┤t :

   Sous Windows :

   ```powershell
   .\gradlew.bat assembleDebug
   ```

   Sous Linux ou macOS :

   ```bash
   ./gradlew assembleDebug
   ```

L'APK de d├®bogage est g├®n├®r├® dans `app/build/outputs/apk/debug/`.

## Langue

L'application est en fran├ºais.

## Licences

Le code propre ├á Hraza est distribu├® selon la [licence PolyForm Noncommercial 1.0.0](LICENSE). Cette licence autorise les usages non commerciaux dans les conditions qu'elle d├®finit ; elle n'accorde pas de droit g├®n├®ral d'utilisation commerciale. Toute utilisation commerciale n├®cessite une autorisation distincte du titulaire des droits.

Les composants tiers restent soumis ├á leurs propres licences. Les mentions correspondantes figurent dans [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), et le texte de la licence Apache 2.0 applicable ├á l'adaptation de `RuntimeTypeAdapterFactory` est fourni dans [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0).
