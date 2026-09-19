# Designing messages

Six data sources at Nordwind. Fill in your group's decisions.

## 1. Kind of message

State, delta, event or command? One sentence on why.

| Source | Kind | Why |
|---|---|---|
| A Telemetry | | |
| B Master data | | |
| C Status change | | |
| D Maintenance order | | |
| E Curtailment | | |
| F Drone inspection | | |

## 2. Key and topic

Topic names follow `[org].[domain].[visibility].[entity].[type]`.

| Source | Key | Topic | `.event` or `.state`? |
|---|---|---|---|
| A Telemetry | | | |
| B Master data | | | |
| C Status change | | | |
| D Maintenance order | | | |
| E Curtailment | | | |
| F Drone inspection | | | |

Your suffix for commands: ______

## 3. What goes into the record

Take the status change (C) and place each piece of information.

| Information | Key, value, headers or timestamp? | Why |
|---|---|---|
| Turbine id | | |
| Error code and error text | | |
| When the turbine reported the fault | | |
| Name of the writing system | | |
| Version of the message format | | |
| Id for tracing the fault | | |

## 4. The drone photo

What goes into the topic instead of the photo?
