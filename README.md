# Kecong openTCS Vehicle Driver

openTCS Vehicle Driver (CommAdapter) for **Kecong (科聪) MRC/FRC series AGV controllers**, implementing the **Kecong UDP Interface Protocol V2.0 (xRobotProtocol)**.

## Overview

This driver integrates Kecong AGV controllers with the [openTCS](https://www.opentcs.org/) traffic control system. It translates openTCS transport orders and movement commands into Kecong's hybrid navigation protocol (laser + QR code), and reports vehicle state back to the openTCS kernel.

### Supported Features

| Feature | Status | Command |
|---------|--------|---------|
| Robot status polling | ✅ | `0xAF` |
| Subscription-based status push | ✅ | `0xB1` |
| Hybrid navigation (laser + QR code) | ✅ | `0xAE` |
| Path splicing (路径拼接) navigation | ✅ | `0xAE` mode 0 |
| Free navigation (自由导航) | ✅ | `0xAE` mode 1 |
| Immediate actions (pause/resume/cancel) | ✅ | `0xB2` |
| Pallet lift (托盘升降) | ✅ | `0xB2` with action `0x16` |
| Fork lift (叉齿升降) | ✅ | `0xB2` with action `0x12` |
| Cargo status query | ✅ | `0xB0` |
| Auto-mode initialization sequence | ✅ | `0x03`/`0x14`/`0x1F` |
| Error event reporting | ✅ | Parsed from `0xAF` |
| Traffic management | 🔜 | `0x70`/`0x71` (planned) |
| QR code-specific navigation | 🔜 | `0xF1`/`0xF5` (planned) |
| Magnetic navigation | 🔜 | `0xE0`-`0xE3` (planned) |

### Protocol Summary

| Parameter | Value |
|-----------|-------|
| Transport | UDP |
| Byte order | Little-endian (小端) |
| Communication mode | Request-Response |
| Navigation port | 17804 |
| Variable operations port | 17800 |
| Recommended poll interval | 100ms |
| Controller IP (direct) | 192.168.100.178 (laser) / 192.168.100.200 (QR) |
| Max data payload | 512 bytes |
| Frame header size | 28 bytes (0x1C) |

## Requirements

- **Java 11** or higher
- **Maven 3.6+**
- **openTCS 5.x** (tested with 5.11.0)
- Kecong controller with **navigation program V3.1.18+** and **firmware V5.0.46+**

## Quick Start

### 1. Build

```bash
git clone <this-repo>
cd kecong-opentcs-driver
mvn clean package
```

The compiled JAR will be at `target/kecong-opentcs-driver-1.0.0.jar`.

### 2. Install into openTCS

Copy the JAR to your openTCS Kernel's library directory:

```bash
cp target/kecong-opentcs-driver-1.0.0.jar /path/to/opentcs/kernel/lib/
```

### 3. Configure openTCS Kernel

Add the driver factory to your Kernel configuration (`kernelconfiguration.xml` or equivalent):

```xml
<Adapter>
    <factoryClass>com.kecong.opentcs.KecongCommAdapterFactory</factoryClass>
</Adapter>
```

### 4. Configure Vehicle Properties

For each Kecong AGV, add these properties in the openTCS plant model:

| Property | Required | Default | Description |
|----------|----------|---------|-------------|
| `kecong:authCode` | **Yes** | — | Protocol auth code (contact Kecong sales) |
| `kecong:host` | No | `192.168.100.178` | Controller IP address |
| `kecong:port` | No | `17804` | Navigation UDP port (hybrid/laser/status) |
| `kecong:varHost` | No | same as `host` | Variable/QR/Magnetic controller IP (defaults to host) |
| `kecong:varPort` | No | `17800` | Variable/QR/Magnetic UDP port |
| `kecong:pollInterval` | No | `100` | Status poll interval (ms) |

### 5. Point Name Convention

By default, the driver expects openTCS point names to be **numeric IDs** matching the Kecong map path point IDs. For example:
- Point named `"5"` → Kecong point ID `5`
- Point named `"120"` → Kecong point ID `120`

For custom mappings, add the property `kecong:pointId` to individual points.

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                   openTCS Kernel                      │
│  (Transport Orders, Routing, Traffic Management)      │
└──────────────────┬───────────────────────────────────┘
                   │ VehicleCommAdapter interface
┌──────────────────▼───────────────────────────────────┐
│              KecongCommAdapter                        │
│  ┌─────────────────────────────────────────────────┐ │
│  │ KecongVehicleProcessModel  (vehicle state)       │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ KecongUdpChannel  (UDP send/receive)             │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ KecongMessageEncoder / KecongMessageDecoder      │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ KecongProtocolFrame  (byte-level encode/decode)  │ │
│  └─────────────────────────────────────────────────┘ │
└──────────────────┬───────────────────────────────────┘
                   │ UDP :17804
┌──────────────────▼───────────────────────────────────┐
│              Kecong MRC/FRC Controller                │
│           (科聪控制器, xRobotProtocol V2.0)           │
└──────────────────────────────────────────────────────┘
```

### Class Diagram

```
KecongCommAdapterFactory          (implements VehicleCommAdapterFactory)
  └── creates KecongCommAdapter   (implements VehicleCommAdapter)
        ├── KecongVehicleProcessModel  (extends VehicleProcessModel)
        ├── KecongUdpChannel           (UDP communication)
        └── Protocol Layer
              ├── KecongProtocolFrame      (frame encode/decode)
              ├── KecongMessageEncoder     (request building)
              ├── KecongMessageDecoder     (response parsing)
              ├── KecongCommandCode        (command constants)
              ├── KecongExecutionCode      (error code constants)
              ├── KecongActionType         (action type constants)
              └── model/
                    ├── RobotStatus        (0xAF response model)
                    └── NavigationTask     (0xAE request model)
```

### Interaction Flow

```
openTCS Kernel                KecongCommAdapter              Kecong Controller
     │                              │                              │
     │──── enable() ───────────────►│                              │
     │                              │──── 0xB1 Subscribe ─────────►│
     │                              │◄─── ACK ─────────────────────│
     │                              │                              │
     │                              │──[Auto-mode init sequence]──►│
     │                              │  0x03 NaviControl=0 (manual) │
     │                              │  0x14 Manual position        │
     │                              │  0xAF Status poll (wait loc) │
     │                              │  0x1F Confirm position       │
     │                              │  0x03 NaviControl=1 (auto)   │
     │                              │                              │
     │                              │──[Polling loop]──────────────│
     │                              │  0xAF Query status (100ms)   │
     │                              │◄── RobotStatus ──────────────│
     │                              │                              │
     │──── sendCommand() ──────────►│                              │
     │                              │──── 0xAE Nav task ──────────►│
     │                              │◄─── ACK ─────────────────────│
     │                              │                              │
     │                              │── 0xAF Poll until complete ──│
     │                              │◄── status: orderId=0 (done)  │
     │◄─── commandExecuted() ───────│                              │
     │                              │                              │
     │──── disable() ──────────────►│                              │
     │                              │──── Close UDP ───────────────│
```

## Usage

### Movement Operations

The driver maps openTCS operations to Kecong actions:

| openTCS Operation | Kecong Action | Description |
|-------------------|---------------|-------------|
| `NOP` (no operation) | — | Move without action |
| `LOAD` / `PICKUP` | Pallet lift up (0x16 ↑) | Raise pallet |
| `UNLOAD` / `DROPOFF` | Pallet lift down (0x16 ↓) | Lower pallet |
| `CHARGE` | Variable write | Battery charging (custom) |

### Error Handling

The driver monitors robot status for error events:
- **Error-level** events (level 2): trigger `Vehicle.State.ERROR`
- **Warning-level** events (level 1): logged without state change
- Error codes are exposed via `KecongVehicleProcessModel.getErrorCodes()`

### Auto-Mode Initialization

On `enable()`, if the robot is not already in auto mode:
1. Switch to manual mode (`NaviControl = 0`)
2. Execute manual positioning (`0x14`)
3. Wait for localization to complete (up to ~6s, polling `0xAF`)
4. Confirm position (`0x1F`)
5. Switch to auto mode (`NaviControl = 1`)

> **Note:** Steps 1, 2, 4 can be skipped if the robot was shut down with correct localization and hasn't moved.

### Subscription Refresh

The driver registers a subscription (`0xB1`) for periodic status updates and refreshes it every 30 seconds before the 60-second duration expires.

## Development

### Project Structure

```
kecong-opentcs-driver/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/kecong/opentcs/
    │   ├── KecongCommAdapter.java           # Main comm adapter
    │   ├── KecongCommAdapterFactory.java    # openTCS factory
    │   ├── KecongVehicleProcessModel.java   # Vehicle state model
    │   ├── protocol/
    │   │   ├── KecongActionType.java         # Action type constants
    │   │   ├── KecongCommandCode.java        # Command code constants
    │   │   ├── KecongExecutionCode.java      # Execution code constants
    │   │   ├── KecongMessageDecoder.java     # Response parsing
    │   │   ├── KecongMessageEncoder.java     # Request building
    │   │   ├── KecongProtocolFrame.java      # Frame encode/decode
    │   │   ├── KecongUdpChannel.java         # UDP communication
    │   │   └── model/
    │   │       ├── NavigationTask.java       # 0xAE navigation task
    │   │       └── RobotStatus.java          # 0xAF robot status
    │   └── util/
    │       └── ByteBufferUtils.java          # Little-endian helpers
    └── test/java/com/kecong/opentcs/
        ├── KecongVehicleProcessModelTest.java
        ├── protocol/
        │   ├── KecongCommandCodeTest.java
        │   ├── KecongExecutionCodeTest.java
        │   ├── KecongMessageDecoderTest.java
        │   ├── KecongMessageEncoderTest.java
        │   ├── KecongProtocolFrameTest.java
        │   └── model/
        │       ├── NavigationTaskTest.java
        │       └── RobotStatusTest.java
        ├── util/
        │   └── ByteBufferUtilsTest.java
        └── integration/
            └── ProtocolStackIntegrationTest.java
```

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage report
mvn test jacoco:report

# Run a specific test class
mvn test -Dtest=KecongProtocolFrameTest

# Run integration tests only
mvn test -Dtest="*Integration*"
```

### Coverage Target

Aim for **≥90% line coverage**. Current coverage report:

```bash
# Generate and view
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

## Configuration Reference

### System Properties

All properties can also be set via Java system properties (using the full key like `kecong:host`):

```bash
java -Dkecong:host=192.168.1.100 -Dkecong:authCode=YOUR_AUTH_CODE ...
```

### Vehicle Integration Example

```xml
<!-- openTCS plant model excerpt -->
<vehicle name="AGV-001" ...>
    <property key="kecong:host" value="192.168.100.178"/>
    <property key="kecong:port" value="17804"/>
    <property key="kecong:varHost" value="192.168.100.200"/>
    <property key="kecong:varPort" value="17800"/>
    <property key="kecong:authCode" value="YOUR_AUTH_CODE_FROM_KECONG"/>
    <property key="kecong:pollInterval" value="100"/>
</vehicle>
```

## Troubleshooting

### Robot not responding

1. Verify the controller is reachable: `ping 192.168.100.178`
2. Check the auth code is correct (contact Kecong sales)
3. Verify the robot is in the correct navigation mode (laser vs QR)
4. Check that the navigation program version is ≥ V3.1.18

### "Auth code error" (0xFF)

The protocol auth code doesn't match. Ensure you're using the correct 16-byte auth code provided by Kecong.

### "Path point count exceeded" (0x83)

The navigation task has too many path points. Split the route into smaller tasks or check the controller configuration.

### "Nav state conflict" (0x80)

The controller can't execute the command in its current state. Check:
- Is the robot in auto mode? (`0xAF` → workMode=3)
- Is localization complete? (`0xAF` → localizationStatus=3)
- Is there an active task? (`0xAF` → orderId≠0)

## References

- [openTCS Documentation](https://www.opentcs.org/docs/)
- [Kecong Robotics (科聪)](https://www.kecongrobotics.com/)
- Kecong UDP Interface Protocol V2.0 Manual (科聪控制器UDP接口协议说明书V2.0)

## License

MIT License. See LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Write tests and ensure coverage
4. Submit a pull request

---

**Made for Kecong MRC/FRC series controllers · Compatible with openTCS 5.x**
