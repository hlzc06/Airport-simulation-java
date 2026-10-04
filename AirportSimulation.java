import java.util.ArrayList;
import java.util.Random;

public class AirportSimulation {
    static final int TOTAL_PLANES = 6;
    static final long START = System.nanoTime();

    static void log(String message) {
        double seconds = (System.nanoTime() - START) / 1_000_000_000.0;
        System.out.printf("[%6.2fs] %-18s %s%n", seconds,
                Thread.currentThread().getName(), message);
    }

    public static void main(String[] args) throws InterruptedException {
        Random random = args.length == 0 ? new Random() : new Random(Long.parseLong(args[0]));
        Airport airport = new Airport();
        FuelTruck truck = new FuelTruck();
        AirTrafficController atc = new AirTrafficController(airport);
        Plane[] planes = new Plane[TOTAL_PLANES];
        truck.start();
        atc.start();

        for (int i = 0; i < TOTAL_PLANES; i++) {
            Thread.sleep(random.nextInt(3) * 1000L);
            // Plane 5 is the emergency aircraft. Counts never exceed 50.
            planes[i] = new Plane(i + 1, i == 4, random.nextInt(51),
                    random.nextInt(51), airport, truck);
            planes[i].start();
        }
        for (Plane plane : planes) plane.join();
        atc.join();
        truck.closeTruck();
        truck.join();
    }
}

// Shared monitor: all airport state is protected by this object's lock.
class Airport {
    private final Plane[] gates = new Plane[3];
    private final ArrayList<Plane> landingQueue = new ArrayList<>();
    private final ArrayList<Plane> departureQueue = new ArrayList<>();
    private boolean runwayBusy;
    private boolean emergencyDemonstrated;
    private int onGround;
    private int served;
    private int boarded;
    private int peakGround;
    private long totalWait;
    private long minWait = Long.MAX_VALUE;
    private long maxWait;

    synchronized void requestLanding(Plane plane) throws InterruptedException {
        plane.requestTime = System.nanoTime();
        landingQueue.add(plane);
        notifyAll();
        while (!plane.landingAllowed) wait();
    }

    synchronized void dock(Plane plane) {
        plane.docked = true;
        runwayBusy = false;
        notifyAll();
    }

    synchronized void requestDeparture(Plane plane) throws InterruptedException {
        departureQueue.add(plane);
        notifyAll();
        while (!plane.departureAllowed) wait();
    }

    synchronized void departed(Plane plane) {
        gates[plane.gate] = null;
        onGround--;
        runwayBusy = false;
        served++;
        boarded += plane.boardingCount;
        notifyAll();
    }

    private int freeGate() {
        for (int i = 0; i < gates.length; i++) {
            if (gates[i] == null) return i;
        }
        return -1;
    }

    private Plane emergencyPlane() {
        for (Plane plane : landingQueue) {
            if (plane.emergency) return plane;
        }
        return null;
    }

    private boolean congestionReady() {
        int docked = 0;
        int waiting = 0;
        for (Plane plane : gates) {
            if (plane != null && plane.docked) docked++;
        }
        for (Plane plane : landingQueue) {
            if (!plane.emergency) waiting++;
        }
        return docked == 2 && waiting >= 2;
    }

    // Only the actual ATC thread calls this method and prints ATC decisions.
    synchronized void controlAirport() throws InterruptedException {
        while (served < AirportSimulation.TOTAL_PLANES) {
            Plane emergency = emergencyPlane();
            Plane next = null;
            int gate = freeGate();

            if (!runwayBusy) {
                if (!emergencyDemonstrated) {
                    // Reserve gate 3 until planes 3 and 4 are waiting and
                    // plane 5 requests emergency landing. First two are serviced.
                    if (emergency != null && congestionReady()) {
                        AirportSimulation.log("ATC: CONGESTION - two gates occupied, "
                                + "two normal planes waiting; Plane-5 has low fuel.");
                        next = emergency;
                        emergencyDemonstrated = true;
                    } else {
                        for (Plane plane : landingQueue) {
                            if (plane.id <= 2) {
                                next = plane;
                                break;
                            }
                        }
                    }
                } else if (emergency != null && gate >= 0) {
                    next = emergency;
                } else if (!departureQueue.isEmpty()) {
                    Plane plane = departureQueue.remove(0);
                    runwayBusy = true;
                    plane.departureAllowed = true;
                    AirportSimulation.log("ATC: Take-off granted to " + plane.getName());
                    notifyAll();
                    continue;
                } else if (!landingQueue.isEmpty()) {
                    next = landingQueue.get(0);
                }

                if (next != null && gate >= 0 && onGround < 3) {
                    landingQueue.remove(next);
                    gates[gate] = next; // Reserve a gate BEFORE landing.
                    next.gate = gate;
                    onGround++;
                    peakGround = Math.max(peakGround, onGround);
                    runwayBusy = true;
                    long waiting = System.nanoTime() - next.requestTime;
                    totalWait += waiting;
                    minWait = Math.min(minWait, waiting);
                    maxWait = Math.max(maxWait, waiting);
                    next.landingAllowed = true;
                    AirportSimulation.log("ATC: Landing granted to " + next.getName()
                            + ", Gate-" + (gate + 1) + "; on ground = " + onGround
                            + (next.emergency ? " [EMERGENCY PRIORITY]" : ""));
                    notifyAll();
                    continue;
                }
            }

            for (Plane plane : landingQueue) {
                if (!plane.waitReported) {
                    AirportSimulation.log("ATC: " + plane.getName() + " must wait in the air "
                            + "(runway busy, no gate, or emergency gate reservation).");
                    plane.waitReported = true;
                }
            }
            wait(); // Releases the monitor so aircraft can update their state.
        }
        printStatistics();
    }

    private void printStatistics() {
        AirportSimulation.log("ATC: Simulation finished. Sanity checks:");
        for (int i = 0; i < gates.length; i++) {
            AirportSimulation.log("ATC: Gate-" + (i + 1) + " empty: " + (gates[i] == null));
        }
        AirportSimulation.log("ATC: Runway free: " + !runwayBusy);
        AirportSimulation.log("ATC: Ground empty: " + (onGround == 0));
        AirportSimulation.log("ATC: Queues empty: "
                + (landingQueue.isEmpty() && departureQueue.isEmpty()));
        AirportSimulation.log("ATC: Maximum ground occupancy: " + peakGround + " / 3");
        AirportSimulation.log("ATC: Emergency scenario demonstrated: " + emergencyDemonstrated);
        AirportSimulation.log("ATC: Planes served: " + served + "; passengers boarded: " + boarded);
        AirportSimulation.log(String.format("ATC: Landing permission wait (seconds): "
                + "minimum %.3f, average %.3f, maximum %.3f",
                minWait / 1_000_000_000.0, totalWait / 1_000_000_000.0 / served,
                maxWait / 1_000_000_000.0));
    }
}

class AirTrafficController extends Thread {
    private final Airport airport;

    AirTrafficController(Airport airport) {
        super("ATC");
        this.airport = airport;
    }

    public void run() {
        try {
            airport.controlAirport();
        } catch (InterruptedException e) {
            interrupt();
            AirportSimulation.log("ATC interrupted; simulation incomplete.");
        }
    }
}

class Plane extends Thread {
    final int id;
    final boolean emergency;
    final int arrivingCount;
    final int boardingCount;
    final Airport airport;
    final FuelTruck truck;
    // Permission and state fields are accessed under their shared monitor locks.
    int gate = -1;
    long requestTime;
    boolean landingAllowed;
    boolean departureAllowed;
    boolean docked;
    boolean waitReported;
    boolean fuelDone;

    Plane(int id, boolean emergency, int arrivingCount, int boardingCount,
          Airport airport, FuelTruck truck) {
        super("Plane-" + id);
        this.id = id;
        this.emergency = emergency;
        this.arrivingCount = arrivingCount;
        this.boardingCount = boardingCount;
        this.airport = airport;
        this.truck = truck;
    }

    public void run() {
        try {
            AirportSimulation.log("Pilot: Requesting landing."
                    + (emergency ? " EMERGENCY: low fuel!" : ""));
            airport.requestLanding(this);
            AirportSimulation.log("Pilot: Landing.");
            Thread.sleep(400);
            AirportSimulation.log("Pilot: Landed; coasting to Gate-" + (gate + 1));
            Thread.sleep(300);
            AirportSimulation.log("Pilot: Docked at Gate-" + (gate + 1));
            airport.dock(this);

            PassengerGroup passengers = new PassengerGroup(this);
            GroundService service = new GroundService(this);
            passengers.start();
            service.start();
            AirportSimulation.log("Pilot: Requesting refuelling truck.");
            truck.requestFuel(this);
            truck.waitForFuel(this);
            service.join();
            passengers.join();

            // Board only after disembarking, cleaning, supplies and fuel finish.
            PassengerGroup boarding = new PassengerGroup(this, true);
            boarding.start();
            boarding.join();
            AirportSimulation.log("Pilot: Requesting take-off.");
            airport.requestDeparture(this);
            AirportSimulation.log("Pilot: Undocking.");
            Thread.sleep(200);
            AirportSimulation.log("Pilot: Coasting to runway.");
            Thread.sleep(300);
            AirportSimulation.log("Pilot: Taking off.");
            Thread.sleep(400);
            AirportSimulation.log("Pilot: Departed.");
            airport.departed(this);
        } catch (InterruptedException e) {
            interrupt();
            AirportSimulation.log("Pilot interrupted; simulation incomplete.");
        }
    }
}

// One group thread per passenger activity, rather than one thread per person.
class PassengerGroup extends Thread {
    private final Plane plane;
    private final boolean boarding;

    PassengerGroup(Plane plane) { this(plane, false); }

    PassengerGroup(Plane plane, boolean boarding) {
        super("Passengers-" + plane.id);
        this.plane = plane;
        this.boarding = boarding;
    }

    public void run() {
        try {
            String action = boarding ? "Boarding" : "Disembarking";
            int count = boarding ? plane.boardingCount : plane.arrivingCount;
            AirportSimulation.log(action + " " + count + " passengers "
                    + (boarding ? "onto " : "from ") + plane.getName());
            Thread.sleep(700);
            AirportSimulation.log(action + " finished for " + plane.getName());
        } catch (InterruptedException e) {
            interrupt();
        }
    }
}

class GroundService extends Thread {
    private final Plane plane;

    GroundService(Plane plane) {
        super("GroundService-" + plane.id);
        this.plane = plane;
    }

    public void run() {
        try {
            AirportSimulation.log("Cleaning and refilling supplies for " + plane.getName());
            // Longer first two services keep both gates occupied during arrivals.
            Thread.sleep(plane.id <= 2 ? 10000 : 1200);
            AirportSimulation.log("Cleaning and supplies finished for " + plane.getName());
        } catch (InterruptedException e) {
            interrupt();
        }
    }
}

class FuelTruck extends Thread {
    private final ArrayList<Plane> requests = new ArrayList<>();
    private boolean closed;

    FuelTruck() { super("FuelTruck"); }

    synchronized void requestFuel(Plane plane) {
        requests.add(plane);
        notifyAll();
    }

    synchronized void waitForFuel(Plane plane) throws InterruptedException {
        while (!plane.fuelDone) wait();
    }

    private synchronized Plane nextRequest() throws InterruptedException {
        while (requests.isEmpty() && !closed) wait();
        if (requests.isEmpty()) return null;
        return requests.remove(0);
    }

    private synchronized void finishFuel(Plane plane) {
        plane.fuelDone = true;
        notifyAll();
    }

    synchronized void closeTruck() {
        closed = true;
        notifyAll();
    }

    public void run() {
        try {
            Plane plane;
            while ((plane = nextRequest()) != null) {
                AirportSimulation.log("Refuelling " + plane.getName());
                Thread.sleep(1000); // Outside monitor: requests can still be queued.
                AirportSimulation.log("Refuelling finished for " + plane.getName());
                finishFuel(plane);
            }
            AirportSimulation.log("Truck finished for the day.");
        } catch (InterruptedException e) {
            interrupt();
        }
    }
}
