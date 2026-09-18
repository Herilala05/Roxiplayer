# Roxi Player — version 1.3.4

Lecteur de musique et vidéo Android (Kotlin + Jetpack Compose + Media3).

La version 1.3.4 ajoute un lecteur vidéo complet tout en conservant le nouvel
accueil moderne inspiré de Lark Player. Elle apporte les gestes de volume,
luminosité et recherche, le changement de vidéo, la rotation, le verrouillage,
les modes d'affichage, la vitesse, la répétition et les sous-titres. Elle conserve
aussi l'identité visuelle de Roxi et la section Vidéos modernisée avec une
navigation inspirée de Files by Google : miniatures, dossiers, recherche, tri
et affichage en grille ou en liste. Les liens directs MP4/HLS/DASH, l'image dans
l'image et le dossier secret protégé par code PIN restent disponibles.

## Compiler l'APK

Prérequis : JDK 17 et le SDK Android (installés avec Android Studio).

1. Créer le fichier `local.properties` à la racine avec le chemin du SDK, par exemple :
   `sdk.dir=C\:\\Users\\TON_NOM\\AppData\\Local\\Android\\Sdk`
2. Dans un terminal, à la racine du projet :
   - Windows : `gradlew.bat assembleDebug`
   - Linux / macOS : `./gradlew assembleDebug`
3. L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

## Structure

- `data/` : scan de la musique, favoris/playlists/historique, paroles (.lrc + ID3)
- `playback/` : service de lecture en arrière-plan + contrôleur
- `ui/player/PlayerSheet.kt` : mini-lecteur ↔ plein écran (transition « container transform »)
- `ui/screens/` : Accueil, Explorer, Bibliothèque, Vidéos, Moi, listes de titres
- `video/` : scan, lecteur plein écran et dossier secret
- `ui/RoxiApp.kt` : navigation, barre du bas, permissions

## Paroles

1. Fichier `.lrc` de même nom que la chanson (dans le dossier choisi dans Moi → Dossier des paroles)
2. Sinon, paroles intégrées au MP3 (tag ID3 USLT)
