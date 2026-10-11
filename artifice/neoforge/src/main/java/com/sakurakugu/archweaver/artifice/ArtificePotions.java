package com.sakurakugu.archweaver.artifice;

import com.sakurakugu.archweaver.api.invisibility.InvisibilityApi;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 药水注册只在内容版发生，喝下后仍然是原版隐身效果。 */
public final class ArtificePotions {
    private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, ArtificeMod.MOD_ID);
    public static final DeferredHolder<Potion, Potion> INVISIBILITY_II = register("invisibility_ii", 2, 3600);
    public static final DeferredHolder<Potion, Potion> LONG_INVISIBILITY_II = register("long_invisibility_ii", 2, 9600);
    public static final DeferredHolder<Potion, Potion> INVISIBILITY_III = register("invisibility_iii", 3, 3600);
    public static final DeferredHolder<Potion, Potion> LONG_INVISIBILITY_III = register("long_invisibility_iii", 3, 9600);
    public static final DeferredHolder<Potion, Potion> INVISIBILITY_IV = register("invisibility_iv", 4, 3600);
    public static final DeferredHolder<Potion, Potion> LONG_INVISIBILITY_IV = register("long_invisibility_iv", 4, 9600);

    private ArtificePotions() {
    }

    private static DeferredHolder<Potion, Potion> register(String name, int level, int duration) {
        // 瓶名沿用原版翻译，提示中的效果等级区分 II、III、IV。
        return POTIONS.register(name, () -> new Potion("invisibility", InvisibilityApi.createEffect(level, duration)));
    }

    public static void register(IEventBus modBus) {
        POTIONS.register(modBus);
        modBus.addListener(ArtificePotions::creativeTabContents);
        NeoForge.EVENT_BUS.addListener(ArtificePotions::brewing);
    }

    private static void creativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FOOD_AND_DRINKS)) {
            sortInvisibilityPotions(event, Items.POTION);
            sortInvisibilityPotions(event, Items.SPLASH_POTION);
            sortInvisibilityPotions(event, Items.LINGERING_POTION);
        } else if (event.getTabKey().equals(CreativeModeTabs.COMBAT)) {
            sortInvisibilityPotions(event, Items.TIPPED_ARROW);
        }
    }

    private static void sortInvisibilityPotions(BuildCreativeModeTabContentsEvent event, Item item) {
        var previous = PotionContents.createItemStack(item, Potions.LONG_INVISIBILITY);
        if (!event.getParentEntries().contains(previous)) return;

        // 原版已经按注册表顺序加入所有药水，移除后插回原版隐身系列末尾，同步搜索栏顺序。
        var visibility = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        for (var potion : List.of(INVISIBILITY_II, LONG_INVISIBILITY_II,
            INVISIBILITY_III, LONG_INVISIBILITY_III, INVISIBILITY_IV, LONG_INVISIBILITY_IV)) {
            if (!potion.value().isEnabled(event.getFlags())) continue;
            var stack = PotionContents.createItemStack(item, potion);
            event.remove(stack, visibility);
            event.insertAfter(previous, stack, visibility);
            previous = stack;
        }
    }

    private static void brewing(RegisterBrewingRecipesEvent event) {
        var builder = event.getBuilder();
        builder.addMix(Potions.INVISIBILITY, Items.GLOWSTONE_DUST, INVISIBILITY_II);
        builder.addMix(INVISIBILITY_II, Items.GLOWSTONE_DUST, INVISIBILITY_III);
        builder.addMix(INVISIBILITY_III, Items.GLOWSTONE_DUST, INVISIBILITY_IV);
        builder.addMix(Potions.LONG_INVISIBILITY, Items.GLOWSTONE_DUST, LONG_INVISIBILITY_II);
        builder.addMix(LONG_INVISIBILITY_II, Items.GLOWSTONE_DUST, LONG_INVISIBILITY_III);
        builder.addMix(LONG_INVISIBILITY_III, Items.GLOWSTONE_DUST, LONG_INVISIBILITY_IV);
        builder.addMix(INVISIBILITY_II, Items.REDSTONE, LONG_INVISIBILITY_II);
        builder.addMix(INVISIBILITY_III, Items.REDSTONE, LONG_INVISIBILITY_III);
        builder.addMix(INVISIBILITY_IV, Items.REDSTONE, LONG_INVISIBILITY_IV);
    }
}
