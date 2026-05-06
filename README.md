# Κατανεμημένα Συστήματα — Παραδοτέο Β
### Ομάδα: 3230096 / 3230188 / 3230064
### Εαρινό Εξάμηνο 2025–2026

Πλατφόρμα online παιχνιδιών με κατανεμημένο backend (Master / Workers /
Reducer / SRG) και Android frontend για τους παίκτες.

---

## Δομή του project

```
3230096_3230188_3230064/
├── src/                         # Backend κώδικας (Java)
│   ├── master/                    Master node (TCP server, δρομολόγηση H(gameName))
│   ├── worker/                    Worker nodes (in-memory games, bet processing)
│   ├── reducer/                   MapReduce reducer για provider/player stats
│   ├── srg/                       Secure Random Generator (TCP, sha256 integrity)
│   ├── manager/                   Console app για τον manager
│   ├── player/                    DummyPlayer (για testing — έχει αντικατασταθεί
│   │                              από το Android app)
│   └── shared/                    Game, Protocol, BetRecord, JsonGameParser
│
├── android/                     # Android app (Παραδοτέο Β)
│   ├── app/src/main/java/...      LoginActivity, MainActivity, MasterClient,
│   │                              GamesAdapter, Session, Validators
│   ├── app/src/main/res/...       Layouts, drawables, theme (dark/gold)
│   ├── build.gradle, settings.gradle, gradle.properties
│   └── README.md                  Οδηγίες Android
│
├── sample_data/                 # Παραδείγματα JSON για προσθήκη παιχνιδιών
│   ├── lucky_slots.json
│   ├── lucky_wheel.json
│   ├── mega_poker.json
│   └── roulette_x.json
│
├── build_and_run.sh             # Compile helper για τον backend
└── README.md                    # Αυτό το αρχείο
```

---

## Τι καλύπτει το Παραδοτέο Β

Όλες οι απαιτήσεις του Α' μέρους, συν:

- **Android application** (αντικαθιστά τον DummyPlayer): Login, Search με
  φίλτρα (min stars, bet category, risk level), Play, Rate, Add Balance.
  Επικοινωνία αποκλειστικά μέσω TCP Sockets, ασύγχρονα (Threads + Handler)
  ώστε η εφαρμογή να παραμένει διαδραστική.
- **Aggregation queries** στον manager: συνολικά κέρδη/ζημιές ανά
  Provider και ανά Player, υπολογισμένα με MapReduce (Workers →
  Reducer → Master).

---

## Εκκίνηση — Backend

### Compile
```bash
./build_and_run.sh        # Κάνει compile και τυπώνει τα commands
```
ή χειροκίνητα:
```bash
find src -name "*.java" > sources.txt
javac -d bin @sources.txt
```

### Run (σε διαφορετικά terminals, με αυτή τη σειρά):

```bash
# 1. Secure Random Generator
java -cp bin srg.SecureRandomGenerator 6000

# 2. Reducer
java -cp bin reducer.Reducer 7000

# 3. Workers (δυναμικός αριθμός — προσθέστε όσους θέλετε)
java -cp bin worker.WorkerNode 0 5001 localhost 6000 localhost 7000
java -cp bin worker.WorkerNode 1 5002 localhost 6000 localhost 7000

# 4. Master
java -cp bin master.Master 5000 localhost 7000 localhost:5001 localhost:5002

# 5. Manager console (προσθέστε παιχνίδια + δείτε stats)
java -cp bin manager.ManagerApp localhost 5000
```

### Προσθήκη παιχνιδιών
Από τον Manager console, επιλέξτε `1. Add game` και δώστε path σε ένα από
τα JSON αρχεία στο `sample_data/`.

---

## Εκκίνηση — Android App

### 1. Άνοιγμα στο Android Studio
- Open an Existing Project → επιλέξτε τον φάκελο `android/`
- Περιμένετε Gradle sync
- Build → Make Project

### 2. Run σε emulator ή πραγματική συσκευή

Στην οθόνη login:

| Πεδίο            | Τιμή (emulator) | Τιμή (πραγματική συσκευή) |
| ---------------- | --------------- | -------------------------- |
| Player ID        | `AN1234`        | `AN1234`                   |
| Starting balance | `100`           | `100`                      |
| Master host      | `10.0.2.2`      | IP του PC στο LAN (π.χ. `192.168.1.10`) |
| Master port      | `5000`          | `5000`                     |

> Ο emulator βλέπει το host machine ως `10.0.2.2` (όχι `localhost`).
> Για πραγματική συσκευή, βεβαιωθείτε ότι το firewall επιτρέπει
> εισερχόμενες συνδέσεις στο 5000.

---

## Αρχιτεκτονική επικοινωνίας

```
        ┌──────────────┐
        │  Android App │  (Παραδοτέο Β)
        └──────┬───────┘
               │ TCP (φίλτρα, play, rate, addBalance)
               ▼
        ┌──────────────┐        TCP         ┌──────────┐
        │    Master    │ ─────────────────► │ Reducer  │
        └──────┬───────┘                    └──────────┘
        TCP ┌──┼──┐ TCP                           ▲
            ▼  ▼  ▼                               │
         ┌──┐┌──┐┌──┐                     map results (TCP)
         │W1││W2││Wn│  ─────────────────────────┘
         └┬─┘└──┘└──┘
          │ TCP (random numbers + sha256 integrity check)
          ▼
        ┌──────────────┐
        │     SRG      │
        └──────────────┘
```

Όλες οι επικοινωνίες γίνονται με raw TCP sockets (`ServerSocket` +
`Socket` + `ObjectOutputStream`/`ObjectInputStream.writeUTF/readUTF`).
Καμία έτοιμη βιβλιοθήκη HTTP/REST, καμία DB — όλα in-memory.

---

## Έλεγχοι που μπορείτε να κάνετε

1. **Concurrent bets**: τρέξτε δύο instances του DummyPlayer (ή ένα
   DummyPlayer + Android app) και πονταρίστε ταυτόχρονα στο ίδιο
   παιχνίδι. Ο Worker χρησιμοποιεί `synchronized` για το shared state.

2. **Dynamic workers**: τρέξτε τον Master με 1, 2, 3, ή περισσότερους
   Workers — απλά αλλάξτε τα arguments. Τα παιχνίδια κατανέμονται με
   `H(GameName) mod N`.

3. **Provider stats (MapReduce)**: από τον Manager, option 4 →
   εμφανίζει ανά provider τα κέρδη/ζημιές ανά game και το total, αφού
   πρώτα τρέξει map phase (όλοι οι Workers στέλνουν δεδομένα στον
   Reducer) και έπειτα reduce phase (Master διαβάζει από Reducer).

4. **Player stats**: option 5 → ανά player το συνολικό P/L.

---

## Παρατηρήσεις υλοποίησης

- **Συγχρονισμός**: αποκλειστικά με `synchronized` / `wait` / `notify`.
  Καμία χρήση `java.util.concurrent` utilities (ακολουθεί τον περιορισμό
  της εκφώνησης).
- **Memory-only storage**: τα παιχνίδια αποθηκεύονται μόνο στη μνήμη των
  Workers. Αν πέσει ένας Worker, τα δεδομένα του χάνονται (το bonus
  active replication δεν έχει υλοποιηθεί σε αυτό το παραδοτέο — ομάδα 3
  ατόμων).
- **SRG integrity**: ο Worker και ο SRG μοιράζονται ένα κοινό secret
  `HashKey` ανά παιχνίδι. Ο SRG στέλνει `(number, sha256(number+secret))`
  και ο Worker επαληθεύει.

---

## Για λεπτομέρειες του Android app

Δείτε το `android/README.md` — έχει screenshots, troubleshooting,
compatibility notes, και πλήρη περιγραφή του UI flow.
