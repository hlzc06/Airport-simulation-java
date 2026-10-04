# Verification

Tested with the Java 17 source-file launcher. Five seeds varied arrival delays and passenger counts. Logs were checked for runway exclusivity, single-truck refuelling, ground occupancy, emergency-before-normal permission, passenger totals and final empty states.

- Seed 42: six departures; emergency priority; resource and final-state checks passed; 20.38 seconds.
- Seed 7: six departures; emergency priority; resource and final-state checks passed; 18.74 seconds.
- Seed 0: six departures; emergency priority; resource and final-state checks passed; 18.26 seconds.
- Seed 1: six departures; emergency priority; resource and final-state checks passed; 17.06 seconds.
- Seed 2: six departures; emergency priority; resource and final-state checks passed; 18.67 seconds.

These are checks of five completed executions plus code review, not proof of every possible scheduling interleaving.
