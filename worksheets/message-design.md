# Designing messages

Your team's sheet for the whole lab. Fill it in as you go, keep it: the topic
names you decide here come back in every later lab.

## 1. Kind of message

State, delta, event or command? One sentence on why — for some sources more
than one answer is defensible.

| Source | Kind | Why |
|---|---|---|
| A Telemetry, every second per turbine | | |
| B Master data, changes rarely | | |
| C Status change with error code | | |
| D Maintenance order to the service team | | |
| E Curtailment demanded by the grid operator | | |
| F Drone inspection photos, 5–20 MB | | |

## 2. Key and topic

`[org].[domain].[visibility].[entity].[type]` — two are already fixed:

- `nordwind.scada.public.turbine-telemetry.event` (history, `cleanup.policy=delete`)
- `nordwind.assets.public.turbine-registry.state` (latest value per key, `cleanup.policy=compact`)

| Source | Key | Topic name | `.event` or `.state`? |
|---|---|---|---|
| A Telemetry | | | |
| B Master data | | | |
| C Status change | | | |
| D Maintenance order | | | |
| E Curtailment | | | |
| F Drone inspection | | | |

Our suffix for commands: ______

Why that one:

## 3. What goes into the record

Take the status change (C).

| Information | Key, value, header or timestamp? | Why |
|---|---|---|
| Turbine id | | |
| Error code and error text | | |
| When the turbine reported the fault | | |
| Name of the writing system | | |
| Version of the message format | | |
| Id for tracing the fault | | |

The record timestamp is: ______

## 4. The drone photo

What we saw when we wrote 1.5 MB:

What goes into the topic instead of the photo:

## Notes for later labs

Topics we created:

Open questions for the trainer:
