# opentcs-commadapter-kc-udp

科聪 UDP 通讯适配器（VehicleCommAdapter 实现），配合 `simulators/kc-simulator` 调试。

## 编译后部署

修改代码后：

```bash
cd commadapters/opentcs-commadapter-kc-udp
mvn clean package -DskipTests
cp protocol/target/kecong-opentcs-protocol-1.0.0.jar \
   adapter/target/kecong-opentcs-adapter-1.0.0.jar \
   ../opentcs-7.3.0-bin/opentcs-kernel/lib/openTCS-extensions/
```

重启 Kernel 生效。
