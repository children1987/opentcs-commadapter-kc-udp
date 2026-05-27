# 科聪 openTCS 车辆驱动

[![codecov](https://codecov.io/gh/children1987/opentcs-commadapter-kc-udp/branch/master/graph/badge.svg)](https://codecov.io/gh/children1987/opentcs-commadapter-kc-udp)
[![Java CI](https://github.com/children1987/opentcs-commadapter-kc-udp/actions/workflows/maven-test.yml/badge.svg)](https://github.com/children1987/opentcs-commadapter-kc-udp/actions/workflows/maven-test.yml)
[![Security Scan](https://github.com/children1987/opentcs-commadapter-kc-udp/actions/workflows/security.yml/badge.svg)](https://github.com/children1987/opentcs-commadapter-kc-udp/actions/workflows/security.yml)

适用于 **科聪 MRC/FRC 系列 AGV 控制器** 的 openTCS 车辆驱动（CommAdapter），实现了 **科聪 UDP 接口协议 V2.0 (xRobotProtocol)**。

## 概述

本驱动将科聪 AGV 控制器接入 [openTCS](https://www.opentcs.org/) 交通控制系统。它将 openTCS 的运输任务和运动指令翻译为科聪的混合导航协议（激光 + 二维码），并将车辆状态上报给 openTCS 内核。

### 支持的功能

| 功能 | 状态 | 命令码 |
|------|------|--------|
| 机器人状态轮询 | ✅ | `0xAF` |
| 订阅式状态推送 | ✅ | `0xB1` |
| 混合导航（激光 + 二维码） | ✅ | `0xAE` |
| 路径拼接导航 | ✅ | `0xAE` mode 0 |
| 自由导航 | ✅ | `0xAE` mode 1 |
| 即时操作（暂停/恢复/取消） | ✅ | `0xB2` |
| 托盘升降 | ✅ | `0xB2` action `0x16` |
| 叉齿升降 | ✅ | `0xB2` action `0x12` |
| 货物状态查询 | ✅ | `0xB0` |
| 自动模式初始化序列 | ✅ | `0x03`/`0x14`/`0x1F` |
| 错误事件上报 | ✅ | 从 `0xAF` 解析 |
| 交通管理 | ✅ | `0x70`/`0x71` |
| 二维码专用导航 | ✅ | `0xF1`/`0xF5` |
| 磁导航 | ✅ | `0xE0`-`0xE3` |

#### 交通管理 (0x70/0x71)

基于资源的交通控制 —— 车辆在行进前请求路径占用权。

| 方向 | 命令码 | 说明 |
|------|--------|------|
| 请求 → | `0x70` | 调度器轮询车辆的待处理资源请求 |
| ← 响应 | `0x70` | 车辆返回最多 16 条路径条目（路径 ID + 端点 ID） |
| 请求 → | `0x71` | 调度器通知成功/失败及已授权的路径 |
| ← 响应 | `0x71` | 空（确认应答） |

#### 二维码导航 (0xF1/0xF5)

通过地面上的二维码标签导航，支持长路径批量下发。

| 命令码 | 用途 | 最大段数 | 响应 |
|--------|------|----------|------|
| `0xF1` | 简单短任务 | 30（单批次） | 无 |
| `0xF5` | 长路径（工厂级） | 共 2048，每批 30 | 回传任务元数据 |

**端口**：17800（`qrPort`）

#### 磁导航 (0xE0–0xE3)

适用于沿磁条或磁钉行驶的 AGV。

| 命令码 | 方向 | 说明 |
|--------|------|------|
| `0xE0` | → | 任务下发（路段 + 标记点/地标） |
| `0xE1` | → | 任务控制（暂停/恢复/取消/启动/清除故障） |
| `0xE2` | ← | 车辆状态（运行状态、路段 ID、位置、航向） |
| `0xE3` | → | 车辆重定位到指定路段 + 偏移 |

### 协议概要

| 参数 | 值 |
|------|----|
| 传输层 | UDP |
| 字节序 | 小端 (Little-endian) |
| 通信模式 | 请求-响应 |
| 导航端口 | 17804 |
| 变量操作端口 | 17800 |
| 推荐轮询间隔 | 100ms |
| 控制器 IP（直连） | 192.168.100.178（激光）/ 192.168.100.200（二维码） |
| 最大数据载荷 | 512 字节 |
| 帧头大小 | 28 字节 (0x1C) |

## 环境要求

- **Java 11** 或更高版本
- **Maven 3.6+**
- **openTCS 7.x**（已测试 7.2.1）
- 科聪控制器，**导航程序 V3.1.18+** 及 **固件 V5.0.46+**

## 快速开始

### 1. 编译

```bash
git clone <本仓库>
cd kecong-opentcs-driver
mvn clean package
```

编译后的 JAR 位于 `target/kecong-opentcs-driver-1.0.0.jar`。

### 2. 安装到 openTCS

将 JAR 复制到 openTCS 内核的库目录：

```bash
cp target/kecong-opentcs-driver-1.0.0.jar /path/to/opentcs/kernel/lib/
```

### 3. 配置 openTCS 内核

在内核配置文件（`kernelconfiguration.xml` 或等效文件）中添加驱动工厂：

```xml
<Adapter>
    <factoryClass>com.kecong.opentcs.KecongCommAdapterFactory</factoryClass>
</Adapter>
```

### 4. 配置车辆属性

在 openTCS 工厂模型中为每台科聪 AGV 添加以下属性：

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `kecong:authCode` | **是** | — | 协议认证码（联系科聪销售获取） |
| `kecong:navHost` | 否 | `192.168.100.178` | 激光/混合导航 IP（算法单元） |
| `kecong:navPort` | 否 | `17804` | 激光/混合导航 UDP 端口 |
| `kecong:qrHost` | 否 | `192.168.100.200` | 二维码/磁导航 IP（逻辑单元） |
| `kecong:qrPort` | 否 | `17800` | 二维码/磁导航 UDP 端口 |
| `kecong:pollInterval` | 否 | `100` | 状态轮询间隔（ms） |

### 5. 路点命名约定

默认情况下，驱动期望 openTCS 的路点名称为**数字 ID**，与科聪地图中的路径点 ID 对应。例如：
- 名称为 `"5"` 的路点 → 科聪点 ID `5`
- 名称为 `"120"` 的路点 → 科聪点 ID `120`

如需自定义映射，在路点上添加属性 `kecong:pointId`。

### 车辆属性（导航单元）

| 属性 | 默认值 | 适用场景 |
|------|--------|----------|
| `kecong:qrHost` | `192.168.100.200` | 二维码/磁导航（控制器使用不同 IP 时） |
| `kecong:qrPort` | `17800` | 二维码/磁导航 |

## 架构

```
┌──────────────────────────────────────────────────────┐
│                   openTCS 内核                         │
│         （运输任务、路径规划、交通管理）                    │
└──────────────────┬───────────────────────────────────┘
                   │ VehicleCommAdapter 接口
┌──────────────────▼───────────────────────────────────┐
│              KecongCommAdapter                        │
│  ┌─────────────────────────────────────────────────┐ │
│  │ KecongVehicleProcessModel  （车辆状态模型）        │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ KecongUdpChannel  （UDP 收发）                    │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ KecongMessageEncoder / KecongMessageDecoder      │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │ KecongProtocolFrame  （字节级编解码）              │ │
│  └─────────────────────────────────────────────────┘ │
└──────────────────┬───────────────────────────────────┘
                   │ UDP :17804
┌──────────────────▼───────────────────────────────────┐
│              科聪 MRC/FRC 控制器                        │
│            （xRobotProtocol V2.0）                    │
└──────────────────────────────────────────────────────┘
```

### 类图

```
KecongCommAdapterFactory          (实现 VehicleCommAdapterFactory)
  └── 创建 KecongCommAdapter      (实现 VehicleCommAdapter)
        ├── KecongVehicleProcessModel  (继承 VehicleProcessModel)
        ├── KecongUdpChannel           (UDP 通信)
        └── 协议层
              ├── KecongProtocolFrame      (帧编解码)
              ├── KecongMessageEncoder     (请求构建)
              ├── KecongMessageDecoder     (响应解析)
              ├── KecongCommandCode        (命令码常量)
              ├── KecongExecutionCode      (错误码常量)
              ├── KecongActionType         (操作类型常量)
              └── model/
                    ├── RobotStatus        (0xAF 响应模型)
                    └── NavigationTask     (0xAE 请求模型)
```

### 交互流程

```
openTCS 内核                 科聪驱动                        科聪控制器
     │                          │                              │
     │──── enable() ───────────►│                              │
     │                          │──── 0xB1 订阅 ─────────────►│
     │                          │◄─── ACK ─────────────────────│
     │                          │                              │
     │                          │──[自动模式初始化]────────────►│
     │                          │  0x03 NaviControl=0（手动）   │
     │                          │  0x14 手动定位               │
     │                          │  0xAF 轮询状态（等待定位）     │
     │                          │  0x1F 确认位置               │
     │                          │  0x03 NaviControl=1（自动）   │
     │                          │                              │
     │                          │──[轮询循环]──────────────────│
     │                          │  0xAF 查询状态（100ms）       │
     │                          │◄── RobotStatus ──────────────│
     │                          │                              │
     │──── sendCommand() ──────►│                              │
     │                          │──── 0xAE 导航任务 ──────────►│
     │                          │◄─── ACK ─────────────────────│
     │                          │                              │
     │                          │── 0xAF 持续轮询至完成 ───────│
     │                          │◄── status: orderId=0（完成）  │
     │◄─── commandExecuted() ───│                              │
     │                          │                              │
     │──── disable() ──────────►│                              │
     │                          │──── 关闭 UDP ────────────────│
```

## 使用说明

### 运动操作

驱动将 openTCS 操作映射为科聪动作：

| openTCS 操作 | 科聪动作 | 说明 |
|-------------|---------|------|
| `NOP`（无操作） | — | 移动但不执行动作 |
| `LOAD` / `PICKUP` | 托盘升起 (0x16 ↑) | 升起托盘 |
| `UNLOAD` / `DROPOFF` | 托盘下降 (0x16 ↓) | 降下托盘 |
| `CHARGE` | 变量写入 | 电池充电（自定义） |

### 错误处理

驱动监控机器人状态以捕获错误事件：
- **错误级**事件（level 2）：触发 `Vehicle.State.ERROR`
- **警告级**事件（level 1）：仅记录日志，不改变状态
- 错误码通过 `KecongVehicleProcessModel.getErrorCodes()` 暴露

### 自动模式初始化

调用 `enable()` 时，若机器人未处于自动模式：
1. 切换到手动模式（`NaviControl = 0`）
2. 执行手动定位（`0x14`）
3. 等待定位完成（约 6 秒，轮询 `0xAF`）
4. 确认位置（`0x1F`）
5. 切换到自动模式（`NaviControl = 1`）

> **注意：** 如果机器人关机时已完成正确定位且未移动，可跳过步骤 1、2、4。

### 订阅刷新

驱动通过 `0xB1` 注册订阅以获取周期性状态更新，并在 60 秒有效期到期前每 30 秒刷新一次。

## 开发

### 项目结构

```
kecong-opentcs-driver/
├── pom.xml
├── README.md
├── README_zh.md
└── src/
    ├── main/java/com/kecong/opentcs/
    │   ├── KecongCommAdapter.java           # 主通信适配器
    │   ├── KecongCommAdapterFactory.java    # openTCS 工厂
    │   ├── KecongVehicleProcessModel.java   # 车辆状态模型
    │   ├── protocol/
    │   │   ├── KecongActionType.java         # 操作类型常量
    │   │   ├── KecongCommandCode.java        # 命令码常量
    │   │   ├── KecongExecutionCode.java      # 错误码常量
    │   │   ├── KecongMessageDecoder.java     # 响应解析
    │   │   ├── KecongMessageEncoder.java     # 请求构建
    │   │   ├── KecongProtocolFrame.java      # 帧编解码
    │   │   ├── KecongUdpChannel.java         # UDP 通信
    │   │   └── model/
    │   │       ├── MagneticNavTask.java     # 0xE0-E3 磁导航
    │   │       ├── NavigationTask.java       # 0xAE 导航任务
    │   │       ├── QrNavigationTask.java     # 0xF1/F5 二维码导航
    │   │       ├── RobotStatus.java          # 0xAF 机器人状态
    │   │       └── TrafficResource.java      # 0x70/0x71 交通管理
    │   └── util/
    │       └── ByteBufferUtils.java          # 小端序工具
    └── test/java/com/kecong/opentcs/
        ├── KecongVehicleProcessModelTest.java
        ├── protocol/
        │   ├── KecongCommandCodeTest.java
        │   ├── KecongExecutionCodeTest.java
        │   ├── KecongMessageDecoderTest.java
        │   ├── KecongMessageEncoderTest.java
        │   ├── KecongProtocolFrameTest.java
        │   └── model/
        │       ├── MagneticNavTaskTest.java
        │       ├── NavigationTaskTest.java
        │       ├── QrNavigationTaskTest.java
        │       ├── RobotStatusTest.java
        │       └── TrafficResourceTest.java
        ├── util/
        │   └── ByteBufferUtilsTest.java
        └── integration/
            └── ProtocolStackIntegrationTest.java
```

### 运行测试

```bash
# 运行全部测试
mvn test

# 生成覆盖率报告
mvn test jacoco:report

# 运行指定测试类
mvn test -Dtest=KecongProtocolFrameTest

# 仅运行集成测试
mvn test -Dtest="*Integration*"
```

### 覆盖率目标

目标 **≥90% 行覆盖率**。查看当前覆盖率：

```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

## 配置参考

### 系统属性

所有属性也可通过 Java 系统属性设置（使用完整键名如 `kecong:navHost`）：

```bash
java -Dkecong:navHost=192.168.1.100 -Dkecong:authCode=YOUR_AUTH_CODE ...
```

### 车辆集成示例

```xml
<!-- openTCS 工厂模型片段 -->
<vehicle name="AGV-001" ...>
    <property key="kecong:navHost" value="192.168.100.178"/>
    <property key="kecong:navPort" value="17804"/>
    <property key="kecong:qrHost" value="192.168.100.200"/>
    <property key="kecong:qrPort" value="17800"/>
    <property key="kecong:authCode" value="YOUR_AUTH_CODE_FROM_KECONG"/>
    <property key="kecong:pollInterval" value="100"/>
</vehicle>
```

## 故障排查

### 控制器无响应

1. 确认控制器可达：`ping 192.168.100.178`
2. 检查认证码是否正确（联系科聪销售获取）
3. 确认机器人处于正确的导航模式（激光 vs 二维码）
4. 确认导航程序版本 ≥ V3.1.18

### "认证码错误" (0xFF)

协议认证码不匹配。请确保使用科聪提供的正确 16 字节认证码。

### "路径点数超限" (0x83)

导航任务的路径点过多。请将路线拆分为多个任务，或检查控制器配置。

### "导航状态冲突" (0x80)

控制器无法在当前状态下执行指令。请检查：
- 机器人是否处于自动模式？（`0xAF` → workMode=3）
- 定位是否完成？（`0xAF` → localizationStatus=3）
- 是否有正在执行的任务？（`0xAF` → orderId≠0）

## 参考资料

- [openTCS 文档](https://www.opentcs.org/docs/)
- [科聪机器人](https://www.kecongrobotics.com/)
- 科聪 UDP 接口协议说明书 V2.0

## 许可证

MIT 许可证，详见 LICENSE 文件。

## 贡献指南

1. Fork 本仓库
2. 创建功能分支
3. 编写测试并确保覆盖率
4. 提交 Pull Request

---

[English](./README.md) | 中文
