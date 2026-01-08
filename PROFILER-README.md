# Profiler Documentation Index

This directory contains comprehensive documentation for the MattMC profiler system.

## Quick Start

**New to the profiler?** Start here:
1. Read [PROFILER-QUICK-REFERENCE.md](PROFILER-QUICK-REFERENCE.md) - Get up and running in 5 minutes
2. Check [PROFILER-OUTPUT-EXPLAINED.md](PROFILER-OUTPUT-EXPLAINED.md) - Understand your results

## Documentation Files

### 📋 PROFILER-QUICK-REFERENCE.md
**Best for: Quick lookups and checking specific metrics**

A condensed reference guide that explains:
- What each line in the profiler output means
- Quick health checks for your server
- Common performance patterns
- When to worry about performance

**Read this when:**
- You just ran the profiler and want to know what the numbers mean
- You need a quick reminder of what's normal
- You want to check if your server is healthy

---

### 📖 PROFILER-OUTPUT-EXPLAINED.md
**Best for: First-time users and detailed understanding**

A comprehensive, plain-English guide covering:
- Detailed breakdown of every metric
- What each operation type does
- How to interpret your results
- Example scenarios (laggy server, network bottleneck, healthy server)
- Tips for using the profiler effectively

**Read this when:**
- You're using the profiler for the first time
- You want to deeply understand what the profiler measures
- You need to explain profiler results to someone else
- You're troubleshooting performance issues

---

### 🎨 PROFILER-VISUAL-GUIDE.md
**Best for: Visual learners and understanding data flow**

Visual diagrams and flowcharts showing:
- How the profiler works step-by-step
- Data flow from timing to report
- Thread tracking visualization
- Performance impact diagrams
- Time unit conversions
- Your server status dashboard

**Read this when:**
- You prefer visual explanations
- You want to understand how timing works
- You're curious about the internal flow
- You want to see your performance visualized

---

### 🔧 PROFILER-TECHNICAL-DETAILS.md
**Best for: Developers and advanced users**

Technical deep-dive covering:
- Complete architecture overview
- Component implementations
- Code examples and data structures
- Instrumentation points in the game code
- Performance overhead analysis
- Thread safety mechanisms
- Future enhancement ideas

**Read this when:**
- You want to understand the implementation
- You're modifying or extending the profiler
- You need technical details for debugging
- You're reviewing the profiler code

---

### 📐 PROFILER.md
**Best for: Implementation planning and reference**

The original implementation plan including:
- Requirements and specifications
- Architecture design
- Technical approach
- Integration points
- Phase-by-phase implementation steps
- Testing strategy

**Read this when:**
- You want to see the original design
- You're planning similar profiling systems
- You need to understand design decisions
- You're reviewing the implementation against the spec

---

## Common Questions

### "I just ran the profiler, what do my numbers mean?"
👉 Start with [PROFILER-QUICK-REFERENCE.md](PROFILER-QUICK-REFERENCE.md)

### "Is my server running well?"
👉 Check the health checklist in [PROFILER-QUICK-REFERENCE.md](PROFILER-QUICK-REFERENCE.md)

### "What does 'tick.level' mean?"
👉 See [PROFILER-OUTPUT-EXPLAINED.md](PROFILER-OUTPUT-EXPLAINED.md) → "Time Distribution by Operation"

### "How does the profiler actually work?"
👉 See [PROFILER-VISUAL-GUIDE.md](PROFILER-VISUAL-GUIDE.md) for visual explanation  
👉 See [PROFILER-TECHNICAL-DETAILS.md](PROFILER-TECHNICAL-DETAILS.md) for code-level details

### "My server is laggy, what should I look for?"
👉 See [PROFILER-OUTPUT-EXPLAINED.md](PROFILER-OUTPUT-EXPLAINED.md) → "Example Scenarios"

### "How much overhead does profiling add?"
👉 See [PROFILER-TECHNICAL-DETAILS.md](PROFILER-TECHNICAL-DETAILS.md) → "Performance Considerations"

---

## Using the Profiler

### Basic Commands

```bash
/profile start    # Begin profiling
/profile stop     # End profiling and generate report
```

**Requirements:** Operator level 2

### Where Reports Are Saved

```
debug/profiling/profile-YYYY-MM-DD_HH.mm.ss.txt
```

### Recommended Profiling Duration

- **Quick check**: 30-60 seconds
- **Baseline measurement**: 5-10 minutes  
- **Thorough analysis**: 30+ minutes during varied activity

---

## Understanding Your Results

### Your Example Output Explained

```
Total Active Time:    6.66 seconds (35.98% of session)
Total Ticks:          370
Average Tick Time:    9.9 ms

1. tick.level                                   3.56s   97.40%
2. tick.connection                              0.07s    1.83%
3. tick.sendChunks                              0.03s    0.71%
4. tick.commandFunctions                        0.00s    0.04%
5. tick.players
```

**Translation:**
- ✅ **9.9ms average tick** = Excellent! (target is < 50ms)
- ✅ **36% active time** = Healthy (server sleeps between ticks)
- ✅ **97% world simulation** = Normal distribution
- ✅ **2% network** = No bottleneck
- ✅ **5x capacity remaining** = Plenty of headroom

**Verdict: Your server is running great!** 🎉

---

## Document Sizes

- **Quick Reference**: ~6 KB - 5 minute read
- **Output Explained**: ~13 KB - 20 minute read
- **Visual Guide**: ~14 KB - 15 minute read
- **Technical Details**: ~20 KB - 30 minute read
- **Implementation Plan**: ~60 KB - Reference only

---

## Recommended Reading Order

### For Server Owners/Operators:
1. **PROFILER-QUICK-REFERENCE.md** (Start here!)
2. **PROFILER-OUTPUT-EXPLAINED.md** (For deeper understanding)
3. **PROFILER-VISUAL-GUIDE.md** (If you want visuals)

### For Developers:
1. **PROFILER-TECHNICAL-DETAILS.md** (Architecture and implementation)
2. **PROFILER.md** (Original design document)
3. **PROFILER-VISUAL-GUIDE.md** (Data flow diagrams)

### For First-Time Users:
1. **PROFILER-QUICK-REFERENCE.md** (Quick start)
2. **PROFILER-OUTPUT-EXPLAINED.md** (Learn what everything means)
3. Run `/profile start`, wait 1 minute, run `/profile stop`
4. Come back to the docs to understand your results!

---

## Additional Resources

### In-Game Help
- `/profile` - Shows available subcommands
- `/profile start` - Shows confirmation message
- `/profile stop` - Shows report file location (clickable link)

### Code Locations
- Command: `src/main/java/net/minecraft/server/commands/ProfileCommand.java`
- Manager: `src/main/java/net/minecraft/util/profiling/custom/ProfilerManager.java`
- Report Generator: `src/main/java/net/minecraft/util/profiling/custom/ProfilerReportGenerator.java`

---

## Contributing

Found an issue or have a suggestion for the profiler documentation?
- Check the implementation in `src/main/java/net/minecraft/util/profiling/custom/`
- Review the design in `PROFILER.md`
- Test your changes with `/profile start` and `/profile stop`

---

## Summary

The profiler helps you:
- ✅ Understand where your server spends time
- ✅ Identify performance bottlenecks
- ✅ Track performance over time
- ✅ Measure the impact of changes
- ✅ Optimize your server configuration

**Start with [PROFILER-QUICK-REFERENCE.md](PROFILER-QUICK-REFERENCE.md) and go from there!**
