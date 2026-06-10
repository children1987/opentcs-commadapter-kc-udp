# 0x02/0x03 变量读写 — 联调经验

> 文档定位：记录 0x02（读多变量）和 0x03（写多变量）的协议格式、实车验证、Java 实现、常见坑位。
> 最后更新：2026-06-10（修正 count 为 U32、DataLen 为 U32、0x03 无 ValueID）

## ⚠️ 协议文档勘误

《科聪控制器UDP接口协议说明书V2.0》§5.1.3/5.1.4 与实际控制器行为存在以下偏差：

| 项目 | 文档描述 | 实际行为（实车验证） |
|------|---------|-------------------|
| count 类型 | U8 + 3B 预留 | **U32**（4 字节） |
| 0x02 头 | [U8 count][3B rsv][U32 VID] | **[U32 count][U32 VID]** |
| 0x03 头 | 同 0x02 | **[U32 count]** — **无 ValueID** |
| 应答 DataLen | U16 + 2B 预留 | **U32**（4 字节） |

## 1. 协议格式（经实车验证）

端口 **17800**（QR/变量端口，IP: 192.168.100.200）

### 1.1 0x02 读多变量

**请求**：
```
[U32 count][U32 ValueID][StrValue × N]

StrValue:
  [U8×16 变量名][U32 成员数量][ValueMember × M]
ValueMember:
  [U16 偏移][U16 长度]
```

**应答**：
```
[U32 ValueID][U32 数据总长][U8[] 变量值（4字节对齐紧凑排列）]
```

### 1.2 0x03 写多变量

**请求**（注意：**无 ValueID 字段**）：
```
[U32 count][StrValue × N]

StrValue:
  [U8×16 变量名][U32 成员数量][ValueMember × M]
ValueMember:
  [U16 偏移][U16 长度][U32 值]
```

**应答**：无数据载荷（exec=0x00 表示成功）

## 2. 实车验证

### 2.1 0x02 读取 AAA（2026-06-10）

**请求**（与科聪 UDP 调试工具输出一致）：
```
01 00 00 00  ← count=1 (U32)
00 00 00 00  ← ValueID=0 (U32)
41 41 41 00 00 00 00 00 00 00 00 00 00 00 00 00  ← "AAA"
02 00 00 00  ← memberCount=2 (U32)
00 00 02 00  ← member1: offset=0, len=2
02 00 02 00  ← member2: offset=2, len=2
```

**应答**：
```
00 00 00 00  ← ValueID=0 (U32)
10 00 00 00  ← DataLen=16 (U32, 不是 U16!)
4f 01 00 00  ← AAA[0] INT16 = 335 (4B aligned)
50 01 00 00  ← AAA[2] UINT16 = 336 (4B aligned)
```

### 2.2 0x03 写入 AAA（2026-06-10）

**请求**（注意：无 ValueID）：
```
01 00 00 00  ← count=1 (U32) — 没有 ValueID!
41 41 41 00 00 00 00 00 00 00 00 00 00 00 00 00  ← "AAA"
02 00 00 00  ← memberCount=2 (U32)
00 00 02 00 4f 01 00 00  ← member1: offset=0, len=2, val=335
02 00 02 00 50 01 00 00  ← member2: offset=2, len=2, val=336
```

**应答**：exec=0x00, dataLen=0

## 3. Python 工具用法

```bash
# 读取变量成员
python kc-tools/kc-inspect.py --read-var AAA 0 2 2 2

# 写入变量成员（自动回读验证）
python kc-tools/kc-inspect.py --write-var AAA 0 2 333 2 2 334
```

## 4. Java 实现

### 4.1 代码位置

| 文件 | 内容 |
|------|------|
| `protocol/.../model/VarReadRequest.java` | 0x02 请求模型 |
| `protocol/.../model/VarReadResponse.java` | 0x02 应答解析（U32 DataLen） |
| `protocol/.../model/VarWriteRequest.java` | 0x03 请求模型 |
| `protocol/.../model/VarWriteMember.java` | 0x03 写入成员 |
| `protocol/.../KecongMessageEncoder.java` | `encodeReadMultiVar()` + `encodeWriteMultiVar()` |

### 4.2 使用示例

```java
// 0x02 读取
VarReadRequest req = new VarReadRequest("AAA",
    Arrays.asList(new VarMember(0, 2), new VarMember(2, 2)));
byte[] data = KecongMessageEncoder.encodeReadMultiVar(
    Collections.singletonList(req), 0);
byte[] resp = qrChannel.sendAndGetData(
    KecongCommandCode.CMD_READ_MULTI_VAR, data);
VarReadResponse result = VarReadResponse.decode(resp);
int v1 = result.getInt(0);            // 4B aligned
int v2 = result.getUnsignedShort(4);   // 4B aligned

// 0x03 写入
VarWriteRequest wreq = new VarWriteRequest("AAA",
    Arrays.asList(new VarWriteMember(0, 2, 333),
                  new VarWriteMember(2, 2, 334)));
byte[] wdata = KecongMessageEncoder.encodeWriteMultiVar(
    Collections.singletonList(wreq));
qrChannel.sendAndVerify(KecongCommandCode.CMD_WRITE_MULTI_VAR, wdata);
```

## 5. 常见坑位

### 5.1 count 是 U32 不是 U8！
**错误**：`struct.pack('<B', 1)` → `01`
**正确**：`struct.pack('<I', 1)` → `01 00 00 00`

### 5.2 0x03 没有 ValueID
0x02 有 8 字节头（count + ValueID），0x03 只有 4 字节头（count）。混用会导致写不进去。

### 5.3 应答 DataLen 是 U32 不是 U16
解析响应时用 U32 读取 DataLen，否则会错位。

### 5.4 端口/IP 分离
NAV: `192.168.100.178:17804`，QR: `192.168.100.200:17800`

## 6. 相关文件索引

| 文件 | 说明 |
|------|------|
| [VarReadRequest.java](../protocol/src/main/java/com/kecong/opentcs/protocol/model/VarReadRequest.java) | 0x02 请求 |
| [VarReadResponse.java](../protocol/src/main/java/com/kecong/opentcs/protocol/model/VarReadResponse.java) | 0x02 应答 |
| [VarWriteRequest.java](../protocol/src/main/java/com/kecong/opentcs/protocol/model/VarWriteRequest.java) | 0x03 请求 |
| [VarWriteMember.java](../protocol/src/main/java/com/kecong/opentcs/protocol/model/VarWriteMember.java) | 0x03 成员 |
| [KecongMessageEncoder.java](../protocol/src/main/java/com/kecong/opentcs/protocol/KecongMessageEncoder.java) | 编解码 |
| [KecongReadMultiVarTest.java](../protocol/src/test/java/com/kecong/opentcs/protocol/KecongReadMultiVarTest.java) | 单元测试 |
| [kc-inspect.py](../../kc-tools/kc-inspect.py) | Python 调试工具 |
