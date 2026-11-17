# Hraza

**Hraza** est une application Android de suivi de la fréquence cardiaque et d'annonce des zones cardiaques.

## Version

Version actuelle : **3.6.0**.

Ce dépôt contient les fichiers sélectionnés pour la diffusion du projet. Le dépôt de référence pour la maintenance reste [GFo126/hraza](https://github.com/GFo126/hraza).

## Prérequis techniques

- Android Studio compatible avec Android Gradle Plugin 9.1.1 et Gradle 9.3.1.
- JDK 17 ou version ultérieure pour exécuter Gradle.
- Android SDK 36.
- Appareil Android 12 (API 31) ou ultérieur pour installer l'application.

L'application utilise les fonctionnalités Bluetooth et les permissions Android associées.

## Compilation

1. Clonez ce dépôt.
2. Ouvrez le projet dans Android Studio et laissez-le synchroniser le projet Gradle.
3. Pour construire l'APK de débogage depuis la racine du dépôt :

   Sous Windows :

   ```powershell
   .\\gradlew.bat assembleDebug
   ```

   Sous Linux ou macOS :

   ```bash
   ./gradlew assembleDebug
   ```

L'APK de débogage est généré dans `app/build/outputs/apk/debug/`.

## Langue

L'application est en français.

## Licences

Le code propre à Hraza est distribué selon la [licence PolyForm Noncommercial 1.0.0](LICENSE). Cette licence autorise les usages non commerciaux dans les conditions qu'elle définit ; elle n'accorde pas de droit général d'utilisation commerciale. Toute utilisation commerciale nécessite une autorisation distincte du titulaire des droits.

Les composants tiers restent soumis à leurs propres licences. Les mentions correspondantes figurent dans [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), et le texte de la licence Apache 2.0 applicable à l'adaptation de `RuntimeTypeAdapterFactory` est fourni dans [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0).
