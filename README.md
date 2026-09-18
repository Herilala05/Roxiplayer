# Roxi Player — version 1.3.6

Lecteur de musique et vidéo Android (Kotlin + Jetpack Compose + Media3).

La version 1.3.3 apporte un nouvel accueil moderne inspiré de Lark Player tout
en gardant l'identité de Roxi. Elle améliore aussi la section Vidéos avec une
navigation inspirée de Files by Google : miniatures, dossiers, recherche, tri
et affichage en grille ou en liste. Les liens directs MP4/HLS/DASH, l'image dans
l'image et le dossier secret protégé par code PIN restent disponibles.

## Version 1.3.6

Sources 1.3.4 récupérées depuis la branche `codex-build-v1.3.3`, avec les
corrections de démarrage, miniatures et navigation préparées en 1.3.5/1.3.6.

- Gestes tactiles, verrouillage, rotation, sous-titres, répétition et aléatoire conservés.
- Panneau Réglages : volume, luminosité locale, vitesse de 0,5× à 2×, format d’image.
- Réglages de vitesse et de format mémorisés.
- Liste vidéo précédente/suivante selon le filtre et le tri affichés, sans transférer
  toutes les URI par Intent. Après destruction du processus : reprise de la vidéo
  courante uniquement, pas de restauration de la file complète.
- Musique mise en pause à l’ouverture vidéo ; gestion du focus audio et des écouteurs.
- Messages d’accès refusé, de chargement et de recherche vide ; sélection Android 14+.
- Contrôles du lecteur défilants horizontalement sur les écrans étroits.
- PiP protégé et proportion bornée ; arrêt du lecteur hors écran ; correction du
  verrouillage et disparition automatique des messages de retour.

Compilation via GitHub Actions. Un APK compilé doit encore être testé sur téléphone,
notamment pour les gestes, les autorisations, le PiP et la reprise de lecture.

## Corrections écrites

- Android 12+ : logo dans l’écran de démarrage système au lieu d’une icône vide.
- Introduction Compose : titre immédiat, animation nominale de 440 ms, sans
  pause ajoutée. Le temps réel de démarrage reste à mesurer sur téléphone.
- Navigation : ignore les appuis redondants et les demandes pendant une transition.
- Miniatures : cache de 12 Mio, décodage à 320 × 200, deux décodages simultanés.
  Android 8.0 utilise une icône de remplacement plutôt qu’une image pleine taille.
- Scan vidéo : pas de scans manuels concurrents ; annulation respectée quand
  on quitte l’écran ; indicateur de chargement réinitialisé même en cas d’erreur.
- Lecteur : position conservée lors d’une recréation, pause quand l’activité
  s’arrête et protection si le téléphone refuse le mode image dans l’image.

## Vérification des sources

Relecture des modifications, vérification XML et intégrité de l’archive.
Ces vérifications ne valident ni la compilation Kotlin, ni le comportement Android.
Les commandes du lecteur utilisent les API Media3 déjà déclarées par le projet :
https://developer.android.com/reference/kotlin/androidx/media3/ui/PlayerView

## Validation Android à effectuer avant diffusion

1. Compiler avec JDK 17 et SDK 35 : `bash gradlew assembleDebug`.
2. Vérifier le lancement à froid et à chaud, avec et sans autorisations médias.
3. Alterner rapidement Accueil, Musique et Vidéos ; vérifier page et onglet actif.
4. Parcourir une grande collection et des vidéos 4K ; surveiller mémoire et logcat.
5. Lire, mettre en pause, quitter/revenir, pivoter, essayer le mode image dans l’image.
6. Vérifier précédent/suivant avec trois vidéos, puis après tri, filtre et recherche.
   Tester la première/dernière vidéo, un lien isolé et une vidéo inaccessible.
7. Vérifier volume, luminosité, vitesse et format en portrait/paysage, puis quitter
   le lecteur : la luminosité des autres écrans doit rester inchangée.
8. Refuser l’accès vidéo, l’autoriser ensuite, puis tester une sélection limitée
   sur Android 14+ et modifier cette sélection.
9. Vérifier la signature avant toute installation par-dessus une ancienne version.
   Le projet utilise la signature debug : une autre clé ne permet pas la mise à jour
   directe. Ne pas désinstaller l’application existante sans sauvegarder ses données.

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
