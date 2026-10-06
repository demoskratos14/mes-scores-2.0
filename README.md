# Mes Scores

Application Android (Kotlin + Jetpack Compose) pour noter les scores de vos jeux de société : tableaux par manches, compteurs, Tarot avec calcul automatique, championnats, jeux personnalisés, journal des parties et minuteur.

- Identifiant : `com.aventure.messcores`
- minSdk 26, compileSdk/targetSdk 35
- Kotlin 2.0.21 (plugin Compose), Android Gradle Plugin 8.6.1, Gradle 8.9 (via le wrapper)

## Compiler

Le wrapper Gradle est inclus (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) : aucune installation de Gradle n'est nécessaire, seulement un JDK 17.

```
./gradlew assembleDebug      # APK de test  -> app/build/outputs/apk/debug/Mes scores-debug.apk
./gradlew assembleRelease    # APK minifié  -> app/build/outputs/apk/release/Mes scores-release.apk
```

## Tests

```
./gradlew testDebugUnitTest
```

Les tests unitaires (`app/src/test/`) couvrent le calcul du Tarot (`TarotScoring.kt` : 3, 4 et 5 joueurs, garde sans, primes, chelems, somme des scores toujours nulle) et les règles de classement et de fin de partie (`ScoreViewModel` : seuil, manche à terminer, égalité, nombre de manches).

Sous Linux/macOS, si `gradlew` n'est pas exécutable après un clonage : `chmod +x gradlew`
(ou, une fois pour toutes : `git update-index --chmod=+x gradlew`).

Pour régénérer le wrapper : `gradle wrapper --gradle-version 8.9`.

## GitHub Actions

Le workflow `.github/workflows/build.yml` construit l'APK debug à chaque push sur `main` et le publie comme artefact.

Pour obtenir un APK **release signé**, créez dans *Settings > Secrets and variables > Actions* :
`KEYSTORE_BASE64` (votre fichier `.jks` encodé en base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
puis lancez le workflow à la main (*Run workflow*) en cochant « release ».
En local, la signature se configure avec les mêmes noms en variables d'environnement, plus `KEYSTORE_FILE` (chemin du `.jks`).
Sans ces variables, la release est construite non signée. Gardez votre clé de signature en lieu sûr : sans elle, impossible de publier des mises à jour.

## Structure du projet

```
MesScores/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/
├── .github/workflows/build.yml
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    └── src/
        ├── test/java/com/aventure/messcores/   TarotScoringTest.kt, ScoreViewModelTest.kt
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/aventure/messcores/
            │   ├── MainActivity.kt            navigation, sauvegarde automatique
            │   ├── ScoreViewModel.kt          état de la partie en cours
            │   ├── GameRules.kt, GameRulesText.kt   règles des jeux et fiches de règles
            │   ├── TarotScoring.kt            calcul d'une manche de Tarot (fonction pure, testée)
            │   ├── GameRepository.kt          jeux prédéfinis et personnalisés
            │   ├── GameHistory.kt, GameHistoryScreen.kt   journal des parties
            │   ├── SetupScreen.kt, ChooseGameScreen.kt, CreateGameScreen.kt
            │   ├── ScoreScreen.kt, CounterScreen.kt       tableau par manches / compteurs
            │   ├── TeamRoundsScreen.kt, NewTeamRoundScreen.kt, NewTarotRoundScreen.kt
            │   ├── TournamentSetupScreen.kt, TournamentBracketScreen.kt, TournamentViewModel.kt
            │   ├── TimerViewModel.kt, TimerOverlay.kt, TimerAlert.kt, TimerReceiver.kt   minuteur
            │   ├── AppBackground.kt, KeepScreenOn.kt, Savers.kt
            └── res/
                ├── values/strings.xml
                ├── drawable-nodpi/bg_board_games.webp
                └── mipmap-*/ic_launcher*.png
```
