package com.sakurakugu.archweaver.api.skin;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.archweaver.api.ApiResult;
import com.sakurakugu.archweaver.skin.SkinSavedData;
import com.sakurakugu.archweaver.skin.SkinService;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * 供附属模组使用的目标皮肤 API。
 *
 * <p>覆盖只在目标在场时生效：假人必须是当前在线的假人实体（含外部模组提供的假人），玩偶必须仍然登记在册，
 * 否则只保存记录，等目标上线或重新登记后再下发。给真实玩家 UUID 设置的覆盖不会显示在真人身上。
 */
public final class SkinApi {
    private SkinApi() { }
    public static ApiResult applyPlayerProfile(MinecraftServer server, UUID target, GameProfile source) {
        try { SkinService.applyProfile(server, target, SkinSavedData.TargetType.PLAYER, source); return ApiResult.success(); }
        catch (RuntimeException exception) { return ApiResult.failure(exception.getMessage()); }
    }
    public static ApiResult applyMannequinProfile(MinecraftServer server, UUID target, GameProfile source) {
        try { SkinService.applyProfile(server, target, SkinSavedData.TargetType.MANNEQUIN, source); return ApiResult.success(); }
        catch (RuntimeException exception) { return ApiResult.failure(exception.getMessage()); }
    }
    public static ApiResult applyPng(MinecraftServer server, UUID target, boolean mannequin, byte[] png, String model) {
        return applyPng(server, target, mannequin, png, model, "");
    }
    /** 文件名只用于展示，服务端会去掉目录并限制长度。 */
    public static ApiResult applyPng(MinecraftServer server, UUID target, boolean mannequin, byte[] png, String model, String fileName) {
        try { SkinService.applyPng(server, target, mannequin ? SkinSavedData.TargetType.MANNEQUIN : SkinSavedData.TargetType.PLAYER, png, model, fileName); return ApiResult.success(); }
        catch (RuntimeException exception) { return ApiResult.failure(exception.getMessage()); }
    }
    public static ApiResult clear(MinecraftServer server, UUID target, boolean mannequin) {
        try { SkinService.clear(server, target, mannequin ? SkinSavedData.TargetType.MANNEQUIN : SkinSavedData.TargetType.PLAYER); return ApiResult.success(); }
        catch (RuntimeException exception) { return ApiResult.failure(exception.getMessage()); }
    }
}
