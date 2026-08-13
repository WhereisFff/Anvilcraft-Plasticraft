---
navigation:
  title: "Drone Station"
  icon: "anvilcraftplasticraft:drone_station"
categories:
  - "anvilcraftplasticraft:production_blocks"
items:
  - anvilcraftplasticraft:drone_station
---

# Drone Station

The Drone Station is the dock and charger of a drone fleet. It offers 16 slots that hold any mix of the four drones, 1 task structure disk slot and 1 capacitor charging slot - 18 real slots in total with no material storage. The bottom face is its only logistics side; task material access is opened by the later job systems.

## Energy

- Stores up to 160,000,000 FE, matching the Super Capacitor; the powered (internal FE above zero) and unpowered states use two block appearances
- While not full the station always requests 256 kW from its power grid and drops the request to zero when full; every gt it charges by the current power conversion efficiency (25,600 FE/gt by default)
- The capacitor slot shares the Plastic Molding Chamber semantics: a fully charged capacitor or super capacitor is consumed whole only when the remaining capacity can take all of it, at most one per gt; the empty shell stays in the freed slot, otherwise it returns to the operating player or drops beside the station
- Every docked drone below full charge receives energy from the station's internal FE at the 8 kW rate (800 FE/gt by default)
- While any station activity such as docking is running, a flat activity fee equal to 256 kW is deducted each gt, never stacking with concurrent activities
- Breaking and re-placing the station keeps its internal FE; slot contents and any drone in the middle of docking drop as items without losing data

## Top Bay and Recall

- The station body always keeps a full stable block collision; the top hatch never opens a hole players or entities could fall into
- The top bay is a single channel: only one drone docks at a time and each descent animation lasts 20 gt, so at most one drone per second
- The recall button in the screen orders world drones within 16 blocks to fly to the top bay one after another; when several return at once they queue in registration order, only the queue head approaches the bay while the rest hover in a 4x4 grid formation with 1 block spacing, 3 blocks above the station top, stacking an extra layer per 16 drones and shifting forward as the bay frees up; at a full station they keep waiting in formation and never overwrite stored items
- On docking the complete entity data is atomically transferred into a station slot while the client plays the descent with a collision-free parked display object
- If power is lost mid-activity, slots, escrowed data and animation progress are all kept while the model switches to the unpowered look; charging back up resumes from the same progress
- The undocking interface is reserved for the job coordinator and no manual undock is provided at this stage
