package com.kecong.opentcs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 电池电量读取策略配置。
 *
 * <p>三种模式：</p>
 * <ul>
 *   <li>{@code PROTOCOL} — 从 0x17 协议响应中直接获取（默认，无需额外配置）</li>
 *   <li>{@code READ_VAR} — 通过 0x01 读取单个控制器变量</li>
 *   <li>{@code READ_MULTI_VAR} — 通过 0x02 批量读取，按偏移量提取</li>
 * </ul>
 *
 * <p>配置来源（优先级从高到低）：</p>
 * <ol>
 *   <li>JSON 热加载文件（{@code energyConfigPath}）— 改后数秒自动生效</li>
 *   <li>Vehicle properties（openTCS 模型 XML）— 需重启 Kernel</li>
 * </ol>
 */
public class KecongEnergyConfig {

    private static final Logger LOG = LoggerFactory.getLogger(KecongEnergyConfig.class);
    private static final String PROP_PREFIX = "kecong:";

    public enum Source { PROTOCOL, READ_VAR, READ_MULTI_VAR }

    private Source source = Source.PROTOCOL;
    private String varName;
    private int varOffset;
    private String varPort = "NAV";
    private Path configFilePath;
    private long lastFileMtime;

    // ---- getters ----

    public Source getSource()          { return source; }
    public String getVarName()         { return varName; }
    public int getVarOffset()          { return varOffset; }
    public String getVarPort()         { return varPort != null ? varPort : "NAV"; }
    public Path getConfigFilePath()    { return configFilePath; }

    // ---- setters ----

    public void setSource(Source s)    { this.source = s; }
    public void setVarName(String v)   { this.varName = v; }
    public void setVarOffset(int o)    { this.varOffset = o; }
    public void setVarPort(String p)   { this.varPort = p; }
    public void setConfigFilePath(Path p) { this.configFilePath = p; }

    // ---- factory ----

    /**
     * 从车辆属性构建配置。
     *
     * <p>识别属性（{@code kecong:} 前缀）：</p>
     * <ul>
     *   <li>{@code energySource} — PROTOCOL | READ_VAR | READ_MULTI_VAR</li>
     *   <li>{@code energyVarName} — 变量名</li>
     *   <li>{@code energyVarOffset} — 偏移量（仅 READ_MULTI_VAR）</li>
     *   <li>{@code energyVarPort} — NAV | QR</li>
     *   <li>{@code energyConfigPath} — JSON 热加载文件路径</li>
     * </ul>
     */
    public static KecongEnergyConfig fromVehicleProperties(Map<String, String> props) {
        KecongEnergyConfig c = new KecongEnergyConfig();

        String src = prop(props, "energySource", "PROTOCOL");
        try { c.source = Source.valueOf(src.toUpperCase()); }
        catch (IllegalArgumentException e) { LOG.warn("无效的 energySource '{}'，回退到 PROTOCOL", src); }

        c.varName   = prop(props, "energyVarName", null);
        c.varOffset = parseIntSafe(prop(props, "energyVarOffset", "0"));
        c.varPort   = prop(props, "energyVarPort", "NAV").toUpperCase();

        if ((c.source == Source.READ_VAR || c.source == Source.READ_MULTI_VAR)
                && (c.varName == null || c.varName.isEmpty())) {
            LOG.warn("{} 模式未配置 energyVarName，回退到 PROTOCOL", c.source);
            c.source = Source.PROTOCOL;
        }

        String cfgPath = prop(props, "energyConfigPath", null);
        if (cfgPath != null && !cfgPath.isEmpty()) {
            c.configFilePath = Paths.get(cfgPath);
        }

        LOG.info("电量配置: source={} varName={} varOffset={} varPort={} hotReload={}",
                c.source, c.varName, c.varOffset, c.varPort,
                c.configFilePath != null ? c.configFilePath : "无");
        return c;
    }

    // ---- 热加载 ----

    /**
     * 若 JSON 配置文件自上次读取后有修改，则重新加载。
     * 每轮询周期调用一次，内部做 mtime 比较，无变化时近乎零开销。
     *
     * @return true 表示配置已更新
     */
    public boolean reloadFromJsonFile() {
        if (configFilePath == null || !Files.exists(configFilePath)) return false;
        try {
            long mtime = Files.getLastModifiedTime(configFilePath).toMillis();
            if (mtime == lastFileMtime) return false;
            lastFileMtime = mtime;

            String json = new String(Files.readAllBytes(configFilePath), StandardCharsets.UTF_8);
            parseJson(json);
            LOG.info("电量配置已热加载 ({}): source={} varName={} varOffset={} varPort={}",
                    configFilePath.getFileName(), source, varName, varOffset, varPort);
            return true;
        } catch (IOException e) {
            LOG.warn("热加载失败 {}: {}", configFilePath, e.getMessage());
            return false;
        }
    }

    private void parseJson(String json) {
        String s = extractStr(json, "energySource");
        if (s != null) try { source = Source.valueOf(s.toUpperCase()); } catch (IllegalArgumentException ignored) {}

        s = extractStr(json, "energyVarName");  if (s != null) varName = s;
        s = extractStr(json, "energyVarPort");  if (s != null) varPort = s.toUpperCase();

        Integer n = extractInt(json, "energyVarOffset"); if (n != null) varOffset = n;
    }

    // ---- 简易 JSON 解析（无外部依赖） ----

    private static String extractStr(String json, String key) {
        int ki = json.indexOf('"' + key + '"');
        if (ki < 0) return null;
        int ci = json.indexOf(':', ki + key.length() + 2);
        if (ci < 0) return null;
        int q1 = json.indexOf('"', ci + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static Integer extractInt(String json, String key) {
        int ki = json.indexOf('"' + key + '"');
        if (ki < 0) return null;
        int ci = json.indexOf(':', ki + key.length() + 2);
        if (ci < 0) return null;
        int i = ci + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        int j = i;
        if (j < json.length() && json.charAt(j) == '-') j++;
        while (j < json.length() && Character.isDigit(json.charAt(j))) j++;
        if (j == i) return null;
        try { return Integer.parseInt(json.substring(i, j)); }
        catch (NumberFormatException e) { return null; }
    }

    /** 解析整数，支持 0x/0X 十六进制前缀 */
    private static int parseIntSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) return Integer.parseInt(s.substring(2), 16);
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String prop(Map<String, String> props, String key, String def) {
        String v = props.get(PROP_PREFIX + key);
        return (v != null && !v.isEmpty()) ? v : def;
    }
}
