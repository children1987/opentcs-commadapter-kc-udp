package com.kecong.opentcs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>配置来源：Vehicle properties（openTCS 模型 XML），修改后需重启 Kernel。</p>
 */
public class KecongEnergyConfig {

    private static final Logger LOG = LoggerFactory.getLogger(KecongEnergyConfig.class);
    private static final String PROP_PREFIX = "kecong:";

    public enum Source { PROTOCOL, READ_VAR, READ_MULTI_VAR }

    private Source source = Source.PROTOCOL;
    private String varName;
    private int varOffset;
    private String varPort = "NAV";

    // ---- getters ----

    public Source getSource()          { return source; }
    public String getVarName()         { return varName; }
    public int getVarOffset()          { return varOffset; }
    public String getVarPort()         { return varPort != null ? varPort : "NAV"; }

    // ---- setters ----

    public void setSource(Source s)    { this.source = s; }
    public void setVarName(String v)   { this.varName = v; }
    public void setVarOffset(int o)    { this.varOffset = o; }
    public void setVarPort(String p)   { this.varPort = p; }

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

        LOG.info("电量配置: source={} varName={} varOffset={} varPort={}",
                c.source, c.varName, c.varOffset, c.varPort);
        return c;
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
