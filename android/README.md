# Fun Games — Android Application (Deliverable B)

Distributed Systems, Spring 2025–2026
Team 3230096 / 3230188 / 3230064

Android frontend for the online games system. It connects to the **Master**
exclusively over **TCP Sockets**, sends search filters, places bets, tops up
balance, rates games, and receives live jackpot notifications through a
broadcast channel.

---

## Project structure

```
android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/games/                          # Game JSON files (for the manager)
│       │   ├── lucky_slots.json
│       │   ├── lucky_wheel.json
│       │   ├── mega_poker.json
│       │   └── roulette_x.json
│       ├── java/com/funGames/app/
│       │   ├── net/
│       │   │   ├── Protocol.java                  # Exact copy of shared.Protocol
│       │   │   └── MasterClient.java              # Asynchronous TCP client (Thread + Handler)
│       │   ├── model/
│       │   │   └── Game.java                      # Transport format identical to the backend
│       │   ├── util/
│       │   │   ├── Session.java                   # In-memory session singleton
│       │   │   ├── SessionPrefs.java              # SharedPreferences (host, port, role)
│       │   │   ├── PlayerProfile.java             # Local stats (XP, wins, bets)
│       │   │   ├── BetHistory.java                # Session bet history
│       │   │   ├── DailyBonus.java                # Daily bonus (+100 FUN/day)
│       │   │   ├── SoundManager.java              # UI sounds
│       │   │   ├── ThemeManager.java              # Theme switching
│       │   │   ├── SearchResults.java             # Search result wrapper
│       │   │   └── Validators.java                # Regex ID validation
│       │   └── ui/
│       │       ├── LoginActivity.java             # Player / Manager login
│       │       ├── MainActivity.java              # Lobby: filters + results + live ticker
│       │       ├── GamePlayActivity.java          # Game screen
│       │       ├── ResultsActivity.java           # Bet result
│       │       ├── StatsActivity.java             # Player statistics
│       │       ├── ManagerActivity.java           # Manager dashboard (Android UI)
│       │       ├── GamesAdapter.java              # RecyclerView adapter for the game list
│       │       ├── games/
│       │       │   ├── SlotMachineView.java       # Custom view — 3-reel slots
│       │       │   ├── RouletteView.java          # Custom view — European roulette
│       │       │   ├── PokerView.java             # Custom view — video poker
│       │       │   └── LuckyWheelView.java        # Custom view — spinning wheel
│       │       ├── AchievementToastView.java      # Achievement toast
│       │       ├── AnimatedBalanceTextView.java   # Animated balance counter
│       │       ├── AnimatedStarRatingView.java    # Animated star rating
│       │       ├── BetHistoryView.java            # Mini history chart
│       │       ├── CasinoBackgroundView.java      # Animated background
│       │       ├── TealCasinoBackgroundView.java  # Alternative background
│       │       ├── ConnectionStatusView.java      # Connection indicator
│       │       ├── LiveTickerView.java            # Scrolling live bets bar
│       │       ├── NaturalLanguageSearchView.java # AI-powered search bar
│       │       ├── ParticleSystem.java            # Particle effects (jackpot)
│       │       ├── ShakeErrorView.java            # Shake animation for errors
│       │       ├── SkeletonLoadingView.java       # Skeleton loading state
│       │       ├── StatsChartView.java            # Bar chart for manager stats
│       │       ├── SwipeActionCallback.java       # Swipe-to-action on cards
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
| Master (main) | 5000 |
| Master broadcast (live ticker / jackpot) | **masterPort + 10** = 5010 |

The broadcast port is **not entered by the user** — it is computed
automatically as `masterPort + 10` inside `LoginActivity`.

---

## Assignment requirements covered

| Requirement | Implementation |
|----------|-----------|
| TCP Sockets exclusively | `MasterClient` uses `ObjectOutputStream.writeUTF` / `ObjectInputStream.readUTF` |
| Asynchronous communication | Every network call runs on a background `Thread`; results are posted back to the UI thread via `Handler(Looper.getMainLooper())` |
| `search()` | Sends filters, receives a stream of `MAP_RESULT` rows + `END` |
| `play()` | Sends `PLAY~~playerId~~gameName~~bet`, receives `OK~~result` |
| `addBalance()` | Sends `ADD_BALANCE~~playerId~~amount` |
| Rating (1–5 stars) | Sends `RATE_GAME~~playerId~~gameName~~stars` |
| Live jackpot notifications | Persistent broadcast socket (port 5010), background thread, `SUBSCRIBE~~playerId` |

---

## How to run it

### 1. Start the backend

From the project root folder:

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

# 5. Manager — add games before testing
java -cp bin manager.ManagerApp localhost 5000 AK1234
```

**Windows:** `start_all.bat` opens each component in its own terminal.

### 2. Open in Android Studio

1. File → Open → select the `android/` folder
2. Wait for the Gradle sync
3. Build → Make Project
4. Run on an emulator or a real device

### 3. Login

| Field | Player | Manager |
|-------|--------|---------|
| ID | 2 uppercase + 4 digits (e.g. `AN1234`) | 2 uppercase + 4 digits (e.g. `AK1234`) |
| Balance | Initial FUN amount | — |
| Master host | `10.0.2.2` (emulator) or the device's LAN IP | same |
| Master port | `5000` | `5000` |

> **Emulator:** `10.0.2.2` is the alias for the host machine's `localhost`.
> On a real device, use the PC's LAN IP (e.g. `192.168.1.X`) and make sure the
> firewall allows ports 5000 and 5010.

---

## Using the app

### Search

1. Pick **Min Stars** (ALL / 1★ – 5★)
2. Pick **Bet Category** (ANY / $ / $$ / $$$)
3. Pick **Risk Level** (ANY / LOW / MED / HIGH)
4. Tap **SEARCH GAMES** — results arrive asynchronously

### Play

1. Tap **PLAY** on a game card
2. Choose a bet amount (within MinBet – MaxBet)
3. The result (WIN / PARTIAL / LOSS / JACKPOT) appears on screen
4. The balance updates automatically

### Rate

Tap **RATE** on a card → choose 1–5 stars → the rating is sent to the Master.

### Add Balance

Tap **+ ADD** on the wallet pod → enter an amount → the Master is updated.

### Manager Dashboard (Android)

If you log in as a Manager, `ManagerActivity` opens with 4 tabs:

| Tab | Function |
|-----|-----------|
| GAMES | Add / Remove / Change Risk |
| BY PROVIDER | MapReduce stats per provider + bar chart |
| BY PLAYER | MapReduce stats per player + leaderboard |
| SYSTEM | Worker health check + Stress test |

---

## Communication protocol

All messages use `writeUTF` / `readUTF` over `ObjectOutputStream` /
`ObjectInputStream`. Fields are separated by `~~`.

| Action | Request | Response |
|----------|---------|----------|
| SEARCH | `SEARCH~~minStars~~betCat~~risk` | Stream: `MAP_RESULT~~<game>` … `END` |
| PLAY | `PLAY~~playerId~~gameName~~bet` | `OK~~<result>` or `ERROR~~<msg>` |
| ADD_BALANCE | `ADD_BALANCE~~playerId~~amount` | `OK~~…` |
| RATE_GAME | `RATE_GAME~~playerId~~gameName~~stars` | `OK~~…` or `ERROR~~<msg>` |
| GET_BALANCE | `GET_BALANCE~~playerId` | `OK~~<balance>` |
| SUBSCRIBE | `SUBSCRIBE~~playerId` (broadcast port) | persistent — push notifications |

---

## Troubleshooting

**"Network error: failed to connect"**
The Master is not running, or the host/port is wrong. On an emulator use
`10.0.2.2` — NOT `localhost` / `127.0.0.1`.

**No games appear**
The Manager must have added at least one game. Try the JSONs in the backend's
`sample_data/` or the assets in `android/app/src/main/assets/games/`.

**"Bet must be between X and Y"**
The bet amount is outside the game's MinBet–MaxBet range.

**Player/Manager ID is rejected**
It must be exactly 2 uppercase letters + 4 digits (e.g. `AN1234`).

**No jackpot notifications received**
Make sure the firewall also allows the **broadcast port** (masterPort + 10 = 5010).
