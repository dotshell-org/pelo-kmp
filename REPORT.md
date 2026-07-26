# Rapport d'audit — Mode Navigation

**Date :** 2026-07-26
**Périmètre :** le mode déclenché par le bouton « Démarrer » d'un itinéraire (guidage temps réel).
**Méthode :** lecture statique du code (aucune exécution sur appareil). Chaque constat est référencé `fichier:ligne`.

## Fichiers concernés

| Fichier | Rôle |
|---|---|
| `app/src/commonMain/kotlin/eu/dotshell/pelo/App.kt` | Orchestration : état, overlay, caméra, cycle de vie |
| `.../generic/service/NavigationModeController.kt` | Contrôleur (waypoints, cap, télémétrie) |
| `.../generic/service/NavigationModeStateStore.kt` | Persistance du flag « navigation active » |
| `.../generic/ui/screens/plan/NavigationModeState.kt` | Calcul des instructions affichées |
| `.../generic/ui/screens/plan/NavigationModeOverlay.kt` | UI de l'overlay |
| `.../generic/ui/screens/plan/NavigationLineIcon.kt` | Badge de ligne |
| `.../androidMain/.../service/NavigationModeForegroundService.kt` | Service de premier plan Android |
| `.../androidMain/kotlin/eu/dotshell/pelo/MainActivity.kt` | Service + écran allumé + lockscreen |
| `.../iosMain/kotlin/eu/dotshell/pelo/MainViewController.kt` | Pendant iOS (quasi vide) |

---

## 1. Résumé exécutif

Le mode Navigation **fonctionne visuellement** mais repose sur une architecture cassée à trois endroits structurants :

1. **Le démarrage de la navigation s'auto-annule côté plateforme.** Activer la navigation masque la bottom sheet, ce qui déclenche `closeItinerary()`, qui rappelle `onNavigationModeChanged(false)`. Conséquence : le service de premier plan est démarré puis **immédiatement arrêté**, l'écran ne reste pas allumé, et **le tracé de l'itinéraire disparaît de la carte**. (NAV-01)
2. **Il existe deux états de navigation concurrents** (`service.NavigationModeUiState` et `ui.screens.plan.NavigationModeUiState`, même nom, classes différentes). Le premier calcule des waypoints, une distance, une instruction, un « arrivé » — **rien de tout cela n'est affiché**. Le second recalcule tout à chaque recomposition. ~60 % du contrôleur est du code mort. (NAV-02, NAV-20)
3. **Aucune sortie de secours.** Sans position GPS, l'overlay ne s'affiche pas du tout, alors que la barre d'onglets, la barre de recherche et les gestes carte sont déjà désactivés : l'utilisateur est piégé sur une carte figée. (NAV-03)

À cela s'ajoutent : pas de fin de navigation automatique, un compte à rebours faux avant le départ et qui ne se rafraîchit pas, un guidage qui n'avance jamais en métro, aucun guidage piéton, des textes en dur non traduits et sans accents, et un mode iOS quasiment inexistant (écran qui s'éteint, permission « Always » demandée sur le mauvais `CLLocationManager`).

**Bilan :** 5 bugs critiques, 9 majeurs, 12 problèmes UI, 11 problèmes UX, 7 dettes techniques.

---

## 2. Bugs critiques (P0)

### NAV-01 — Démarrer la navigation coupe le service, l'écran allumé et le tracé

**Fichiers :** `App.kt:749`, `App.kt:751-761`, `App.kt:571-581`, `MainActivity.kt:57-71`

Enchaînement :

```kotlin
// App.kt:749
val hasSheet = !navigationState.isActive && (itineraryActive || ...)

// App.kt:751-753
LaunchedEffect(sheetContentKey) {
    if (hasSheet) bottomSheetState.expand() else bottomSheetState.hide()
}

// App.kt:754-761  ← aucune garde sur navigationState.isActive
LaunchedEffect(bottomSheetState.currentValue) {
    if (bottomSheetState.currentValue == SheetValue.Hidden) {
        closeSheet()
        if (itineraryActive) closeItinerary()   // ← appelle onNavigationModeChanged(false)
    }
}
```

Séquence réelle au tap sur « Démarrer » :

1. `navigationController.start(...)` → `isActive = true`
2. `onNavigationModeChanged(true)` → `MainActivity` démarre le foreground service, pose `FLAG_KEEP_SCREEN_ON` et `setShowWhenLocked(true)`
3. recomposition → `hasSheet = false` → `bottomSheetState.hide()`
4. `currentValue == Hidden` → `closeItinerary()` → `onNavigationModeChanged(false)`
5. `MainActivity` **arrête le service**, retire `KEEP_SCREEN_ON` et `setShowWhenLocked(false)`

**Conséquences observables :**

- L'écran s'éteint pendant la navigation (comportement de veille normal).
- Pas de suivi en arrière‑plan, pas de notification persistante, pas d'affichage sur écran verrouillé — toute la mécanique `NavigationModeForegroundService` est neutralisée.
- `closeItinerary()` vide `activeJourneys` → `itineraryGeoJson` devient `null` (`App.kt:469-489`) → **la ligne du trajet disparaît de la carte dès le démarrage**.
- `itineraryActive` repasse à `false` → `filteredStopsCollection` (`App.kt:524-565`) retombe sur **tous les arrêts du réseau** au lieu des seuls arrêts du trajet : pollution visuelle + coût de rendu inutile pendant toute la navigation.
- Le hack `hasAppliedFirstNavigationCallback` (`MainActivity.kt:32,58-62`) est une rustine posée sur ce bug, qui n'absorbe que le premier appel et laisse passer celui-ci.

**Correctif suggéré :** garder l'effet de fermeture (`if (!navigationState.isActive && itineraryActive) closeItinerary()`), et ne pas réinitialiser `activeJourneys` / `itineraryActive` quand la navigation est active. Le hack `hasAppliedFirstNavigationCallback` devient alors supprimable.

---

### NAV-03 — Sans position GPS : écran mort, aucune sortie possible

**Fichier :** `App.kt:1113-1147`

```kotlin
if (navigationState.isActive) {
    val loc = userLocation
    val activeJourney = navigationState.journey
    if (loc != null && activeJourney != null) {   // ← overlay conditionné à la position
        NavigationModeOverlay(...)
    }
}
```

Or, dès que `navigationState.isActive` est vrai :

- la `NavigationBar` n'est plus composée (`App.kt:920`)
- la barre de recherche est masquée (`showTopBar`, `App.kt:793`)
- la bottom sheet est masquée (`hasSheet`, `App.kt:749`)
- la carte est non interactive (`interactive = !navigationState.isActive`, `App.kt:1283`)
- il n'y a **aucun `BackHandler`** pour le mode navigation (le seul du projet est dans `SettingsTab`, `App.kt:1590`)

**Résultat :** si `userLocation == null`, l'utilisateur voit une carte figée sans aucun contrôle. Cas déclencheurs très fréquents : premier fix pas encore arrivé au moment du tap, métro/tunnel, permission de localisation refusée, GPS désactivé. Sur Android le bouton retour quitte l'appli ; sur iOS il n'y a rien.

**Correctif suggéré :** toujours afficher l'overlay dès `isActive` (avec un état « recherche du signal GPS… »), et ajouter un `BackHandler` qui propose de quitter la navigation.

---

### NAV-04 — Le mode Navigation ne se termine jamais

**Fichiers :** `NavigationModeController.kt:34,90`, `NavigationModeState.kt:21,43,96`, `NavigationModeOverlay.kt` (intégral)

Deux drapeaux d'arrivée existent et **aucun n'est lu** :

- `NavigationModeUiState.isComplete` (contrôleur) — jamais consommé dans `App.kt`
- `NavigationModeUiState.isFinished` (état UI) — jamais référencé dans `NavigationModeOverlay.kt`

`instructionFor(...)` produit bien `"Vous êtes arrivé"` (`NavigationModeController.kt:241`) mais cette chaîne appartient à l'état mort (voir NAV-02) et n'atteint jamais l'écran.

**Conséquences :** arrivé à destination, l'overlay continue d'afficher « Dans 0 arrêt, descendre à X », le service tourne, l'écran reste allumé (une fois NAV-01 corrigé), la batterie continue d'être consommée. Seule sortie : la croix.

---

### NAV-05 — Le flag persistant peut rester bloqué à `true` (Android)

**Fichiers :** `NavigationModeForegroundService.kt:65-84,86-106,110-123`, `MainActivity.kt:38-42`

Dans `onStartCommand` :

```kotlin
ACTION_START, null -> {
    NavigationModeStateStore.setNavigationActive(this, true)   // écrit AVANT toute vérification
    startForeground(NOTIFICATION_ID, buildForegroundNotification())
    startTracking()   // ← peut appeler stopSelf() si la permission manque
    ...
}
```

Si la permission de localisation manque, `startTracking()` fait `stopSelf()` (`ligne 121`) **sans remettre le flag à `false`**. `onDestroy` ne corrige rien non plus : la garde `if (!NavigationModeStateStore.isNavigationActive(this))` (`ligne 95`) est fausse dans ce cas précis, donc la branche de nettoyage est sautée.

Même effet en cas de mort de process pendant la navigation : le flag reste `true`, le contrôleur Compose repart à `isActive = false`.

**Conséquence :** au lancement suivant, `MainActivity.onCreate` (`ligne 39`) lit `true`, active `setShowWhenLocked(true)` + `FLAG_KEEP_SCREEN_ON` **en permanence**, et `hasAppliedFirstNavigationCallback` empêche le premier `false` de corriger la situation. L'écran ne s'éteint plus jamais et l'appli s'affiche sur l'écran verrouillé, sans navigation en cours. Seul remède utilisateur : forcer l'arrêt de l'appli.

**Correctif suggéré :** écrire le flag seulement après le succès de `startForeground` + `startTracking`, l'effacer sur tout chemin d'échec, et réconcilier au démarrage (si le flag est vrai mais qu'aucune navigation ne peut être restaurée → le remettre à `false` et arrêter le service).

---

### NAV-06 — `startForeground` non protégé (Android 12+/14+)

**Fichier :** `NavigationModeForegroundService.kt:77`

`startForeground` est appelé sans `try/catch`. Sur Android 12+ un démarrage depuis l'arrière‑plan lève `ForegroundServiceStartNotAllowedException` ; sur Android 14+, un service de type `location` lève `SecurityException` si la permission de localisation n'est pas accordée au moment du démarrage. `targetSdk = 36` (`app/build.gradle.kts:163`) — l'appli est pleinement soumise à ces règles.

Par ailleurs `POST_NOTIFICATIONS` est déclaré au manifeste mais **jamais demandé à l'exécution** (`MainActivity.kt:193-196` ne liste que `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`). Sur Android 13+, la notification persistante n'apparaît donc pas : plus aucun indicateur visible que la localisation tourne, et plus aucun moyen de revenir dans l'appli depuis le tiroir de notifications.

---

## 3. Bugs majeurs (P1)

### NAV-07 — Le guidage n'avance jamais en souterrain (métro)

**Fichier :** `NavigationModeState.kt:138-147`

```kotlin
val maxLegIndexByLocation = if (nearestStop.isLegEnd && ...) nearestStop.legIndex + 1
                            else nearestStop.legIndex
currentIndex = currentIndex.coerceAtMost(maxLegIndexByLocation)
```

La position ne peut que **reculer** l'index de tronçon, jamais l'avancer. En métro, le dernier fix GPS date de l'entrée en station : `nearestStop` reste figé sur le tronçon 0, donc `currentIndex` est bloqué à 0 pendant tout le trajet, même une heure plus tard. L'utilisateur voit indéfiniment le premier tronçon.

Aggravant : `findNearestJourneyStopCandidate` (`ligne 228-258`) **n'a aucun seuil de distance**. Un utilisateur à 30 km du trajet obtient quand même un « arrêt le plus proche », qui contraint silencieusement l'état.

---

### NAV-08 — Distances calculées en degrés bruts (biais ~30 % à Lyon)

**Fichiers :** `GeometryUtils.squaredDistance` (`utils/geo/GeometryUtils.kt:54-58`), utilisé en `NavigationModeState.kt:250-257`, `NavigationModeState.kt:294-302`, `GeometryUtils.findNavigationAxisSegment:85-90`

```kotlin
fun squaredDistance(lat1, lon1, lat2, lon2): Double {
    val dLat = lat1 - lat2
    val dLon = lon1 - lon2
    return dLat * dLat + dLon * dLon   // pas de facteur cos(latitude)
}
```

À 45,75° N, 1° de longitude vaut ≈ 0,70 × 1° de latitude en distance réelle. L'écart est donc surpondéré d'environ 43 % sur l'axe est‑ouest. Impacts :

- **arrêt le plus proche faux** quand deux arrêts sont proches mais orientés différemment → mauvais tronçon courant, mauvais compte d'arrêts restants
- **segment de cap faux** dans `findNavigationAxisSegment` → la carte s'oriente dans la mauvaise direction

`GeometryUtils.distanceMeters` (haversine, `ligne 42`) existe déjà : il suffit de l'utiliser, ou d'appliquer `dLon * cos(lat)` avant l'élévation au carré.

---

### NAV-09 — Compte à rebours faux avant le départ, et jamais rafraîchi

**Fichiers :** `NavigationModeState.kt:154-183`, `App.kt:1117-1121`

**(a) Valeur fausse.** `formatRemainingTime` :

```kotlin
val remainingSeconds = if (elapsedSinceDeparture in 0..fullTripSeconds) {
    fullTripSeconds - elapsedSinceDeparture
} else {
    fullTripSeconds          // ← repli
}
```

Tant que l'heure courante est **avant** `departureTime` (cas normal : on démarre la navigation en attendant le bus), `elapsedSinceDeparture` vaut `now + 86400 - departure`, sort de la plage, et la fonction renvoie la **durée totale du trajet**. Exemple : trajet 14h20 → 14h45, il est 14h03 → l'overlay affiche « 25 min » alors qu'il reste 42 min, et **la valeur reste figée** jusqu'à 14h20. Incohérent avec l'heure d'arrivée affichée juste en dessous.

`computeRemainingJourneySeconds` (`ligne 100-105`) fait le bon calcul mais n'est utilisée que pour `isFinished`, lui‑même inutilisé (NAV-04).

**(b) Jamais rafraîchi.** `App.kt:1119` appelle `GeometryUtils.currentTimeInSeconds()` **directement dans le corps de composition**, sans état observable ni ticker. L'heure n'est donc relue que si une autre recomposition survient — en pratique à chaque fix GPS (5 s). Sans GPS (métro), le compte à rebours **se fige complètement**.

**(c) Troncature.** `remainingSeconds / 60` sans arrondi supérieur → « 0 min » affiché pendant les 59 dernières secondes.

**Correctif suggéré :** un ticker `LaunchedEffect { while (true) { now = ...; delay(1_000) } }`, et remplacer `formatRemainingTime` par `computeRemainingJourneySeconds` (arrivée − maintenant).

---

### NAV-10 — Compte d'arrêts instable et faux avant l'embarquement

**Fichier :** `NavigationModeState.kt:280-306`

`computeRemainingStopsOnLeg` prend l'index de l'arrêt **le plus proche** du tronçon. Trois problèmes :

- **Pas d'hystérésis** : entre deux arrêts, le plus proche bascule d'avant en arrière → le texte oscille « Dans 5 arrêts » / « Dans 4 arrêts » / « Dans 5 arrêts ».
- **Pas de monotonie** : le compteur peut remonter, ce qu'un utilisateur ne pardonne pas dans une UI de guidage.
- **Pas de seuil** : un utilisateur encore chez lui, mais géographiquement plus proche du terminus que du départ, obtient `remainingStops = 0` → « **Au prochain arrêt, descendre à X** » alors qu'il n'est pas monté.

Combiné à NAV-08, l'index choisi peut en plus être le mauvais.

---

### NAV-11 — Aucun guidage piéton

**Fichier :** `NavigationModeState.kt:119` (`journey.legs.filterNot { it.isWalking }`)

Tous les tronçons à pied sont éliminés de la logique de guidage. Or ce sont exactement les segments où l'utilisateur a besoin d'être guidé (rejoindre l'arrêt de départ, correspondance à pied entre deux stations, sortie vers la destination finale). Le mode affiche à la place le premier tronçon en transport avec « Dans 8 min, monter a X », sans indiquer **où** est X ni **combien de mètres** il reste.

Ironie : `NavigationModeController` calcule déjà `distanceToNextMeters` et l'instruction `"Rejoignez ${target.stopName}"` (`NavigationModeController.kt:243`) — code mort (NAV-02).

`calculateJourneyTrace` (`MapGeoJson.kt:536`) construit pourtant bien la géométrie piétonne, elle n'est utilisée que pour le cap caméra.

---

### NAV-12 — Trajet 100 % piéton : overlay vide et cassé

**Fichiers :** `NavigationModeState.kt:32-45`, `NavigationModeOverlay.kt:92-97`

Si `nonWalkingLegs` est vide, `buildNavigationModeUiState` renvoie `currentLeg = null`, `displayedLeg = null`, `actionText = "Trajet en cours"`, `directionText = ""`.

L'overlay affiche alors `NavigationLineIcon(lineName = "")` → le repli dessine un cercle coloré contenant « **?** » (`NavigationLineIcon.kt:47`), à côté d'un libellé vide et d'un texte générique, dans une carte de 132 dp majoritairement vide. Cas atteignable dès qu'on lance un itinéraire vers une adresse proche.

Même symptôme partiel sur un tronçon dont `direction` est vide : `directionText` devient littéralement « **Direction ?** » (`NavigationModeState.kt:85`).

---

### NAV-13 — Double flux de localisation haute précision (Android)

**Fichiers :** `LocationProvider.android.kt:40-49` (5 s / 3 s, `PRIORITY_HIGH_ACCURACY`), `NavigationModeForegroundService.kt:127-146` (5 s / 2 s, `PRIORITY_HIGH_ACCURACY`)

Pendant la navigation, **deux** clients `FusedLocationProvider` haute précision tournent en parallèle. Consommation batterie doublée. Pire, les fixes du service ne servent **qu'au `TripDetector`** : ils n'alimentent jamais `NavigationModeController`. Donc quand l'appli passe en arrière‑plan, le service collecte des positions que la navigation n'utilise pas, et l'état de guidage reste gelé jusqu'au retour au premier plan.

**Correctif suggéré :** un seul flux, porté par le service, rediffusé au contrôleur via un `StateFlow`.

---

### NAV-14 — Le tap « Démarrer » est asynchrone, non protégé, et sur le thread principal

**Fichiers :** `App.kt:851-857`, `MapGeoJson.kt:536-599`, `TransportViewModel.kt:1317`

```kotlin
onStartNavigation = { journey ->
    scope.launch {                                   // rememberCoroutineScope → dispatcher Main
        val tracePoints = calculateJourneyTrace(journey, viewModel)
        navigationController.start(journey, tracePoints)
        onNavigationModeChanged(true)
    }
}
```

- `calculateJourneyTrace` fait des appels réseau (`getLineByName`, une requête **par ligne distincte**). Hors ligne ou en réseau lent, **rien ne se passe visiblement pendant plusieurs secondes** : aucun spinner, aucun état désactivé sur le bouton.
- Le bouton reste cliquable : N taps → N traces calculées → N appels `start()` concurrents.
- `sectionLinesBetweenStops` (`TransportViewModel.kt:1317`) n'est **pas** `suspend` et ne bascule pas de dispatcher : ce découpage géométrique s'exécute donc sur le **thread principal**, à raison d'un par tronçon. Risque de jank au démarrage.
- La télémétrie `ItineraryChosen` est émise **avant** le démarrage effectif (`InlineItinerarySheetContent.kt:916-923`) : un démarrage qui échoue est quand même compté.

---

### NAV-15 — Rien ne vérifie la permission de localisation avant de démarrer

**Fichier :** `App.kt:851-857`

Aucune vérification de permission ni de disponibilité du GPS avant de basculer en mode navigation. Si la localisation est refusée, on entre directement dans le piège NAV-03.

---

## 4. Problèmes d'interface (UI)

### NAV-16 — Aucune gestion des zones sûres (encoche / Dynamic Island / barre gestuelle)

**Fichier :** `NavigationModeOverlay.kt:54`, `:140-148`

```kotlin
.padding(start = 12.dp, end = 12.dp, top = 30.dp, bottom = 12.dp)   // 30.dp en dur
```

Le reste de l'appli utilise correctement `windowInsetsPadding(WindowInsets.statusBars)` (`App.kt:963`, `App.kt:1010`, `App.kt:1431`). Ici, 30 dp en dur :

- insuffisant sur iPhone à Dynamic Island (safe area haute = 59 pt) → **la carte passe sous l'îlot**
- variable selon les Android à encoche

Idem en bas : `.height(108.dp).padding(bottom = 12.dp)` sans `navigationBarsPadding()`. La `NavigationBar` étant masquée en navigation, plus rien ne consomme l'inset : le contenu du bandeau inférieur (compte à rebours, croix, bouton alerte) **chevauche la barre gestuelle Android et le home indicator iOS**.

### NAV-17 — Hauteurs fixes → texte tronqué

**Fichier :** `NavigationModeOverlay.kt:59`, `:144`, `:105-109`

La carte du haut est figée à `height(132.dp)` et le `Text` de `actionText` n'a ni `maxLines` ni `overflow`. Un libellé long — « Dans 12 arrêts, changer de ligne a Gare de Vaise Rue de la Claire » en `titleLarge` — passe à 3–4 lignes et se fait **couper verticalement**. Le problème est mécanique dès que l'utilisateur augmente la taille de police système (accessibilité).

Même risque sur le bandeau inférieur (`108.dp`) qui empile `headlineMedium` + `titleMedium`.

### NAV-18 — Les cartes n'ont ni ombre ni bordure sur la carte

**Fichier :** `NavigationModeOverlay.kt:56-62`, `:140-148`

Fond `MaterialTheme.colorScheme.surface` posé directement sur le fond de carte, sans `.shadow(...)` ni `.floatingControlBorder(...)` — alors que **tous** les autres contrôles flottants de l'appli les utilisent (`App.kt:987-989`, `App.kt:1389-1391`, `App.kt:1495-1498`). Sur un fond de carte clair, les panneaux se fondent dans le décor et perdent leur hiérarchie.

### NAV-19 — Rayons de coin et typographie incohérents

**Fichier :** `NavigationModeOverlay.kt:43-47`, `:117`, `:125-130`

- Carte principale arrondie à `20.dp`, puce « À suivre » à `14.dp` : les deux sont visuellement contiguës, les rayons devraient s'accorder.
- La forme `bottomStart = 0.dp, bottomEnd = 20.dp` produit un angle bas‑gauche vif et un bas‑droit arrondi, alors que la puce en dessous est **alignée à gauche et de largeur contenue** — le raccord ne fonctionne pas visuellement.
- `Text(text = strings["next_up"], fontSize = 16.sp, style = MaterialTheme.typography.bodySmall)` : `fontSize` écrase la taille du style. Soit l'un, soit l'autre.

### NAV-20 — Deux classes différentes portant le même nom

**Fichiers :** `NavigationModeController.kt:24` et `NavigationModeState.kt:11`

Deux `NavigationModeUiState` coexistent dans des packages différents, toutes deux importées dans `App.kt` (`ligne 164` et `ligne 166`). Source d'erreur garantie à la première évolution.

### NAV-21 — Champs et modèles morts

- `NavigationModeUiState` (contrôleur) : `currentLegIndex`, `nextStopName`, `nextRouteName`, `nextStopType`, `distanceToNextMeters`, `remainingMinutes`, `instruction`, `isComplete` — **aucun n'est lu**. Seuls `isActive`, `journey` et `bearing` servent.
- Toute la machinerie waypoints (`toNavigationWaypoints`, `updateTargetWaypoint`, `ARRIVAL_RADIUS_METERS`, `dedupeConsecutive`, `instructionFor`, `remainingMinutesUntil`) est donc **du code mort** : `NavigationModeController.kt:94-101,174-254`.
- Modèles jamais référencés : `data/models/navigation/NavigationAlertPrompt.kt`, `NavigationAlertPromptKind.kt`, `NavigationKeyStopDeadline.kt`.
- `data/models/itinerary/LegStopPosition.kt` et `JourneyStopCandidate.kt` sont **dupliqués en `private data class`** dans `NavigationModeState.kt:215` et `:221`.

### NAV-22 — Libellé d'accessibilité trompeur sur la croix

**Fichier :** `NavigationModeOverlay.kt:167`

`contentDescription = strings["back"]` (« Retour ») sur un bouton qui **arrête la navigation**. Doit être « Arrêter la navigation ».

### NAV-23 — Aucune sémantique d'accessibilité sur les instructions

**Fichier :** `NavigationModeOverlay.kt` (intégral)

- `NavigationLineIcon` a `contentDescription = null` (`NavigationLineIcon.kt:35,42`) : les badges de ligne sont muets pour VoiceOver/TalkBack.
- Aucune `liveRegion` sur `actionText` : les changements d'instruction ne sont **jamais annoncés**. Pour une UI de guidage, c'est bloquant.

### NAV-24 — Bouton « alerte » silencieusement inopérant, ou pré-rempli n'importe où

**Fichier :** `App.kt:1128-1143`

```kotlin
val nearestStop = filteredStopsCollection?.features?.minByOrNull { stop ->
    if (userPos != null && coords.size >= 2) { ... } else Double.MAX_VALUE
}
if (nearestStop != null) { ... showAlertReport = true }
```

- Si `filteredStopsCollection` est `null` (arrêts pas encore chargés), **le tap ne fait rien du tout**, sans le moindre retour visuel.
- Si `userLocation` est `null`, toutes les distances valent `Double.MAX_VALUE` → `minByOrNull` renvoie le **premier arrêt de la liste**, arbitraire, et le pré‑remplit comme « votre arrêt ». Même défaut au `FAB` (`App.kt:1082-1088`).
- La distance est là encore calculée en degrés bruts (voir NAV-08).

### NAV-25 — Import qualifié en ligne dans la composition

**Fichier :** `NavigationModeOverlay.kt:179`

`remember { eu.dotshell.pelo.generic.service.TransportServiceProvider.getRealtimeConfig() }` : nom complet inline plutôt qu'un import. Cosmétique, mais isolé dans le fichier.

### NAV-26 — Bandeau inférieur sans libellés

**Fichier :** `NavigationModeOverlay.kt:153-162`

« 20 min » au‑dessus de « 14:35 », sans aucun libellé. Rien n'indique que le second est l'heure d'arrivée. Un « Arrivée 14:35 » lèverait l'ambiguïté.

### NAV-27 — Couleur d'alerte en dur

**Fichier :** `NavigationModeOverlay.kt:185` — `tint = Color(0xFFFACC15)` hors palette de thème (cohérent avec le FAB, mais hors système).

---

## 5. Problèmes d'expérience (UX)

### NAV-28 — Carte totalement verrouillée

`App.kt:1283` — `interactive = !navigationState.isActive` désactive **rotation, défilement, inclinaison, zoom, double‑tap, zoom rapide** (`MapGestures.android.kt:5-12`). Impossible de regarder la suite du trajet, de vérifier une sortie de station, de dézoomer pour se situer. Toutes les applis de navigation permettent l'exploration libre avec un bouton « recentrer ».

### NAV-29 — Zoom et inclinaison figés, inadaptés au transport

`App.kt:782,1284` — zoom `18.5` et inclinaison `55°` constants. Pertinent pour la marche, absurde pour un trajet de métro de 8 km : on ne voit qu'un pâté de maisons pendant 20 minutes. Aucun mode « vue d'ensemble ».

### NAV-30 — Caméra saccadée toutes les 5 secondes

`LocationProvider.android.kt:40` — intervalle 5 s, non ajusté en navigation. `MapCanvas.kt:198-215` relance un `animateTo` à chaque fix. Résultat : **un saut toutes les 5 secondes** au lieu d'un défilement continu, sans interpolation ni prédiction.

### NAV-31 — Rotation caméra potentiellement à contresens

`NavigationModeController.kt:114-124` — le cap vient du segment le plus proche sur la trace complète du trajet. Deux effets :

- au franchissement d'un segment, le cap saute de façon discontinue (350° → 10°) : selon la normalisation de `animateTo`, la carte peut **pivoter dans le mauvais sens sur presque un tour**
- sur un trajet qui repasse près de lui‑même (boucle, rue parallèle au retour), le segment le plus proche peut être **celui du retour** → la carte s'oriente à l'envers

### NAV-32 — Aucun guidage vocal ni retour haptique

Rien dans tout le périmètre. En pratique, l'utilisateur doit garder les yeux sur son téléphone pendant tout le trajet — ce qui est précisément ce qu'un mode navigation doit éviter, surtout à l'approche d'un arrêt de descente.

### NAV-33 — Aucune donnée temps réel dans le « mode temps réel »

Le guidage s'appuie uniquement sur l'horaire planifié (`journey.departureTime` / `arrivalTime`, secondes depuis minuit). L'appli dispose pourtant des positions véhicules en direct et des alertes trafic — l'un et l'autre sont **explicitement coupés** dès qu'un itinéraire est actif (`App.kt:463-468`). Un retard de 6 minutes ne se voit nulle part.

### NAV-34 — Notification statique

`NavigationModeForegroundService.kt:221-229` — texte figé « Pelo reste visible sur ecran verrouille… », jamais mis à jour avec l'instruction courante. Aucune action rapide (« Arrêter la navigation ») dans la notification, seul moyen de sortir quand l'appli est en arrière‑plan.

### NAV-35 — Notification plein écran agressive à chaque allumage d'écran

`NavigationModeForegroundService.kt:48-56,232-261`

À **chaque** `ACTION_SCREEN_ON` pendant la navigation, une notification `IMPORTANCE_HIGH` + `CATEGORY_CALL` + `setFullScreenIntent(..., true)` est émise : son, vibration, et relance de l'activité par‑dessus l'écran verrouillé. Vérifier l'heure sur son téléphone déclenche une interruption de type appel entrant.

Risques additionnels : `CATEGORY_CALL` sur une appli non téléphonique et `USE_FULL_SCREEN_INTENT` sont **restreints depuis Android 14** (permission auto‑refusée hors applis d'appel/réveil) — risque de non‑conformité Play Store et de comportement différent selon les versions.

### NAV-36 — Fermeture sans confirmation, et itinéraire perdu

`NavigationModeOverlay.kt:165-177` + `App.kt:1124-1127` — un tap accidentel sur la croix (48 dp, en bas à gauche, dans la zone du pouce) arrête la navigation immédiatement. Combiné à NAV-01, `closeItinerary()` a déjà vidé `activeJourneys`, `itineraryDeparture` et `itineraryArrival` : l'utilisateur retombe sur une carte vierge et doit **ressaisir tout son itinéraire**.

### NAV-37 — Aucune indication de progression

Pas de barre de progression, pas de liste des étapes, pas de vue « prochains arrêts », pas de distance restante. La seule information de position dans le trajet est la phrase « Dans N arrêts ».

### NAV-38 — Aucune détection de sortie d'itinéraire

Si l'utilisateur rate sa correspondance ou descend au mauvais arrêt, rien ne le détecte et rien n'est recalculé. Le guidage continue d'afficher un trajet devenu faux.

---

## 6. Internationalisation et orthographe

### NAV-39 — Textes de guidage codés en dur, hors système i18n

**Fichier :** `NavigationModeState.kt:39,67-83,93`

L'appli dispose de `values/strings.xml` (fr) et `values-en/strings.xml` (en), utilisés via `StringProvider`. L'overlay les utilise pour `next_up`, `back`, `alert_report_title` (`NavigationModeOverlay.kt:126,167,183`) — mais **toutes les instructions de guidage sont en français dur** dans `NavigationModeState.kt`.

Résultat en anglais : un écran mi‑anglais mi‑français — « **Up next** » au‑dessus de « **Dans 3 arrets, descendre a Bellecour** ».

Idem pour les chaînes du contrôleur mort (`NavigationModeController.kt:241-247`).

### NAV-40 — Accents manquants dans des textes visibles

**Fichier :** `NavigationModeState.kt`

| Ligne | Texte actuel | Correct |
|---|---|---|
| 69 | `monter a ${...}` | monter **à** |
| 71 | `l'arret suivant` | l'arr**ê**t suivant |
| 73 | `changer de ligne a $targetStopName` | ligne **à** |
| 75 | `descendre a $targetStopName` | descendre **à** |
| 78 | `Au prochain arret,` | arr**ê**t |
| 80 | `"arret"` / `"arrets"` | arr**ê**t / arr**ê**ts |

**Fichier :** `androidMain/res/values/strings.xml`

| Ligne | Texte actuel | Correct |
|---|---|---|
| 28 | `positions a jour` | **à** jour |
| 30 | `ecran verrouille` / `met a jour` | **é**cran verrouill**é** / met **à** jour |
| 31 | `Reveil navigation` | R**é**veil |
| 32 | `au reveil de l ecran` | r**é**veil de l**'é**cran |
| 34 | `Retour a la navigation` | **à** |

### NAV-41 — Notifications Android non traduites

`androidMain/res/values/` existe (fr) mais il n'y a **pas** de `values-en/`. Les notifications de navigation restent en français quelle que soit la langue de l'appareil.

### NAV-42 — Formulation à l'infinitif

« Dans 3 arrêts, descendre à X » — l'infinitif est inhabituel pour du guidage. L'impératif est la norme : « Descendez à X dans 3 arrêts ».

---

## 7. Spécificités iOS

**Fichiers :** `MainViewController.kt:52-58`, `LocationPermissionManager.ios.kt`, `LocationProvider.ios.kt`, `iosApp/Info.plist`

### NAV-43 — L'écran s'éteint pendant la navigation

`MainViewController.kt:52-58` : le callback `onNavigationModeChanged` ne fait **que** demander une permission. Aucun `UIApplication.sharedApplication.idleTimerDisabled = true`, aucun retour à `false` à l'arrêt. L'iPhone se verrouille au bout de 30 s de navigation. C'est l'équivalent iOS du `FLAG_KEEP_SCREEN_ON` Android, purement absent.

### NAV-44 — La permission « Always » est demandée sur le mauvais gestionnaire

`LocationPermissionManager.ios.kt:13-18` instancie **un second `LocationProvider`** (donc un second `CLLocationManager`), lui demande `requestAlwaysAuthorization()`, puis le conserve indéfiniment dans un singleton — sans jamais lui demander de position.

Le `LocationProvider` qui délivre réellement les fixes est celui créé dans `App.kt:317`. Son champ `usesAlwaysAuthorization` reste `false`, donc sa précision reste `kCLLocationAccuracyNearestTenMeters` : **`kCLLocationAccuracyBestForNavigation` n'est jamais appliqué** (`LocationProvider.ios.kt:36-40,54-58`). On demande une permission intrusive sans jamais en tirer bénéfice.

### NAV-45 — `UIBackgroundModes` sans `location`

`iosApp/Info.plist:42-46` ne déclare que `fetch` et `processing`. Sans `location` **et** sans `allowsBackgroundLocationUpdates = true`, iOS suspend les mises à jour dès que l'appli passe en arrière‑plan : la permission « Always » demandée en NAV-44 est **inutilisable**. Demander « Always » sans usage en arrière‑plan justifié est par ailleurs un motif classique de rejet App Store.

### NAV-46 — `hasBackgroundLocationPermission` ment

`LocationPermissionManager.ios.kt:20-24` renvoie `true` en dur, avec le commentaire « For now, assume we have permission if we requested it ». Tout appelant qui s'y fie prendra une mauvaise décision.

### NAV-47 — Aucun équivalent au service de premier plan

Pas de Live Activity, pas de tâche en arrière‑plan, pas de notification persistante, pas de restauration d'état. `NavigationModeStateStore.setNavigationActive` écrit un flag que **rien sur iOS ne relit**. En pratique, sortir de l'appli pendant la navigation la termine silencieusement.

---

## 8. Dette technique / performance

| ID | Constat | Référence |
|---|---|---|
| NAV-48 | `buildNavigationModeUiState` recalculé intégralement à chaque recomposition : parcours de tous les tronçons + tous les arrêts intermédiaires, deux fois (`findNearestJourneyStopCandidate` appelé en `:138` puis en `:273`). Aucun `remember`. | `App.kt:1117` |
| NAV-49 | `NavigationModeController.dispose()` ne remet pas le flag persistant à `false` → incohérence garantie entre le store et l'UI si la composition part. | `NavigationModeController.kt:78-81` |
| NAV-50 | `isSameJourneyLeg` compare par contenu : deux tronçons identiques dans un même trajet (aller‑retour en boucle) résolvent tous deux sur le premier index. | `NavigationModeState.kt:260-266` |
| NAV-51 | Grâce arbitraire de 5 s avant `serviceScope.cancel()` dans `onDestroy` — si la finalisation de `TripDetector` dépasse ce délai, la donnée est perdue silencieusement. | `NavigationModeForegroundService.kt:99-102` |
| NAV-52 | `isFinalizing` remis à `false` dans un `finally` **à l'intérieur** de la coroutine, mais le `return` anticipé ligne 197 (`detector == null`) laisse le drapeau bloqué à `true` : `finalizeTripDetector()` devient définitivement inopérant. | `NavigationModeForegroundService.kt:191-207` |
| NAV-53 | `registerReceiver` sans `RECEIVER_NOT_EXPORTED`. Exempté car `ACTION_SCREEN_ON` est un broadcast protégé, mais fragile avec `targetSdk = 36`. | `NavigationModeForegroundService.kt:290-295` |
| NAV-54 | Aucun test unitaire sur le mode navigation (`androidUnitTest` ne couvre que `ItineraryWalkUtils` et `WalkingRoute`). `buildNavigationModeUiState` est pourtant une fonction pure, trivialement testable. | `app/src/androidUnitTest/` |

---

## 9. Ordre de remédiation proposé

### Lot 1 — Débloquer (corrige les régressions visibles)

1. **NAV-01** — garder l'effet « sheet cachée → `closeItinerary()` » sur `!navigationState.isActive`. Débloque à lui seul : service de premier plan, écran allumé, affichage sur écran verrouillé, tracé sur la carte, filtrage des arrêts. Permet de supprimer le hack `hasAppliedFirstNavigationCallback`.
2. **NAV-03** — afficher l'overlay dès `isActive` avec un état « recherche du signal », + `BackHandler`.
3. **NAV-05 / NAV-06** — fiabiliser le flag persistant et protéger `startForeground` ; demander `POST_NOTIFICATIONS`.
4. **NAV-04** — détecter et présenter l'arrivée, arrêter la navigation.

### Lot 2 — Fiabiliser le guidage

5. **NAV-08** — remplacer `squaredDistance` par une distance corrigée en latitude (ou haversine).
6. **NAV-09** — ticker 1 s + `computeRemainingJourneySeconds` pour le compte à rebours.
7. **NAV-07 / NAV-10** — autoriser l'avancement temporel des tronçons, ajouter un seuil de distance et une hystérésis monotone sur le compte d'arrêts.
8. **NAV-02 / NAV-20 / NAV-21** — fusionner les deux `NavigationModeUiState`, supprimer le code et les modèles morts.
9. **NAV-14 / NAV-15** — vérifier la permission, désactiver le bouton pendant le calcul, déporter `calculateJourneyTrace` hors du thread principal.

### Lot 3 — UI et accessibilité

10. **NAV-16 / NAV-17** — insets système + hauteurs adaptatives (`heightIn` au lieu de `height`).
11. **NAV-39 / NAV-40 / NAV-41** — externaliser les chaînes, corriger les accents, ajouter `values-en` Android.
12. **NAV-22 / NAV-23 / NAV-26** — sémantique d'accessibilité, `liveRegion`, libellés.
13. **NAV-12 / NAV-18 / NAV-19** — cas piéton pur, ombres, harmonisation des rayons.

### Lot 4 — Expérience

14. **NAV-28 / NAV-29** — carte explorable + bouton « recentrer » + vue d'ensemble.
15. **NAV-30 / NAV-31** — cadence 1 s en navigation, interpolation, normalisation du cap.
16. **NAV-36 / NAV-37 / NAV-38** — confirmation d'arrêt, progression, détection hors‑itinéraire.
17. **NAV-11** — guidage piéton (la géométrie existe déjà).
18. **NAV-32 / NAV-33 / NAV-34 / NAV-35** — voix/haptique, temps réel, notification vivante, suppression du plein écran agressif.

### Lot 5 — iOS

19. **NAV-43** — `idleTimerDisabled`.
20. **NAV-44 / NAV-45 / NAV-46** — un seul `CLLocationManager`, `allowsBackgroundLocationUpdates`, `UIBackgroundModes: location`, ou renoncer à « Always ».
21. **NAV-47** — Live Activity ou, a minima, un arrêt propre et explicite en arrière‑plan.

### Lot 6 — Dette

22. **NAV-48 à NAV-54** — mémoïsation, cohérence du store, correction de `isFinalizing`, tests unitaires sur `buildNavigationModeUiState`.

---

## Note de méthode

Cet audit est **statique**. Les enchaînements critiques (notamment NAV-01 et NAV-03) ont été tracés ligne à ligne mais **n'ont pas été vérifiés sur appareil**. Avant de corriger, il est utile de confirmer NAV-01 en instrumentant `onNavigationModeChanged` (log de la valeur reçue) et en observant que le service démarre puis s'arrête dans la seconde.
