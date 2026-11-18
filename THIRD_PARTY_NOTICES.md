# Mentions relatives aux composants tiers

Les dépendances ci-dessous sont déclarées dans les fichiers Gradle du projet. Elles sont téléchargées par Gradle lors de la compilation ; elles ne sont pas toutes redistribuées comme fichiers sources dans ce dépôt. Les versions indiquées correspondent au catalogue de versions du projet au moment de cette publication.

## Code tiers adapté et présent dans le dépôt

### RuntimeTypeAdapterFactory — Gson

Le fichier `app/src/main/java/com/gfocreation/heartrateandzoneannounce/misc/RuntimeTypeAdapterFactory.kt` est une adaptation Kotlin de l'utilitaire `RuntimeTypeAdapterFactory` publié dans le projet Gson de Google.

- Projet d'origine : https://github.com/google/gson
- Fichier source de référence : https://github.com/google/gson/blob/main/extras/src/main/java/com/google/gson/typeadapters/RuntimeTypeAdapterFactory.java
- Copyright du code de référence : Copyright (C) 2011 Google Inc.
- Licence du code de référence : Apache License, Version 2.0 (Apache-2.0).
- Texte de la licence : [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0).

Le fichier Kotlin de ce dépôt a été adapté et modifié pour Hraza par gfocreation-ops. Cette mention n'implique pas que Google approuve ou soutient Hraza. La licence principale PolyForm Noncommercial 1.0.0 ne remplace pas les conditions Apache 2.0 applicables à ce fichier.

## Dépendances de l'application

Les bibliothèques AndroidX et Android Jetpack listées ci-dessous sont distribuées sous Apache License 2.0 :

- AndroidX Core KTX (`androidx.core:core-ktx:1.17.0`)
- AndroidX Lifecycle Runtime KTX (`androidx.lifecycle:lifecycle-runtime-ktx:2.9.4`)
- AndroidX Activity Compose (`androidx.activity:activity-compose:1.11.0`)
- Jetpack Compose (BOM `2024.09.00`, UI, UI Graphics, UI Text, UI Tooling Preview, Material Icons Extended, Material 3, Foundation et Foundation Layout)
- AndroidX DataStore Preferences (`androidx.datastore:datastore-preferences:1.2.0`)
- AndroidX Navigation Compose (`androidx.navigation:navigation-compose:2.7.2`)
- AndroidX AppCompat (`androidx.appcompat:appcompat:1.7.1`)

Autres dépendances de l'application :

- Gson (`com.google.code.gson:gson:2.13.2`) — Apache License 2.0.

## Dépendances de test

- JUnit 4 (`junit:junit:4.13.2`) — Eclipse Public License 1.0 (EPL-1.0).
- AndroidX Test JUnit (`androidx.test.ext:junit:1.3.0`) — Apache License 2.0.
- AndroidX Espresso Core (`androidx.test.espresso:espresso-core:3.7.0`) — Apache License 2.0.
- AndroidX Compose UI Test JUnit4 — Apache License 2.0.

## Outils de compilation

- Kotlin et ses plugins Gradle (`2.2.10`) — Apache License 2.0.
- Android Gradle Plugin (`com.android.application:9.1.1`) — Apache License 2.0.
- Gradle Wrapper / Gradle (`9.3.1`) — Apache License 2.0.

Les licences et notices propres à chaque bibliothèque ou outil restent applicables. Les licences des dépendances téléchargées sont fournies avec leurs distributions respectives ; cette liste informative ne les remplace pas.
