# Κατανεμημένα Συστήματα — Παραδοτέο Β
### Ομάδα: 3230096 / 3230188 / 3230064
### Εαρινό Εξάμηνο 2025–2026

---

## Τι είναι αυτό το project

Μια πλατφόρμα online τυχερών παιχνιδιών που τρέχει **κατανεμημένα** σε πολλαπλά μηχανήματα. Αποτελείται από:

- **Backend** σε Java (Master, Workers, Reducer, SRG)
- **Manager console app** για τη διαχείριση παιχνιδιών και στατιστικών
- **Android app** για τους παίκτες

---

## Αρχιτεκτονική — Ποιος κάνει τι

```
Android App / DummyPlayer
        │
        │ TCP (search, play, rate, addBalance)
        ▼
┌─────────────────────────────────────────────┐
│                   MASTER                    │
│  - Δέχεται connections από clients          │
│  - Δρομολογεί games σε Workers με           │
│    H(GameName) mod N                        │
│  - Τρέχει MapReduce για stats               │
│  - Κάνει broadcast jackpot events           │
└──────┬──────────────────────────────────────┘
       │ TCP
       ├──────────────────┬─────────────────┐
       ▼                  ▼                 ▼
┌──────────┐       ┌──────────┐      ┌──────────┐
│ Worker 1 │       │ Worker 2 │  ... │ Worker N │
│          │       │          │      │          │
│ Αποθηκεύ │       │ Αποθηκεύ │      │ Αποθηκεύ │
│ει games  │       │ει games  │      │ει games  │
│ in-memory│       │ in-memory│      │ in-memory│
└────┬─────┘       └────┬─────┘      └────┬─────┘
     │ TCP               │ TCP              │ TCP
     └───────────────────┴──────────────────┘
                         │
                         ▼
                  ┌──────────────┐
                  │    REDUCER   │
                  │              │
                  │ Συγκεντρώνει │
                  │ MAP results  │
                  │ από Workers  │
                  └──────────────┘

┌──────────────────────────────────────────────┐
│           SECURE RANDOM GENERATOR            │
│  - Ξεχωριστό μηχάνημα                        │
│  - Παράγει τυχαίους αριθμούς για κάθε game   │
│  - Στέλνει (number, sha256(number+secret))   │
│  - Producer-consumer με buffer size=50       │
└──────────────────────────────────────────────┘
```

---

## Πώς λειτουργεί κάθε component

### Master
- TCP Server που δέχεται όλα τα requests (clients + manager)
- Είναι **πολυνηματικός** — κάθε connection σε ξεχωριστό thread
- Δρομολογεί κάθε παιχνίδι στον κατάλληλο Worker: `Worker = H(GameName) mod N`
- Για Search: στέλνει παράλληλα σε **όλους** τους Workers και συγκεντρώνει αποτελέσματα
- Για Stats: ξεκινά **MapReduce** — Workers κάνουν map, Reducer κάνει reduce
- Έχει ξεχωριστό **broadcast port (5010)** για real-time notifications

### Workers
- Κρατάνε τα games **αποκλειστικά στη μνήμη** (HashMap)
- Για κάθε bet: καλούν τον SRG για τυχαίο αριθμό → υπολογίζουν αποτέλεσμα → ενημερώνουν stats
- **Συγχρονισμός** με `synchronized`/`wait`/`notify` για ταυτόχρονα bets
- Κάνουν **map phase** για MapReduce: στέλνουν τα δεδομένα τους στον Reducer

### Reducer
- Δέχεται MAP results από όλους τους Workers
- Περιμένει με `wait()` μέχρι να έρθουν όλα
- Κάνει **reduce**: συγκεντρώνει και επιστρέφει στον Master

### SRG (Secure Random Generator)
- Producer thread: παράγει συνεχώς αριθμούς → αποθηκεύει σε Queue (max 50)
- Consumer: κάθε Worker παίρνει τον επόμενο αριθμό
- Ασφάλεια: στέλνει `(number, sha256(number + secret))` — ο Worker επαληθεύει

### Active Replication (Bonus)
- Κάθε game αποθηκεύεται στον primary Worker ΚΑΙ σε όλους τους άλλους ως replica
- Αν πέσει primary → το request πηγαίνει αυτόματα στο replica
- Αν επανέλθει primary → re-hydration από replica
- Replica χρησιμοποιείται **μόνο** σε failure

---

## Πώς τρέχει το σύστημα

### Σειρά εκκίνησης (ΥΠΟΧΡΕΩΤΙΚΗ):

```
1. SRG          java -cp bin srg.SecureRandomGenerator 6000
2. Reducer      java -cp bin reducer.Reducer 7000
3. Workers      java -cp bin worker.WorkerNode 0 5001 localhost 6000 localhost 7000
                java -cp bin worker.WorkerNode 1 5002 localhost 6000 localhost 7000
                java -cp bin worker.WorkerNode 2 5003 localhost 6000 localhost 7000
                java -cp bin worker.WorkerNode 3 5004 localhost 6000 localhost 7000
4. Master       java -cp bin master.Master 5000 5010 localhost 7000 localhost:5001 localhost:5002 localhost:5003 localhost:5004
5. Manager      java -cp bin manager.ManagerApp localhost 5000 AK1234
6. DummyPlayer  java -cp bin player.DummyPlayer localhost 5000 5010 AB1234 500.0
```

> **Σημείωση για τον Master:** Το 5010 είναι το broadcast port (για jackpot notifications). Πρέπει να είναι διαφορετικό από τα worker ports.

### Run Configurations στο IntelliJ:
| Config | Arguments |
|--------|-----------|
| 1. SRG | `6000` |
| 2. Reducer | `7000` |
| 3. Worker 1 | `0 5001 localhost 6000 localhost 7000` |
| 4. Worker 2 | `1 5002 localhost 6000 localhost 7000` |
| 8. Worker 3 | `2 5003 localhost 6000 localhost 7000` |
| 9. Worker 4 | `3 5004 localhost 6000 localhost 7000` |
| 5. Master | `5000 5010 localhost 7000 localhost:5001 localhost:5002 localhost:5003 localhost:5004` |
| 6. Manager | `localhost 5000 AK1234` |
| 7. DummyPlayer | `localhost 5000 5010 AB1234 500.0` |

---

## Manager Console — Τι κάνει κάθε επιλογή

```
--- MANAGER MENU ---
1. Add game          → Προσθέτει παιχνίδι από JSON file
2. Remove game       → Απενεργοποιεί παιχνίδι (τα stats παραμένουν)
3. Update risk level → Αλλάζει το risk level ενός παιχνιδιού
4. Stats by Provider → MapReduce: κέρδη/ζημιές ανά provider και game
5. Stats by Player   → MapReduce: συνολικό P/L ανά παίκτη
6. Leaderboard       → MapReduce: όλοι οι παίκτες ranked by P/L
7. Worker Status     → Ping σε όλους τους Workers — ONLINE/OFFLINE, games, bets
8. Stress Test       → Τρέχει N ταυτόχρονα bets για testing παραλληλίας
0. Exit
```

### JSON format για παιχνίδι:
```json
{
  "GameName": "LuckySlots",
  "ProviderName": "provider1",
  "Stars": 4,
  "NoOfVotes": 120,
  "GameLogo": "/usr/bin/images/luckyslots.png",
  "MinBet": 0.1,
  "MaxBet": 10,
  "RiskLevel": "low",
  "HashKey": "secret_key_001"
}
```
> BetCategory και Jackpot υπολογίζονται αυτόματα από το σύστημα.

---

## Android App — Τι κάνει κάθε οθόνη

| Οθόνη | Περιγραφή |
|-------|-----------|
| Login | Επιλογή ρόλου (Player/Manager), σύνδεση με Master |
| Main (Player) | Φίλτρα αναζήτησης + Live ticker με real-time bet events |
| Results | Λίστα παιχνιδιών με emoji icon, risk, jackpot, PLAY/RATE |
| GamePlay | Animation παιχνιδιού (Roulette, Slots, Poker, Wheel) |
| Stats (📊) | Level, XP, win rate, net P/L, bar chart τελευταίων bets |
| Manager | Tabs: Games / By Provider / By Player / System |
| System tab | Worker Status + Stress Test |

### Login για emulator:
- Master host: `10.0.2.2` (ο emulator βλέπει έτσι το host machine)
- Master port: `5000`
- Broadcast port: αυτόματα `5010` (masterPort + 10)

---

## Λογική Πονταρίσματος

```
Παίκτης πονταρίζει → Master → Worker

Worker:
  1. Παίρνει τυχαίο αριθμό από SRG
  2. Επαληθεύει: sha256(number + secret) == received_hash
  3. Αν number % 100 == 0 → JACKPOT (×10/20/40 ανάλογα risk)
  4. Αλλιώς: index = number % 10 → multiplier από πίνακα risk
  5. Ενημερώνει houseBalance και allBets (synchronized)
  6. Master κάνει broadcast το αποτέλεσμα (για live ticker)

Πίνακες multipliers:
  Low:    [0.0, 0.0, 0.0, 0.1, 0.5, 1.0, 1.1, 1.3, 2.0, 2.5]  Jackpot: 10x
  Medium: [0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 1.5, 2.5, 3.5]  Jackpot: 20x
  High:   [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5]  Jackpot: 40x
```

---

## MapReduce — Πώς λειτουργεί

```
Manager ζητά stats
        ↓
Master στέλνει WORKER_STATS_*_MR σε όλους τους Workers παράλληλα
        ↓
Κάθε Worker συνδέεται με τον Reducer και στέλνει τα δεδομένα του
        ↓
Reducer συγκεντρώνει (wait/notify μέχρι να έρθουν όλοι)
        ↓
Master κάνει REDUCE_FETCH → παίρνει τα αποτελέσματα → εμφανίζει
```

---

## Τεχνικές λεπτομέρειες

- **Επικοινωνία:** αποκλειστικά TCP Sockets (`ServerSocket`, `ObjectOutputStream.writeUTF`)
- **Συγχρονισμός:** μόνο `synchronized`, `wait()`, `notify()` — καμία χρήση `java.util.concurrent`
- **Storage:** αποκλειστικά in-memory (HashMap, ArrayList, ArrayDeque) — καμία βάση δεδομένων
- **Protocol:** custom text protocol με separator `~~` (π.χ. `PLAY~~AN1234~~LuckySlots~~5.00`)
- **Hashing:** `H(GameName) = Math.abs(gameName.hashCode()) % N`
