# Asia Pacific Airport simulation

## Run

Install a JDK (Java 11 or newer). Open a terminal in this folder:

```
java AirportSimulation.java
```

For a repeatable arrival schedule and passenger counts:

```
java AirportSimulation.java 42
```

Alternatively, compile and run:

```
javac AirportSimulation.java
java AirportSimulation
```

The numeric argument is an optional random seed. Thread scheduling and waiting times can still vary. No external dependencies are required. The program is in one file so it is easy to run; each role still has its own class and thread.

## Requirements and assumptions

- Six plane threads arrive, with a randomly selected delay of exactly 0, 1 or 2 seconds before each arrival. Plane 5 has low fuel.
- Three gates exist. A gate and ground capacity slot are reserved together before landing permission is granted. The ground count includes aircraft cleared to land, which conservatively prevents more than three aircraft from occupying the airport.
- One runway serves both landing and departure. Landing holds the runway until docking, including the taxi phase. Departure holds it from permission until take-off completes. This models taxi movements conservatively.
- A plane retains its assigned gate until it has taken off. This conservative reservation avoids any ground waiting area and slightly reduces throughput.
- Aircraft waiting for permission remain airborne. Permission does not interrupt a movement already using the runway.
- Passenger groups, ground service and the fuel truck perform their activities on separate threads. Different gates can work at the same time. Cleaning/supplies and disembarking can overlap: the cleaning crew works in cleared sections and the supplies crew uses the service area. Boarding waits for all service and disembarkation to finish.
- One truck processes requests in FIFO order. Only this truck thread performs refuelling, so two aircraft cannot be refuelled simultaneously.
- Each aircraft has independently generated arriving and boarding passenger counts from 0 through 50. Passengers are represented as a group, matching the handout's output guidance.
- Landing waiting time runs from registration of the landing request to ATC permission; it excludes the landing and taxi durations. Statistics use `System.nanoTime()` for elapsed time.
- This is a console demonstration under normal, uninterrupted execution. Interrupts are reported; it does not implement recovery from a cancelled aircraft or equipment failure. Do not use interruption as a way of ending a normal demonstration.

## How the emergency demonstration works

The assignment asks for three gates overall but an emergency scenario with two gates occupied and two normal aircraft waiting. To make that scenario reliable, gate 3 is temporarily reserved for an emergency during the first wave. ATC first admits planes 1 and 2. Planes 3 and 4 wait in the air. Plane 5 requests emergency landing; ATC waits until the first two have docked and at least two normal aircraft are queued, prints the congestion event and admits the emergency to the reserved gate ahead of the normal queue. Plane 6 arrives with the same random delay rule.

The first two aircraft receive a longer, 10-second ground service to keep their gates occupied throughout this staged arrival window. After the emergency is admitted, all three gates are available for ordinary allocation. Emergency landing requests have first priority, then queued departures, then normal landings in arrival-request order. Departure priority lets aircraft free ground capacity. The emergency does not pre-empt a busy runway.

This is an explicit demonstration policy, rather than a claim that random arrivals alone always produce the required congestion. Explain this assumption in your report. The emergency aircraft is assumed to have enough remaining fuel to wait for the current movement and initial scenario setup.

## Where the lecture concepts appear

| Concept | Location | Purpose |
| --- | --- | --- |
| `Thread` and `run()` (Week 6) | Plane, ATC, passenger, service and truck classes | Each role executes on its own thread. |
| `synchronized` (Week 7) | Airport and FuelTruck methods | Protect shared queues, permission flags, gate assignments and counters. |
| Monitor and conditional synchronization (Week 8) | Permission waits and fuel request waits | Block until the corresponding condition is true. |
| `while (...) wait()` and `notifyAll()` (Weeks 8–9) | Airport and FuelTruck | Recheck conditions after waking; allow different waiting roles to progress. |
| Shared-resource safety (Week 11) | Gate/runway reservation in `controlAirport()` | Perform checking and resource reservation atomically under one monitor. |
| Deadlock and liveness (Weeks 10–12) | ATC scheduling and truck queue | Release locks while waiting and avoid holding two monitor locks together. |
| `join()` | Plane service completion and main shutdown | Wait for actual thread completion before boarding or ending the run. |

Small supporting additions are `ArrayList` for manually managed queues, `Random` for arrivals and passenger counts, and `System.nanoTime()` for elapsed time. These do not automatically manage concurrency. No executors, futures, parallel streams, timers or concurrent queue libraries are used.

## Read the code in this order

1. `main`: creates the shared airport and truck, starts the workers, creates six planes, and waits for completion.
2. `Plane.run`: contains the aircraft's complete journey, from landing request through departure.
3. `Airport`: stores shared state and schedules permissions. ATC actually executes the scheduling method; a method's class name alone does not create an ATC thread.
4. `PassengerGroup` and `GroundService`: simulate work at each gate.
5. `FuelTruck`: takes queued requests one at a time and signals completion.

For example, `requestLanding()` is called by a plane thread. It adds that plane to a queue and waits; it does not print an ATC decision. The ATC thread executes `controlAirport()`, reserves resources and signals permission. Similarly, a pilot requests fuel, but the truck thread prints and performs refuelling. Every output line identifies the current thread.

`wait()` releases its monitor lock. `sleep()` does not release a held monitor lock, so simulated work sleeps are outside the synchronized monitor methods. `notifyAll()` only wakes threads; each thread must reacquire the lock and test its own condition again.

Permission flags are read and written under the Airport lock. Fuel completion is read and written under the FuelTruck lock. `join()` ensures service threads finish before boarding and that all planes finish before shutdown. The arrays and queues are deliberately simple because only six aircraft exist.

## Verification and presentation

Check the supplied sample output for:

- The explicit congestion message and Plane-5 emergency priority before planes 3 and 4 receive landing permission.
- Ground occupancy never exceeding three.
- Runway movements occurring one at a time.
- Passenger/service output overlapping across gates.
- A refuelling finish before the next refuelling start.
- Six departures, three empty gates, free runway, empty ground and empty queues.
- Minimum, average and maximum waiting time, and the total boarding count.

Runtime is usually about 17–23 seconds depending on arrivals and scheduling, comfortably below the 60-second target on an ordinary machine. This is a simulation timing target, not a hard real-time guarantee if the machine is paused or overloaded.

For the 3–5 minute video, run the simulation and explain the monitor, emergency allocation, single truck and final checks using the matching code. Include your student number in the final submission ZIP name as required by the brief. This package contains the code, explanation and sample output; you still need the required academic report and your own presentation recording. Understand and adapt the implementation, and declare AI assistance according to your course rules.
