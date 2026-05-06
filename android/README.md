# Fun Games — Android Application (Παραδοτέο Β)

Κατανεμημένα Συστήματα, Εαρινό 2025–2026
Ομάδα 3230096 / 3230188 / 3230064

Android frontend για το σύστημα online παιχνιδιών του Α' μέρους. Συνδέεται
στον **Master** μέσω **TCP Sockets** (αποκλειστικά), στέλνει φίλτρα, κάνει
ποντάρισμα, προσθέτει balance και βαθμολογεί παιχνίδια.

---

## Δομή του project

```
FunGamesApp/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/funGames/app/
│       │   ├── net/
│       │   │   ├── Protocol.java          # Ακριβές αντίγραφο του shared.Protocol
│       │   │   └── MasterClient.java      # Ασύγχρονος TCP client (Threads + Handler)
│       │   ├── model/
│       │   │   └── Game.java              # Ίδιο transport format με το backend
│       │   ├── util/
│       │   │   ├── Session.java           # In-memory session (singleton)
│       │   │   └── Validators.java
│       │   └── ui/
│       │       ├── LoginActivity.java     # Player ID + balance + Master host/port
│       │       ├── MainActivity.java      # Φίλτρα + αποτελέσματα + play/rate/addBalance
│       │       └── GamesAdapter.java      # RecyclerView adapter
│       └── res/                            # Layouts, drawables, values
├── build.gradle                            # Top-level
├── settings.gradle
├── gradle.properties
└── README.md
```

---

## Απαιτήσεις που καλύπτονται

- **TCP Sockets only** — καμία HTTP/REST επικοινωνία. Χρήση
  `ObjectOutputStream.writeUTF` / `ObjectInputStream.readUTF` όπως στο
  backend `DummyPlayer`.
- **Ασύγχρονη επικοινωνία** — κάθε network call στον `MasterClient`
  τρέχει σε δικό του `Thread`, τα results ποστάρονται πίσω στο
  main (UI) thread μέσω `Handler(Looper.getMainLooper())`. Το UI
  παραμένει διαδραστικό σε όλη τη διάρκεια των requests.
- **search() / play() / addBalance() / rateGame()** — όπως ζητάει η
  εκφώνηση, με τα ίδια payload formats που χρησιμοποιεί και ο DummyPlayer.
- **Ίδιο wire protocol με το backend** — μέσω των αντιγραμμένων
  `Protocol.java` και `Game.java` (σταθερές `SEP`, `SEARCH`, `PLAY`,
  κτλ. και `fromTransportString()` / 10 πεδία `~~` διαχωρισμένα).
- **In-memory only** — καμία βάση, κανένα shared prefs, κανένα SQLite.
  Ο `Session` singleton ζει για το lifetime του Android process.

---

## Πώς να το τρέξετε

### 1. Άνοιγμα στο Android Studio

1. Άνοιγμα → Open an Existing Project → επιλογή του φακέλου `FunGamesApp/`
2. Περιμένετε να κάνει Gradle sync (κατεβάζει τα dependencies)
3. Build → Make Project

### 2. Εκκίνηση του backend (Α' μέρος)

Από τον φάκελο του Α' παραδοτέου, ξεκινήστε τα components με τη σειρά:

```bash
# 1. Secure Random Generator
java -cp bin srg.SecureRandomGenerator 6000

# 2. Reducer
java -cp bin reducer.Reducer 7000

# 3. Workers (ένας ή περισσότεροι)
java -cp bin worker.WorkerNode 0 5001 localhost 6000 localhost 7000
java -cp bin worker.WorkerNode 1 5002 localhost 6000 localhost 7000

# 4. Master
java -cp bin master.Master 5000 localhost 7000 localhost:5001 localhost:5002

# 5. Manager — προσθέστε μερικά παιχνίδια πριν τεστάρετε το app
java -cp bin manager.ManagerApp localhost 5000
```

### 3. Εκκίνηση του Android app

Τρέξτε σε **Android Emulator** ή σε πραγματική συσκευή.

Στην οθόνη login:

| Πεδίο            | Τιμή (παράδειγμα)            |
| ---------------- | ---------------------------- |
| Player ID        | `AN1234` (2 κεφαλαία + 4 ψηφία) |
| Starting balance | `100`                        |
| Master host      | `10.0.2.2` (emulator → host) ή IP του μηχανήματος |
| Master port      | `5000`                       |

**Σημαντικό για τον emulator:** το `10.0.2.2` είναι το alias του Android
emulator για `localhost` του host machine σας. Αν τρέχετε σε πραγματική
συσκευή, βρείτε την IP του PC στο LAN (π.χ. `192.168.1.X`) και
χρησιμοποιήστε αυτή — και βεβαιωθείτε ότι το firewall επιτρέπει συνδέσεις
στο port 5000.

---

## Χρήση της εφαρμογής

### Search

1. Επιλέξτε ελάχιστα αστέρια (0–5, άδειο = 0)
2. Επιλέξτε bet category chip (ANY / $ / $$ / $$$)
3. Επιλέξτε risk chip (ANY / LOW / MED / HIGH)
4. Πατήστε **Search**
5. Τα αποτελέσματα εμφανίζονται σαν κάρτες με game info, risk pill,
   jackpot multiplier και bet range.

### Play

1. Στην κάρτα ενός παιχνιδιού πατήστε **PLAY**
2. Εμφανίζεται dialog με τα όρια πονταρίσματος και το τρέχον balance
3. Εισάγετε ποσό και πατήστε **Place bet**
4. Το αποτέλεσμα (win / partial / loss / jackpot) εμφανίζεται σε popup
5. Το balance ενημερώνεται αυτόματα στο top bar

### Rate

1. Πατήστε **Rate** σε μια κάρτα παιχνιδιού
2. Πατήστε τα αστέρια (1–5)
3. Submit → η βαθμολογία αποστέλλεται στον Master

### Add Balance

1. Πατήστε **+ FUN** στο top bar
2. Εισάγετε ποσό → Add
3. Το balance ενημερώνεται και ο Master ειδοποιείται

---

## Πρωτόκολλο επικοινωνίας (για reference)

Όλα τα μηνύματα πάνε με `writeUTF` / `readUTF` πάνω από
`Object[Input|Output]Stream`. Τα πεδία χωρίζονται με `~~`.

| Ενέργεια    | Request                             | Response                                   |
| ----------- | ----------------------------------- | ------------------------------------------ |
| SEARCH      | `SEARCH~~minStars~~betCat~~risk`    | Stream από `MAP_RESULT~~<10 πεδία game>` και `END` |
| PLAY        | `PLAY~~playerId~~gameName~~bet`     | `OK~~<playerResult>` ή `ERROR~~<msg>`     |
| ADD_BALANCE | `ADD_BALANCE~~playerId~~amount`     | `OK~~Balance updated`                      |
| RATE_GAME   | `RATE_GAME~~playerId~~gameName~~stars` | `OK~~Rating updated` ή `ERROR~~<msg>`  |

Αν το αλλάξετε στο backend, πρέπει να ενημερώσετε και το
`net/Protocol.java` + `model/Game.java` στο app.

---

## Troubleshooting

**"Network error: failed to connect"**
Ο Master δεν τρέχει, ή λάθος host/port, ή το firewall μπλοκάρει. Σε
emulator χρησιμοποιήστε `10.0.2.2` ΟΧΙ `localhost` / `127.0.0.1`.

**"Bet must be between X and Y"**
Το ποντάρισμά σας είναι εκτός του range που δηλώνει το παιχνίδι.

**Player ID δεν γίνεται δεκτό**
Πρέπει να είναι ακριβώς 2 κεφαλαία γράμματα ακολουθούμενα από 4 ψηφία
(π.χ. `AN1234`, `XY9999`). Ο DummyPlayer του Α' μέρους έχει το ίδιο regex.

**Η εφαρμογή δεν βλέπει κανένα παιχνίδι**
Πρώτα πρέπει ο Manager να έχει προσθέσει τουλάχιστον ένα παιχνίδι μέσω
του console app του Α' μέρους. Δοκιμάστε με τα sample JSONs στο
`sample_data/` του backend.

---

## Σχεδιαστικές αποφάσεις

- **Dark neon-casino palette**: navy + gold για τα CTAs + pink/mint
  accents για win/loss. Επιλογή συνεπής με το casino theme.
- **Chip-based filters** αντί για dropdowns: γρηγορότερα στο κινητό,
  λιγότερα taps.
- **In-memory balance, subtract-before-return**: όπως και ο DummyPlayer
  του Α' μέρους, κρατάμε balance τοπικά και το ενημερώνουμε όταν
  έρχεται το playerResult από τον Master.
- **Callbacks, όχι LiveData / Coroutines**: ο κώδικας μένει απλός Java
  και αξιοποιεί μόνο core Android (Handler + Thread), όπως απαιτεί το
  πνεύμα της εργασίας που απαγορεύει ready-made concurrency utilities
  στο backend.
