# Online Casino — Distributed Systems

<p align="center">
  <img src="screenshots/login_player.png" width="220"/>
</p>

A fully distributed online casino platform built in Java, with a real Android mobile frontend. Players search for games, place bets, and receive live jackpot notifications — all over raw TCP sockets, no frameworks, no database.

---

## What is it?

Picture a casino where the "house" runs across multiple servers at the same time. A player opens the Android app, picks a game — slot machine, roulette, lucky wheel, poker — bets some FUN currency, and within milliseconds a result comes back. Behind the scenes, a chain of distributed components handles the request: a Master server routes it to the right Worker, the Worker asks a dedicated Secure Random Generator for a cryptographically verified random number, computes the payout, and sends it back. If a jackpot hits, every connected player gets a live push notification instantly.

There is no database. No external frameworks. No third-party libraries. Everything — storage, concurrency, communication, fault tolerance — is built entirely on raw Java sockets and the standard JDK.

---

## Login Screen

<p align="center">
  <img src="screenshots/login_player.png" width="220"/>
  &nbsp;&nbsp;&nbsp;
  <img src="screenshots/login_manager.png" width="220"/>
</p>

The app opens to a single login screen with two roles selectable at the top: **Player** and **Manager**.

- **Player mode** — enter a Player ID (format: 2 uppercase letters + 4 digits, e.g. `ON2212`), a starting balance in FUN tokens, and the server IP and port. For the Android emulator the backend is always reachable at `10.0.2.2:5000`; on a real device you use the local network IP of the machine running the backend.
- **Manager mode** — enter a Manager ID (same format, e.g. `AK1234`) and the server address, then tap **Open Console** to access the full management dashboard.

---

## Player — Lobby

<p align="center">
  <img src="screenshots/lobby.png" width="280"/>
</p>

The lobby is the main hub for players. It shows:

- **Wallet** — current balance in FUN tokens, with a **+ ADD** button to top up.
- **Daily Bonus** — a banner appears when a daily bonus of +100 FUN is claimable.
- **Search Filters** — three independent filters to narrow down games:
  - **Min Stars** — minimum rating (ALL, 1★ through 5★)
  - **Bet Category** — spending tier ($, $$, $$$, or ALL)
  - **Risk Level** — LOW, MED, HIGH, or ALL
- **Live Ticker** — a scrolling bar at the bottom streams real-time bets from all active players on the server.
- **SEARCH GAMES** button — sends the selected filters asynchronously to the Master and populates the results screen below.

---

## Player — Game List

<p align="center">
  <img src="screenshots/games.png" width="280"/>
</p>

Search results appear as a scrollable card list. Each card shows:

- Game name and provider
- Star rating and number of votes
- Bet range in FUN
- Risk level badge (LOW / MED / HIGH RISK)
- Jackpot multiplier (e.g. JACKPOT 20X)
- Bet category chip ($, $$, $$$)
- **RATE** button — opens a 1–5 star rating dialog
- **PLAY** button — navigates directly into that game's screen

---

## Player — Games

### RouletteX

<p align="center">
  <img src="screenshots/roulette.png" width="280"/>
</p>

A full European roulette wheel with a complete betting table. Players can:

- Choose a bet type by tapping: **RED / BLACK** (2x), **ODD / EVEN** (2x), **1–18 / 19–36** (2x), column bets (1st/2nd/3rd 12, 3x), or any straight number (36x)
- Use the **MIN / ½ / 2X / MAX** quick-fill buttons for the bet amount
- Open the **Bet Calculator** (abacus icon) to preview all possible outcomes before committing
- Hit **SPIN** to send the play request to the backend

A live latency indicator (e.g. `LIVE 361ms`) shows the round-trip time to the Master server in real time.

### Bet Calculator

<p align="center">
  <img src="screenshots/bet_calculator.png" width="280"/>
</p>

Tapping the calculator icon opens a modal that breaks down every possible outcome for the current bet amount. For a 10.00 FUN bet it shows the probability, the multiplier, and the exact return for each scenario — from JACKPOT (1%, 20x, +190 FUN) down to No win (50%, 0x, −10 FUN) — plus the **Expected value per bet**. This is computed locally from the game's risk table and jackpot value.

### luckywheel

<p align="center">
  <img src="screenshots/wheel.png" width="280"/>
</p>

An animated spinning wheel divided into coloured segments, each mapped to a multiplier or LOSE. The wheel is rendered as a custom `Canvas` view that animates a deceleration spin on result. Players set a bet amount and tap **SPIN**; the server returns the outcome index which determines which segment the pointer lands on.

### MegaPoker

<p align="center">
  <img src="screenshots/poker.png" width="280"/>
</p>

A video poker game with a full hand-ranking paytable visible before betting: Royal Flush (JACKPOT), Straight Flush (50x), Four of a Kind (25x), Full House (9x), Flush (6x), Straight (4x), Three of a Kind (3x). The card area animates the deal. Players tap **DEAL** to play; the result from the server determines the hand drawn.

### LuckySlots

<p align="center">
  <img src="screenshots/slots.png" width="280"/>
</p>

A classic 3-reel slot machine with animated spinning reels. The paytable is always visible below the reels: Jackpot, Diamond (10x), Seven (5x), Bell (3x), Star (2x), Grapes (1.5x). Tapping **PULL** sends the play request; the three reel outcomes are determined by the server's random number and displayed via the spin animation.

---

## Player — My Stats

<p align="center">
  <img src="screenshots/my_stats.png" width="280"/>
</p>

The stats screen (accessible via the trophy icon in any game) shows the player's full session history:

- **Level & XP** — a progression bar (e.g. Level 0 · Beginner, 90 XP)
- **Total Bets** — number of rounds played
- **Win Rate** — percentage of rounds won
- **Net P/L** — total profit or loss in FUN
- **Best Win** — largest single win in FUN
- **Last N Bets chart** — a bar chart where green bars are wins and pink bars are losses, giving a quick visual read of recent form

---

## Manager Dashboard

The Manager interface has four tabs: **GAMES**, **BY PROVIDER**, **BY PLAYER**, and **SYSTEM**.

### Games Tab

<p align="center">
  <img src="screenshots/manager_games.png" width="280"/>
</p>

Three actions available:

- **Add New Game** — register a new game by providing a JSON file with name, provider, bet limits, risk level, and hash key. The Master hashes the game name to select the target Worker, stores it in memory, and replicates it to the other Workers.
- **Change Risk Level** — update the payout profile of an existing game. LOW / MEDIUM / HIGH each has a different multiplier table and jackpot value.
- **Remove Game** — deactivate a game so it no longer appears in player searches. Historical stats (wins/losses) are preserved and still show up in the analytics queries.

### Stats by Provider

<p align="center">
  <img src="screenshots/manager_provider.png" width="280"/>
</p>

Tap **RUN QUERY** to trigger a full **MapReduce** across all Workers. Each Worker emits intermediate results (provider → game → house balance) to the Reducer; the Reducer aggregates and returns the final totals to the Master, which formats them for display. Results show:

- Per-game house profit/loss in FUN for each provider
- Provider total
- A bar chart comparing net results across all games at a glance

### Stats by Player + Leaderboard

<p align="center">
  <img src="screenshots/manager_player.png" width="280"/>
</p>

**Stats by Player** runs a second MapReduce to compute total P/L per player across all Workers. The scatter chart plots each player's net result. Below it, **RUN LEADERBOARD** produces a ranked list sorted by total profit descending — useful for identifying the top winners and biggest losers across the entire platform.

### System Tab

<p align="center">
  <img src="screenshots/manager_system.png" width="280"/>
</p>

The system tab shows:

- **Signed-in Manager ID** and **Master connection address**
- **Check Workers** — pings all Worker nodes and returns their status (ONLINE/OFFLINE), number of active games, and total bets processed
- **Stress Test (50 bets)** — fires 50 concurrent bets across all active games simultaneously. The result shows total time, win/loss counts, and every individual outcome — useful for verifying that concurrent synchronisation works correctly under load. The example shows 50 bets completing in 0.86 seconds.

---

## Running the backend

This is a **distributed system** — each component (SRG, Reducer, Worker, Master) is an independent process that communicates with the others over TCP sockets. You can run every component on the **same machine** using `localhost`, or spread them across **multiple machines** on the same network. The system works either way without any code changes.

### Option A — One machine (Windows)

Double-click `start_all.bat`. It compiles everything and opens each component in its own terminal window automatically.

### Option B — One machine (Linux / Mac)

```bash
find src -name "*.java" > sources.txt && javac -d bin @sources.txt

java -cp bin srg.SecureRandomGenerator 6000         # start first
java -cp bin reducer.Reducer 7000
java -cp bin worker.WorkerNode 0 5001 localhost 6000 localhost 7000
java -cp bin worker.WorkerNode 1 5002 localhost 6000 localhost 7000
java -cp bin master.Master 5000 5003 localhost 7000 localhost:5001 localhost:5002
```

### Option C — Multiple machines

```bash
# Machine A (e.g. 192.168.1.10) — SRG and Reducer
java -cp bin srg.SecureRandomGenerator 6000
java -cp bin reducer.Reducer 7000

# Machine B (e.g. 192.168.1.11) — Workers and Master
java -cp bin worker.WorkerNode 0 5001 192.168.1.10 6000 192.168.1.10 7000
java -cp bin worker.WorkerNode 1 5002 192.168.1.10 6000 192.168.1.10 7000
java -cp bin master.Master 5000 5003 192.168.1.10 7000 192.168.1.11:5001 192.168.1.11:5002

# Machine C — DummyPlayer (or Android app connecting to Machine B)
java -cp bin player.DummyPlayer 192.168.1.11 5000 5001 AB1234 100.0
```

---

## How it works — Architecture

### TCP sockets for all communication

Every single connection in the system is a raw TCP socket. The Master listens on port 5000 for players and managers, Workers on their own ports, the SRG on 6000, the Reducer on 7000. There is no HTTP, no REST, no message queue. Each request opens a connection, sends a command in our custom `~~`-delimited protocol, reads the response, and closes. The only exception is the broadcast channel on port 5001, which stays open permanently so the Master can push jackpot notifications to connected players without polling.

### Multithreading with `synchronized` and `wait/notify`

No `java.util.concurrent` was used anywhere. Every shared data structure — the game registry, the bet history, the subscriber list — is protected with plain `synchronized` blocks. Threads coordinate through `wait()` and `notifyAll()`. This is most visible in the SRG, where a producer thread and a consumer thread share a buffer per game and block each other correctly without any locks or semaphores from the concurrency library.

### Producer-Consumer — Secure Random Generator

The SRG is a standalone server process that maintains one queue per active game. A background producer thread runs continuously for each game, filling its buffer with cryptographically secure random integers (`java.security.SecureRandom`) up to a capacity of 50 numbers. When a Worker needs a random number to resolve a bet, it connects to the SRG and consumes one — blocking if the buffer is empty. Each number comes bundled with a SHA-256 HMAC (`SHA-256(number + gameSecret)`) that the Worker verifies before use.

### MapReduce for statistics

When a Manager requests statistics, the Master sends a Map task to every Worker in parallel. Each Worker scans its in-memory bet history and emits partial results over TCP to the Reducer. The Reducer merges them using `wait/notify` synchronisation until all Workers have reported, then returns the final aggregated answer to the Master. The entire pipeline is hand-built: no Hadoop, no Spark, no shared memory.

### Active Replication and fault tolerance

Every game stored on Worker 1 is simultaneously replicated to Worker 2, and vice versa. When a bet is resolved on the primary Worker, it immediately sends a sync message to all replicas. If a Worker goes silent, all traffic is transparently redirected to the surviving replica with no downtime. Games are distributed across Workers using `hash(gameName) mod N`.

### In-memory storage — no database, no external libraries

All game data, bet history, player ratings, and running stats live in standard Java collections inside the Worker and Reducer JVMs. Nothing is persisted to disk. The only dependency is the standard JDK.

---

## Security — Encrypted TCP with Diffie-Hellman + AES

### The problem

In the original architecture every message travelled as readable plain text. Anyone on the same network running Wireshark could see everything in real time:

```
PLAY~~player1~~LuckySlots~~10.0
OK~~25.0
ADD_BALANCE~~player1~~500.0
OK~~added
```

<p align="center">
  <img src="screenshots/wireshark_before.png" width="750"/>
  <br/><em>Before encryption: protocol strings fully readable in Wireshark</em>
</p>

### The solution

We implemented a `SecureChannel` class that wraps every **external** TCP connection (Player → Master, Manager → Master) with end-to-end encryption, using only `javax.crypto`, `java.security`, and `java.math.BigInteger` — all part of the standard JDK. Internal channels (Master ↔ Worker, Worker ↔ Reducer, Worker ↔ SRG) remain plain TCP since they run on a trusted local network.

The handshake: both sides independently generate an ephemeral Diffie-Hellman key pair (RFC 2409 Group 2, 1024-bit MODP), exchange only their public keys, and each computes the shared secret locally — it is never transmitted. A 128-bit AES key is derived as the first 16 bytes of `SHA-256(shared_secret)`. Every message is then encrypted with AES-128-CBC and a fresh random IV, making replay attacks impossible.

```
Player                           Master
  │                                │
  │── DH public key (g^a) ────────►│   safe to intercept
  │◄─ DH public key (g^b) ─────────│   safe to intercept
  │                                │
  │  Both compute g^(ab) locally   │
  │                                │
  │═══════ AES-128-CBC ════════════│
  │── "PLAY~~p1~~LuckySlots~~10" ─►│   unreadable ciphertext
  │◄─ "OK~~25.0" ──────────────────│   unreadable ciphertext
```

<p align="center">
  <img src="screenshots/wireshark.png" width="750"/>
  <br/><em>After encryption: Wireshark cannot parse the data — "Malformed Packet: RSL"</em>
</p>

The `SecureChannel` class exposes the same `writeUTF` / `readUTF` API as the original `ObjectOutputStream`, so the rest of the codebase required zero protocol changes.
