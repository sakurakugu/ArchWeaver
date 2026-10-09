package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.NumberSetting;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 不暂停世界的相机面板；分页限制控件数量，兼容较小的 GUI 分辨率。 */
public final class CameraPanelScreen extends Screen {
    private enum Tab { CAMERA, ANGLES, TARGETS, INPUTS }
    private final boolean pickFollow;
    private Tab tab;
    private String filter = "";
    private int page;

    public CameraPanelScreen(boolean pickFollow) {
        super(Component.translatable("camera.archweaver.panel"));
        this.pickFollow = pickFollow;
        tab = pickFollow ? Tab.TARGETS : ClientCamera.orthographic() ? Tab.ANGLES : Tab.CAMERA;
    }

    private int left() { return width / 2 - 140; }

    @Override
    protected void init() {
        clearWidgets();
        for (Tab value : Tab.values()) {
            button(Component.translatable("camera.archweaver.tab." + value.name().toLowerCase(Locale.ROOT)),
                left() + value.ordinal() * 70, 32, 68, () -> { tab = value; page = 0; init(); });
        }
        switch (tab) {
            case CAMERA -> cameraControls();
            case ANGLES -> angleControls();
            case TARGETS -> targetControls();
            case INPUTS -> inputControls();
        }
        button(Component.translatable("gui.done"), width / 2 - 60, height - 26, 120, this::onClose);
    }

    private void cameraControls() {
        NumberSetting[] settings = {NumberSetting.SPEED, NumberSetting.DISTANCE, NumberSetting.SHOULDER_DISTANCE,
            NumberSetting.SHOULDER_OFFSET, NumberSetting.ORBIT_SPEED};
        for (int i = 0; i < settings.length; i++) number(settings[i], 56 + i * 22);
        toggle(Toggle.AUTO_ORBIT, 166);
        toggle(Toggle.SELECT_PREVIOUS, 188);
    }

    private void angleControls() {
        for (int i = 0; i < CameraMath.Angle.values().length; i++) {
            var angle = CameraMath.Angle.values()[i];
            button(Component.translatable(angle.key()), left() + i % 5 * 56, 60 + i / 5 * 22, 54, () -> {
                ClientCamera.angle(angle);
                init();
            });
        }
        number(NumberSetting.YAW, 110);
        number(NumberSetting.PITCH, 134);
        number(NumberSetting.SCALE, 158);
    }

    private void inputControls() {
        toggle(Toggle.BODY_INTERACTION, 62);
        toggle(Toggle.BODY_MOVEMENT, 90);
    }

    private void toggle(Toggle setting, int y) {
        var button = button(CameraSelectorScreen.toggleLabel(setting), left(), y, 280, () -> { CameraPreferences.flip(setting); init(); });
        button.setTooltip(Tooltip.create(Component.translatable("camera.archweaver.toggle." + setting.key() + ".tooltip")));
    }

    private void number(NumberSetting setting, int y) {
        button(Component.translatable("camera.archweaver.setting." + setting.key()), left(), y, 158, () -> { });
        double initial = numberValue(setting);
        EditBox field = new EditBox(font, left() + 162, y, 76, 20, Component.translatable("camera.archweaver.setting." + setting.key()));
        field.setMaxLength(12);
        field.setValue(String.format(Locale.ROOT, "%.2f", initial));
        field.setResponder(text -> {
            try {
                double value = Double.parseDouble(text);
                if (!Double.isFinite(value) || value < setting.min || value > setting.max) { field.setTextColor(0xFF5555); return; }
                field.setTextColor(0xE0E0E0);
                CameraPreferences.set(setting, value);
                if (setting == NumberSetting.YAW) ClientCamera.rotation(value, numberValue(NumberSetting.PITCH));
                if (setting == NumberSetting.PITCH) ClientCamera.rotation(numberValue(NumberSetting.YAW), value);
            } catch (NumberFormatException ignored) { field.setTextColor(0xFF5555); }
        });
        field.setTooltip(Tooltip.create(Component.literal(setting.min + " … " + setting.max)));
        addRenderableWidget(field);
        button(Component.literal("−"), left() + 242, y, 18, () -> adjust(setting, -1));
        button(Component.literal("+"), left() + 262, y, 18, () -> adjust(setting, 1));
    }

    private static double numberValue(NumberSetting setting) {
        if (ClientCamera.active()) {
            if (setting == NumberSetting.YAW) return ClientCamera.yaw();
            if (setting == NumberSetting.PITCH) return ClientCamera.pitch();
        }
        return CameraPreferences.get(setting);
    }

    private void adjust(NumberSetting setting, int direction) {
        double value = setting.clamp(numberValue(setting) + direction * (setting == NumberSetting.SPEED || setting == NumberSetting.SHOULDER_OFFSET ? 0.1 : 1));
        CameraPreferences.set(setting, value);
        if (setting == NumberSetting.YAW) ClientCamera.rotation(value, numberValue(NumberSetting.PITCH));
        if (setting == NumberSetting.PITCH) ClientCamera.rotation(numberValue(NumberSetting.YAW), value);
        init();
    }

    private void targetControls() {
        EditBox search = new EditBox(font, left(), 60, 280, 20, Component.translatable("camera.archweaver.search"));
        search.setHint(Component.translatable("camera.archweaver.search"));
        search.setValue(filter);
        search.setResponder(value -> {
            if (!value.equals(filter)) { filter = value; page = 0; rebuildTargets(); }
        });
        addRenderableWidget(search);
        button(Component.translatable("camera.archweaver.body_target"), left(), 84, 138, () -> {
            if (ClientCamera.bodyHit() instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() != minecraft.player) choose(hit.getEntity());
            else minecraft.player.sendOverlayMessage(Component.translatable("camera.archweaver.no_entity"));
        });
        button(Component.translatable("camera.archweaver.block_center"), left() + 142, 84, 138, () -> {
            if (ClientCamera.category() != Category.ORBIT) ClientCamera.select(Category.ORBIT, 0);
            if (!ClientCamera.useBlockCenter()) minecraft.player.sendOverlayMessage(Component.translatable("camera.archweaver.no_block"));
        });
        button(Component.translatable("camera.archweaver.player_center"), left(), 108, 138, () -> {
            ClientCamera.select(Category.ORBIT, 0);
            ClientCamera.usePlayerCenter();
        });
        button(Component.translatable("camera.archweaver.refix"), left() + 142, 108, 138, () -> {
            ClientCamera.select(Category.FIXED, 0);
            ClientCamera.refix();
        });
        List<Entity> targets = targets();
        int capacity = Math.max(1, (height - 196) / 22);
        int pages = Math.max(1, (targets.size() + capacity - 1) / capacity);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * capacity; i < Math.min(targets.size(), (page + 1) * capacity); i++) {
            Entity entity = targets.get(i);
            var row = button(entity.getDisplayName().copy().append(" · ").append(entity.getType().getDescription()), left(), 136 + (i % capacity) * 22, 280, () -> choose(entity));
            row.setTooltip(Tooltip.create(Component.literal(entity.getUUID().toString())));
        }
        button(Component.literal("←"), left(), height - 52, 45, () -> { page = Math.max(0, page - 1); init(); });
        button(Component.literal((page + 1) + " / " + pages), left() + 49, height - 52, 182, this::init);
        button(Component.literal("→"), left() + 235, height - 52, 45, () -> { page = Math.min(pages - 1, page + 1); init(); });
    }

    private void rebuildTargets() {
        // 重建列表同时保留搜索框焦点与光标，支持连续输入。
        init();
        for (var child : children()) if (child instanceof EditBox box) {
            setFocused(box);
            box.moveCursorToEnd(false);
            break;
        }
    }

    private List<Entity> targets() {
        List<Entity> result = new ArrayList<>();
        if (minecraft.level == null) return result;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity != minecraft.player && entity.isAlive() && !entity.isRemoved()
                && (entity.getDisplayName().getString() + " " + entity.getType().getDescription().getString()).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) result.add(entity);
        }
        result.sort(Comparator.<Entity, Boolean>comparing(e -> !(e instanceof Player)).thenComparing(e -> e.getDisplayName().getString()).thenComparing(Entity::getId));
        return result;
    }

    private void choose(Entity entity) {
        if (entity.isRemoved() || !entity.isAlive()) return;
        ClientCamera.follow(entity);
        if (pickFollow) onClose();
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(label, button -> action.run()).bounds(x, y, width, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float partial) {
        graphics.fill(left() - 8, 8, left() + 288, height - 4, 0xDA202020);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partial) {
        graphics.centeredText(font, title.copy().append(" · ").append(Component.translatable(ClientCamera.category().key())), width / 2, 16, 0xFFFFFFFF);
        super.extractRenderState(graphics, x, y, partial);
        if (tab == Tab.INPUTS) {
            graphics.centeredText(font, Component.translatable("camera.archweaver.inputs_hint"), width / 2, 124, 0xFFAAAAAA);
            graphics.centeredText(font, Component.translatable("camera.archweaver.body_hint"), width / 2, 140, 0xFFAAAAAA);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { CameraPreferences.save(); ClientCamera.flushPreferences(); super.removed(); }
}
