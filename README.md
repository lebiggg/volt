# ⚡ Volt — Privacy-first power suite for GrapheneOS

> **L'héritier FOSS de Greenify** — hibernation intelligente des apps, sans root, avec wake-on-push UnifiedPush.
> Conçu pour Android 14/15/16, testé sur GrapheneOS / Pixel 8.

[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![FOSS](https://img.shields.io/badge/100%25-FOSS-success.svg)]()
[![No Trackers](https://img.shields.io/badge/trackers-zero-success.svg)]()
[![GrapheneOS](https://img.shields.io/badge/optimized-GrapheneOS-yellow.svg)](https://grapheneos.org)
[![Status](https://img.shields.io/badge/status-alpha-orange.svg)]()

---

## ✨ Ce que fait Volt

### 1. 🔋 Hibernate — le module principal

Détecte automatiquement les apps inutilisées qui consomment de l'énergie, les met en hibernation graduée, et garantit que **les notifications continuent d'arriver** grâce au wake-on-push UnifiedPush.

- **Score de nocivité 0-100** par app, décomposé en 5 composantes transparentes (inactivité, ratio background, réveils CPU, réseau background, impact batterie). Aucun score opaque : tape sur un score, vois le détail.
- **Whitelist 5 couches** automatique : apps système, rôles critiques (téléphone, SMS, launcher), clavier actif, services d'accessibilité, gestionnaires de mots de passe, 2FA connus, VPN. Signal, Aegis, Bitwarden & co sont protégés par défaut.
- **3 niveaux** : SOFT (bucket RESTRICTED), MEDIUM (+ force-stop à l'écran éteint), HARD (+ sweep périodique 6 h).
- **Wake-on-push** : `FLAG_INCLUDE_STOPPED_PACKAGES` garantit la livraison du push même pour une app force-stoppée — ce que Greenify n'a jamais su faire proprement.

### 2. 📡 UnifiedPush Hub — *expérimental*

Connexion WebSocket persistante (TLS 1.3, backoff full-jitter, zéro log de payload) vers le serveur de push de votre choix (NextPush, Gotify, ntfy…).

> ⚠️ **État actuel** : le canal WebSocket et le routage fonctionnent, mais le protocole d'enregistrement UnifiedPush (`REGISTER`/`UNREGISTER` des apps clientes) n'est **pas encore implémenté**. Volt ne peut donc pas encore se déclarer comme distributeur système auprès d'autres apps. À considérer comme une démo technique, pas un distributeur production.

---

## ⚠️ Prérequis : Shizuku (lire avant d'installer)

**Sur Android 16, Volt nécessite [Shizuku](https://shizuku.rikka.app) pour fonctionner.** Ce n'est pas un choix de design — c'est une contrainte de l'OS : Google a verrouillé la permission `CHANGE_APP_IDLE_STATE` (`not a changeable permission type`), et elle n'est plus accordable par `adb pm grant`. La seule voie applicative restante pour manipuler les App Standby Buckets est Shizuku, qui s'exécute sous l'UID `shell`.

Shizuku ne nécessite **pas de root**, mais demande une mise en route unique (via le débogage sans fil ou un PC). Volt vous guide pas à pas dans l'onglet Tableau.

| Android | Shizuku |
|---|---|
| ≤ 15 | optionnel (voie réflexion `setAppStandbyBucket` encore possible) |
| **16+** | **requis** pour toute hibernation |

---

## 🚀 Installation (≈ 10 min, sans compiler)

### 1. Installer Volt
Télécharge le dernier APK depuis [**Releases**](../../releases) → installe-le (1 tap). Pas besoin d'Android Studio.

### 2. Installer + démarrer Shizuku
- Installe [Shizuku depuis F-Droid](https://f-droid.org/packages/moe.shizuku.privileged.api/)
- Démarre-le via **débogage sans fil** (sans PC) — [guide officiel](https://shizuku.rikka.app/guide/setup/)

### 3. Configurer Volt
- Ouvre Volt → onglet **Tableau** → accorde les accès demandés (l'onboarding s'adapte et disparaît une fois tout vert)
- Onglet **Hibernate** → autorise Volt dans Shizuku → choisis tes apps

### Build depuis les sources (développeurs)

```bash
git clone https://github.com/<user>/volt
cd volt
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔐 Permissions — transparence totale

Aucune télémétrie. Aucune connexion réseau hors le WebSocket UnifiedPush sortant que **vous** configurez.

| Permission | Type | Pourquoi |
|---|---|---|
| `INTERNET` | normale | WebSocket UnifiedPush sortant |
| `FOREGROUND_SERVICE_DATA_SYNC` | normale | Service réseau persistant |
| `POST_NOTIFICATIONS` | runtime | Notification de service minimale |
| `PACKAGE_USAGE_STATS` | special access | Score de nocivité (UsageStatsManager) |
| `QUERY_ALL_PACKAGES` | normale | Liste des apps installées |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | normale | Longévité du service |

L'action privilégiée (`am set-standby-bucket`) passe par **Shizuku**, pas par une permission Volt directe.

---

## 🏗️ Architecture

```
ui/screens/             Dashboard | Push | Hibernate (3 onglets)
ui/theme/               Material You dynamique (GrapheneOS-friendly)

BatteryCommandService   Foreground Service typé dataSync, FLAG_INCLUDE_STOPPED_PACKAGES
PushConnectionManager   OkHttp WebSocket, backoff full-jitter, zéro log de payload
ScreenStateReceiver     ACTION_SCREEN_ON/OFF → déclenche le sweep
VoltTileService         Quick Settings tile « Hiberner maintenant »

data/hibernation/
├── HibernationController       Façade unique (idempotente, thread-safe, Shizuku-first)
├── HibernationDecisionEngine   Score → niveau
├── HibernationRepository       Pont Entity ↔ Policy (Room)
├── HibernationWorker           Sweep périodique 6 h (WorkManager)
├── nocivity/NocivityScorer     Score 0-100 en 5 composantes
├── whitelist/WhitelistResolver Protection 5 couches
├── shizuku/ShizukuGateway      am set-standby-bucket / force-stop via Shizuku
└── persistence/                Room database

VoltContainer           Service locator minimaliste (pas de Hilt)
```

Toute manipulation de bucket passe par **un seul point** : `HibernationController`. C'est l'invariant central — un seul fichier à toucher si l'API système change.

---

## 🧪 Tests

```bash
./gradlew :app:testDebugUnitTest      # modèles purs (24 assertions)
./gradlew :app:connectedAndroidTest   # intégration (device requis)
```

### Validation manuelle (Pixel 8 / GrapheneOS / Android 16)

```bash
# Prérequis : Shizuku démarré et Volt autorisé.
# Vérifier qu'une app passe bien en bucket restricted après hibernation :
adb shell am get-standby-bucket org.fdroid.fdroid    # → 45 (RESTRICTED) après un SOFT dans Volt
```

---

## 🤝 Contribuer

### Ajouter une app à la whitelist curated

Une app 2FA / password manager / messagerie chiffrée manque dans [`CuratedWhitelist.kt`](app/src/main/java/com/tonnomdeved/volt/data/hibernation/whitelist/CuratedWhitelist.kt) ? Ouvre une PR avec le `packageName`, le lien du repo source, et la justification.

### Signaler une régression

Issue avec : la sortie de `adb shell dumpsys package <pkg> | grep flags`, le motif de protection affiché dans le bottom sheet, et le score décomposé.

---

## 🛡️ Modèle de menace

**Volt assume** : un device GrapheneOS à jour, un utilisateur prêt à configurer Shizuku une fois, un serveur UnifiedPush de confiance.

**Volt ne protège pas contre** : un attaquant physique avec accès ADB, un MITM réseau sans certificate pinning (à activer manuellement dans `PushConnectionManager`), une app qui se root elle-même (hors périmètre — voir Auditor de GrapheneOS).

---

## 🗺️ Roadmap

- [x] Hibernate — moteur, whitelist, scoring, UI, Shizuku
- [x] Quick Settings tile
- [ ] UnifiedPush — protocole `REGISTER`/`UNREGISTER` complet
- [ ] Statistiques d'économie batterie (mAh/jour estimés)
- [ ] Forensics — analyseur de wakelocks nocturnes
- [ ] Internationalisation (en/fr)
- [ ] Démarrage auto au boot (opt-in)

---

## 📜 Licence

GPL-3.0 — voir [LICENSE](LICENSE).

---

*Made with anger at battery drain and respect for your privacy.*
