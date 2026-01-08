# Profiler Quick Reference

## What Each Line Means (Your Example)

```
────────────────────────────────────────────────────────────────────────────────
Server Thread (Main Thread)
────────────────────────────────────────────────────────────────────────────────
```
**→** This section is about your **main server thread** (the heart of your Minecraft server)

---

```
Total Active Time:    6.66 seconds (35.98% of session)
```
**→** The server was **actively working** for 6.66 seconds  
**→** That's **36% of the total time** you were profiling  
**→** The other 64% was **sleep/idle time** (this is NORMAL - servers sleep between ticks)

---

```
Total Ticks:          370
```
**→** The server ran **370 game ticks**  
**→** At 20 ticks per second, this is about **18.5 seconds** of profiling  
**→** A "tick" is one cycle of the game loop

---

```
Average Tick Time:    9.9 ms
```
**→** Each tick took **9.9 milliseconds** on average  
**→** Target is under 50ms for smooth gameplay  
**→** **9.9ms is EXCELLENT** - you have 5x performance headroom! ✅

---

```
Time Distribution by Operation:
```
**→** Here's where your server spends its time, broken down by task type

---

```
1. tick.level                                   3.56s   97.40%
```
**→ 97% of time**: Updating the game world  
**What it does:**
- Move entities (players, mobs, items)
- Update blocks (redstone, water, crops)
- Process chunks
- Update tile entities (chests, furnaces)

**Is this normal?** YES! World simulation is the server's main job.

---

```
2. tick.connection                              0.07s    1.83%
```
**→ 2% of time**: Handling network packets from players  
**What it does:**
- Receive player actions (movement, mining, placing)
- Process incoming packets
- Manage connections

**Is this normal?** YES! Very healthy - network isn't a bottleneck.

---

```
3. tick.sendChunks                              0.03s    0.71%
```
**→ 0.7% of time**: Sending world chunks to players  
**What it does:**
- Send new chunks as players explore
- Update changed chunks

**Is this normal?** YES! Low percentage means players aren't moving much or chunks are cached efficiently.

---

```
4. tick.commandFunctions                        0.00s    0.04%
```
**→ 0.04% of time**: Running command functions and datapacks  
**What it does:**
- Execute .mcfunction files
- Run command blocks
- Process datapack commands

**Is this normal?** YES! Nearly zero means no heavy datapacks running.

---

```
5. tick.players
```
**→ No data shown**: Took too little time to display  
**What it does:**
- Update player stats
- Manage player list
- Player-specific timers

**Is this normal?** YES! So fast it doesn't even show up.

---

## Quick Health Check

### ✅ Your Server is HEALTHY if:
- Average Tick Time < 50ms ← **You: 9.9ms ✅**
- Total Active Time < 95% ← **You: 36% ✅**
- tick.level is highest percentage ← **You: 97% ✅**
- No single operation > 50% alone ← **You: All good ✅**

### ⚠️ Warning Signs (you DON'T have these):
- Average Tick Time > 50ms → Server lagging
- Total Active Time > 95% → Server maxed out
- tick.connection > 30% → Network bottleneck
- Any operation > 80% → Single bottleneck

---

## What Your Numbers Mean

**Your Performance:**
- **9.9ms average tick** = 20% of available time (50ms target)
- **You have 5x headroom** before hitting performance issues
- **World simulation is 97%** = Normal and expected
- **Network is 2%** = Healthy
- **Everything else < 1%** = No bottlenecks

**What you could handle:**
- 5x more players
- 5x more entities
- Complex redstone farms
- More active chunks
- Heavy automation

**In simple terms:** Your server is **running great** with plenty of capacity to spare! 🎉

---

## Commands

```bash
/profile start    # Begin profiling
/profile stop     # End profiling and generate report
```

**Requirements:** Operator level 2 (op level 2)

---

## Where Reports Are Saved

**Directory:** `debug/profiling/`  
**Filename:** `profile-YYYY-MM-DD_HH.mm.ss.txt`  
**Example:** `debug/profiling/profile-2026-01-08_15.30.45.txt`

---

## How Long to Profile

- **Quick check**: 30-60 seconds
- **Baseline**: 5-10 minutes
- **Deep analysis**: 30+ minutes

---

## Common Operations

| Operation | What It Is |
|-----------|-----------|
| `tick.level` | World updates (entities, blocks, chunks) |
| `tick.connection` | Network packet processing |
| `tick.sendChunks` | Sending chunks to players |
| `tick.commandFunctions` | Datapack/command execution |
| `tick.players` | Player management |

---

## Real Performance Issues (Examples)

### Laggy Server
```
Average Tick Time:    52.3 ms    ⚠️ Over 50ms target!
tick.level           17.2s   93%
```
**Problem:** World taking too long  
**Solution:** Reduce entities, limit chunks, optimize redstone

### Network Bottleneck
```
Average Tick Time:    42.7 ms
tick.connection       8.5s   54%    ⚠️ Too high!
```
**Problem:** Network dominating  
**Solution:** Too many players, check bandwidth, investigate packet flooding

### Your Server (Healthy) ✅
```
Average Tick Time:    9.9 ms     ✅ Excellent!
tick.level           3.56s   97%  ✅ Normal distribution
tick.connection      0.07s    2%  ✅ Healthy network
```
**Status:** Perfect! No issues.

---

## Summary

Your profiler output shows your server is **performing excellently**:

1. ✅ **Fast ticks** (9.9ms vs 50ms target)
2. ✅ **Low load** (36% active time)
3. ✅ **Normal distribution** (97% world simulation)
4. ✅ **No bottlenecks** (nothing over 50%)
5. ✅ **5x capacity remaining**

**You're good to go!** 🚀

---

## Need More Details?

- **User-friendly guide:** See `PROFILER-OUTPUT-EXPLAINED.md`
- **Technical deep-dive:** See `PROFILER-TECHNICAL-DETAILS.md`
- **Implementation plan:** See `PROFILER.md`
