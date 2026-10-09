package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.NumberSetting;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.TargetButton;
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
    private int targetLimit = 16;
    private boolean sortTargetsByDistance;
    private List<Entity> loadedTargets;

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
        toggle(Toggle.AUTO_ORBIT, left(), 166, 138);
        toggle(Toggle.HIDE_HUD_TEXT, left() + 142, 166, 138);
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
        toggle(setting, left(), y, 280);
    }

    private void toggle(Toggle setting, int x, int y, int width) {
        var button = button(CameraSelectorScreen.toggleLabel(setting), x, y, width, () -> { CameraPreferences.flip(setting); init(); });
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
        button(Component.literal("目标数：" + targetLimit), left(), 132, 138, () -> {
            targetLimit = targetLimit >= 128 ? 8 : targetLimit * 2;
            page = 0;
            init();
        });
        button(Component.literal(sortTargetsByDistance ? "按距离排序：开" : "按距离排序：关"), left() + 142, 132, 138, () -> {
            sortTargetsByDistance = !sortTargetsByDistance;
            page = 0;
            init();
        });
        List<Entity> targets = targets();
        int capacity = Math.max(1, (height - 220) / 22);
        int visibleCount = Math.min(targetLimit, targets.size());
        int pages = Math.max(1, (visibleCount + capacity - 1) / capacity);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * capacity; i < Math.min(visibleCount, (page + 1) * capacity); i++) {
            Entity entity = targets.get(i);
            var row = addRenderableWidget(new TargetButton(left(), 160 + (i % capacity) * 22, 280, 20, entity, button -> choose(entity)));
            row.setTooltip(Tooltip.create(Component.literal(entity.getUUID().toString())));
        }
        pageButton(PixelGlyph.ARROW_LEFT, "gui.archweaver.page.previous", left(), () -> { page = Math.max(0, page - 1); init(); });
        button(Component.literal((page + 1) + " / " + pages), left() + 24, height - 52, 232, this::init);
        pageButton(PixelGlyph.ARROW_RIGHT, "gui.archweaver.page.next", left() + 256, () -> { page = Math.min(pages - 1, page + 1); init(); });
    }

    /** 翻页按钮：图标按钮不留文字，提示与朗读都取自 tooltip。 */
    private void pageButton(PixelGlyph glyph, String tooltip, int x, Runnable action) {
        var label = Component.translatable(tooltip);
        // 左右翻页按钮保持正方形，避免图标按钮被拉成长条。
        addRenderableWidget(new SolidButton(x, height - 52, 20, 20, glyph, label, button -> action.run()));
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
        if (loadedTargets == null) {
            loadedTargets = new ArrayList<>();
            if (minecraft.level == null) return loadedTargets;
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity != minecraft.player && entity.isAlive() && !entity.isRemoved()) loadedTargets.add(entity);
            }
        }
        List<Entity> result = new ArrayList<>();
        String query = filter.toLowerCase(Locale.ROOT);
        for (Entity entity : loadedTargets) if (entity.isAlive() && !entity.isRemoved()
            && (entity.getDisplayName().getString() + " " + entity.getType().getDescription().getString()).toLowerCase(Locale.ROOT).contains(query)) result.add(entity);
        if (sortTargetsByDistance && minecraft.player != null) result.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(minecraft.player)));
        else result.sort(Comparator.<Entity, Boolean>comparing(e -> !(e instanceof Player)).thenComparing(e -> e.getDisplayName().getString()).thenComparing(Entity::getId));
        return result;
    }

    private void choose(Entity entity) {
        if (entity.isRemoved() || !entity.isAlive()) return;
        ClientCamera.follow(entity);
        if (pickFollow) onClose();
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(new SolidButton(x, y, width, 20, label, button -> action.run()));
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
