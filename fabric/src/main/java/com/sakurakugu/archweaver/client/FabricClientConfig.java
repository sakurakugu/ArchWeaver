package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.config.FabricConfigFile;
import com.sakurakugu.archweaver.platform.PlatformClientConfig;
import com.sakurakugu.archweaver.client.camera.CameraPreferences;
import com.sakurakugu.archweaver.client.chunkloading.MapBackend;
import net.fabricmc.loader.api.FabricLoader;

/** 地图与相机设置仅在客户端初始化。 */
public final class FabricClientConfig extends FabricConfigFile implements PlatformClientConfig, CameraPreferences.Backend {
    public FabricClientConfig() {
        load(FabricLoader.getInstance().getConfigDir().resolve("archweaver-client.properties"));
        markerNameScale(); weakLoadingVisible(); realPlayersVisible(); mapBackend(); mainPageView();
        for (var setting : CameraPreferences.NumberSetting.values()) number(setting);
        for (var setting : CameraPreferences.Toggle.values()) toggle(setting);
        save();
    }
    public double markerNameScale() { return number("chunkMap.markerNameScale", 1, 0.5, 2); }
    public void setMarkerNameScale(double value) { put("chunkMap.markerNameScale", Math.clamp(value, 0.5, 2)); }
    public boolean weakLoadingVisible() { return bool("chunkMap.showWeakLoading", true); }
    public void setWeakLoadingVisible(boolean value) { put("chunkMap.showWeakLoading", value); }
    public boolean realPlayersVisible() { return bool("chunkMap.showRealPlayers", false); }
    public void setRealPlayersVisible(boolean value) { put("chunkMap.showRealPlayers", value); }
    public MapBackend mapBackend() { return choice("chunkMap.backend", MapBackend.BUILTIN); }
    public void setMapBackend(MapBackend value) { put("chunkMap.backend", value.name()); }
    public int mainPageView() { return integer("mainPage.lastView", 0, 0, 2); }
    public void setMainPageView(int value) { put("mainPage.lastView", Math.clamp(value, 0, 2)); }
    public double number(CameraPreferences.NumberSetting setting) {
        return number("camera." + setting.key(), setting.initial, setting.min, setting.max);
    }
    public void number(CameraPreferences.NumberSetting setting, double value) { put("camera." + setting.key(), setting.clamp(value)); }
    public boolean toggle(CameraPreferences.Toggle setting) { return bool("camera." + setting.key(), setting.initial); }
    public void toggle(CameraPreferences.Toggle setting, boolean value) { put("camera." + setting.key(), value); }
}
