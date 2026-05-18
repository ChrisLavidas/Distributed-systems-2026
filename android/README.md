# Fun Games — Android Application (Παραδοτέο Β)

Κατανεμημένα Συστήματα, Εαρινό 2025–2026  
Ομάδα 3230096 / 3230188 / 3230064

Android frontend για το σύστημα online παιχνιδιών. Συνδέεται στον **Master**
μέσω **TCP Sockets** αποκλειστικά, στέλνει φίλτρα, κάνει ποντάρισμα,
προσθέτει balance, βαθμολογεί παιχνίδια και λαμβάνει live jackpot
notifications μέσω broadcast channel.

---

## Δομή του project

```
android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/games/                          # JSON αρχεία παιχνιδιών (για manager)
│       │   ├── lucky_slots.json
│       │   ├── lucky_wheel.json
│       │   ├── mega_poker.json
│       │   └── roulette_x.json
│       ├── java/com/funGames/app/
│       │   ├── net/
│       │   │   ├── Protocol.java                  # Ακριβές αντίγραφο shared.Protocol
│       │   │   └── MasterClient.java              # Ασύγχρονος TCP client (Thread + Handler)
│       │   ├── model/
│       │   │   └── Game.java                      # Transport format ίδιο με backend
│       │   ├── util/
│       │   │   ├── Session.java                   # In-memory session singleton
│       │   │   ├── SessionPrefs.java              # SharedPreferences (host, port, role)
│       │   │   ├── PlayerProfile.java             # Stats τοπικά (XP, wins, bets)
│       │   │   ├── BetHistory.java                # Ιστορικό στοιχημάτων session
│       │   │   ├── DailyBonus.java                # Daily bonus (+100 FUN/ημέρα)
│       │   │   ├── SoundManager.java              # Ήχοι UI
│       │   │   ├── ThemeManager.java              # Theme switching
│       │   │   ├── SearchResults.java             # Αποτέλεσμα search (wrapper)
│       │   │   └── Validators.java                # Regex ID validation
│       │   └── ui/
│       │       ├── LoginActivity.java             # Player / Manager login
│       │       ├── MainActivity.java              # Lobby: φίλτρα + αποτελέσματα + live ticker
│       │       ├── GamePlayActivity.java          # Οθόνη παιχνιδιού
│       │       ├── ResultsActivity.java           # Αποτέλεσμα στοιχήματος
│       │       ├── StatsActivity.java             # Στατιστικά παίκτη
│       │       ├── ManagerActivity.java           # Manager dashboard (Android UI)
│       │       ├── GamesAdapter.java              # RecyclerView adapter για λίστα παιχνιδιών
│       │       ├── games/
│       │       │   ├── SlotMachineView.java       # Custom view — 3-reel slots
│       │       │   ├── RouletteView.java          # Custom view — European roulette
│       │       │   ├── PokerView.java             # Custom view — video poker
│       │       │   └── LuckyWheelView.java        # Custom view — spinning wheel
│       │       ├── AchievementToastView.java      # Toast επίτευξης
│       │       ├── AnimatedBalanceTextView.java   # Animated balance counter
│       │       ├── AnimatedStarRatingView.java    # Animated star rating
│       │       ├── BetHistoryView.java            # Mini chart ιστορικού
│       │       ├── CasinoBackgroundView.java      # Animated background
│       │       ├── TealCasinoBackgroundView.java  # Εναλλακτικό background
│       │       ├── ConnectionStatusView.java      # Indicator σύνδεσης
│       │       ├── LiveTickerView.java            # Scrolling live bets bar
│       │       ├── NaturalLanguageSearchView.java # AI-powered search bar
│       │       ├── ParticleSystem.java            # Particle effects (jackpot)
│       │       ├── ShakeErrorView.java            # Shake animation για errors
│       │       ├── SkeletonLoadingView.java       # Skeleton loading state
│       │       ├── StatsChartView.java            # Bar chart για manager stats
│       │       ├── SwipeActionCallback.java       # Swipe-to-action σε κάρτες
│       │       └── WinStreakBannerView.java        # Win streak banner
│       └── res/                                   # Layouts, drawables, anim, values
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

---

## Ports

| | Port |
|--|------|
| Master (κύριο) | 5000 |
| Master broadcast (live ticker / jackpot) | **masterPort + 10** = 5010 |

Το broadcast port **δεν εισάγεται από τον χρήστη** — υπολογίζεται αυτόματα
ως `masterPort + 10` μέσα στο `LoginActivity`.

---

## Απαιτήσεις εκφώνησης που καλύπτονται

| Απαίτηση | Υλοποίηση |
|----------|-----------|
| TCP Sockets αποκλειστικά | `MasterClient` χρησιμοποιεί `ObjectOutputStream.writeUTF` / `ObjectInputStream.readUTF` |
| Ασύγχρονη επικοινωνία | Κάθε network call τρέχει σε background `Thread`, αποτελέσματα ποστάρονται στο UI thread μέσω `Handler(Looper.getMainLooper())` |
| `search()` | Στέλνει φίλτρα, λαμβάνει stream από `MAP_RESULT` rows + `END` |
| `play()` | Στέλνει `PLAY~~playerId~~gameName~~bet`, λαμβάνει `OK~~result` |
| `addBalance()` | Στέλνει `ADD_BALANCE~~playerId~~amount` |
| Rating (1–5 αστέρια) | Στέλνει `RATE_GAME~~playerId~~gameName~~stars` |
| Live jackpot notifications | Persistent broadcast socket (port 5010), background thread, `SUBSCRIBE~~playerId` |

---

## Πώς να το τρέξετε

### 1. Εκκίνηση backend

Από τον root φάκελο του project:

```bash
# Compile
find src -name "*.java" > sources.txt && javac -d bin @sources.txt

# 1. SRG
java -cp bin srg.SecureRandomGenerator 6000

# 2. Reducer
java -cp bin reducer.Reducer 7000

# 3. Workers
java -cp bin worker.WorkerNode 0 5001 localhost 6000 localhost 7000
java -cp bin worker.WorkerNode 1 5002 localhost 6000 localhost 7000

# 4. Master  (broadcast = 5010 = masterPort + 10)
java -cp bin master.Master 5000 5010 localhost 7000 localhost:5001 localhost:5002

# 5. Manager — προσθέστε παιχνίδια πριν τεστάρετε
java -cp bin manager.ManagerApp localhost 5000 AK1234
```

**Windows:** `start_all.bat` ανοίγει κάθε component σε δικό του terminal.

### 2. Άνοιγμα στο Android Studio

1. File → Open → επιλογή του φακέλου `android/`
2. Αναμονή Gradle sync
3. Build → Make Project
4. Run σε emulator ή πραγματική συσκευή

### 3. Login

| Πεδίο | Player | Manager |
|-------|--------|---------|
| ID | 2 κεφαλαία + 4 ψηφία (π.χ. `AN1234`) | 2 κεφαλαία + 4 ψηφία (π.χ. `AK1234`) |
| Balance | Αρχικό ποσό FUN | — |
| Master host | `10.0.2.2` (emulator) ή LAN IP συσκευής | ίδιο |
| Master port | `5000` | `5000` |

> **Emulator:** το `10.0.2.2` είναι το alias για το `localhost` του host
> machine. Σε πραγματική συσκευή χρησιμοποιήστε την LAN IP του PC
> (π.χ. `192.168.1.X`) και βεβαιωθείτε ότι το firewall επιτρέπει port 5000 και 5010.

---

## Χρήση της εφαρμογής

### Search

1. Επιλέξτε **Min Stars** (ALL / 1★ – 5★)
2. Επιλέξτε **Bet Category** (ANY / $ / $$ / $$$)
3. Επιλέξτε **Risk Level** (ANY / LOW / MED / HIGH)
4. Πατήστε **SEARCH GAMES** — τα αποτελέσματα έρχονται ασύγχρονα

### Play

1. Πατήστε **PLAY** σε μια κάρτα παιχνιδιού
2. Επιλέξτε ποσό στοιχήματος (εντός MinBet – MaxBet)
3. Το αποτέλεσμα (WIN / PARTIAL / LOSS / JACKPOT) εμφανίζεται στην οθόνη
4. Το balance ενημερώνεται αυτόματα

### Rate

Πατήστε **RATE** σε μια κάρτα → επιλέξτε 1–5 αστέρια → η βαθμολογία αποστέλλεται στον Master.

### Add Balance

Πατήστε **+ ADD** στο wallet pod → εισάγετε ποσό → το Master ενημερώνεται.

### Manager Dashboard (Android)

Αν συνδεθείτε ως Manager ανοίγει το `ManagerActivity` με 4 tabs:

| Tab | Λειτουργία |
|-----|-----------|
| GAMES | Add / Remove / Change Risk |
| BY PROVIDER | MapReduce stats ανά provider + bar chart |
| BY PLAYER | MapReduce stats ανά παίκτη + leaderboard |
| SYSTEM | Worker health check + Stress test |

---

## Πρωτόκολλο επικοινωνίας

Όλα τα μηνύματα με `writeUTF` / `readUTF` πάνω από `ObjectOutputStream` /
`ObjectInputStream`. Πεδία χωρίζονται με `~~`.

| Ενέργεια | Request | Response |
|----------|---------|----------|
| SEARCH | `SEARCH~~minStars~~betCat~~risk` | Stream: `MAP_RESULT~~<game>` … `END` |
| PLAY | `PLAY~~playerId~~gameName~~bet` | `OK~~<result>` ή `ERROR~~<msg>` |
| ADD_BALANCE | `ADD_BALANCE~~playerId~~amount` | `OK~~…` |
| RATE_GAME | `RATE_GAME~~playerId~~gameName~~stars` | `OK~~…` ή `ERROR~~<msg>` |
| GET_BALANCE | `GET_BALANCE~~playerId` | `OK~~<balance>` |
| SUBSCRIBE | `SUBSCRIBE~~playerId` (broadcast port) | persistent — push notifications |

---

## Troubleshooting

**"Network error: failed to connect"**  
Ο Master δεν τρέχει, ή λάθος host/port. Σε emulator χρησιμοποιήστε
`10.0.2.2` — ΟΧΙ `localhost` / `127.0.0.1`.

**Δεν εμφανίζονται παιχνίδια**  
Ο Manager πρέπει να έχει προσθέσει τουλάχιστον ένα παιχνίδι. Δοκιμάστε
τα JSONs στο `sample_data/` του backend ή τα assets στο `android/app/src/main/assets/games/`.

**"Bet must be between X and Y"**  
Το ποσό στοιχήματος είναι εκτός ορίων MinBet–MaxBet του παιχνιδιού.

**Player/Manager ID δεν γίνεται δεκτό**  
Πρέπει να είναι ακριβώς 2 κεφαλαία γράμματα + 4 ψηφία (π.χ. `AN1234`).

**Δεν λαμβάνονται jackpot notifications**  
Βεβαιωθείτε ότι το firewall επιτρέπει και το **broadcast port** (masterPort + 10 = 5010).
