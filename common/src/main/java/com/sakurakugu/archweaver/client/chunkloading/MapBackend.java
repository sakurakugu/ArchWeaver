package com.sakurakugu.archweaver.client.chunkloading;

import java.util.Set;

/** 地图选择是纯客户端偏好，缺失提供者时只临时回退，不改写用户选择。 */
public enum MapBackend {
    BUILTIN("backend_builtin"), JOURNEYMAP("backend_journey"), XAERO("backend_xaero");

    private final String labelKey;
    MapBackend(String key) { labelKey = "gui.archweaver.chunkloader." + key; }
    public String labelKey() { return labelKey; }

    public static MapBackend resolve(MapBackend preferred, Set<MapBackend> installed) {
        return installed.contains(preferred) ? preferred : BUILTIN;
    }
}
