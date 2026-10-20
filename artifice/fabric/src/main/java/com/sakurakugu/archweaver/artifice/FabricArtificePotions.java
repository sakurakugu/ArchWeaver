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
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** 内容注册只依赖核心公开 API。 */
public final class FabricArtificePotions {
    public static final Holder<Potion> INVISIBILITY_II = register("invisibility_ii", 2, 3600);
    public static final Holder<Potion> LONG_INVISIBILITY_II = register("long_invisibility_ii", 2, 9600);
    public static final Holder<Potion> INVISIBILITY_III = register("invisibility_iii", 3, 3600);
    public static final Holder<Potion> LONG_INVISIBILITY_III = register("long_invisibility_iii", 3, 9600);
    public static final Holder<Potion> INVISIBILITY_IV = register("invisibility_iv", 4, 3600);
    public static final Holder<Potion> LONG_INVISIBILITY_IV = register("long_invisibility_iv", 4, 9600);
    private static Holder<Potion> register(String name, int level, int duration) {
        return Registry.registerForHolder(BuiltInRegistries.POTION,
            Identifier.fromNamespaceAndPath(ArtificeMod.MOD_ID, name),
            new Potion("invisibility", InvisibilityApi.createEffect(level, duration)));
    }
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
            sort(output, Items.POTION); sort(output, Items.SPLASH_POTION); sort(output, Items.LINGERING_POTION);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> sort(output, Items.TIPPED_ARROW));
        FabricPotionBrewingBuilder.BUILD.register(builder -> {
        builder.addMix(Potions.INVISIBILITY, Items.GLOWSTONE_DUST, INVISIBILITY_II);
        builder.addMix(INVISIBILITY_II, Items.GLOWSTONE_DUST, INVISIBILITY_III);
        builder.addMix(INVISIBILITY_III, Items.GLOWSTONE_DUST, INVISIBILITY_IV);
        builder.addMix(Potions.LONG_INVISIBILITY, Items.GLOWSTONE_DUST, LONG_INVISIBILITY_II);
        builder.addMix(LONG_INVISIBILITY_II, Items.GLOWSTONE_DUST, LONG_INVISIBILITY_III);
        builder.addMix(LONG_INVISIBILITY_III, Items.GLOWSTONE_DUST, LONG_INVISIBILITY_IV);
        builder.addMix(INVISIBILITY_II, Items.REDSTONE, LONG_INVISIBILITY_II);
        builder.addMix(INVISIBILITY_III, Items.REDSTONE, LONG_INVISIBILITY_III);
        builder.addMix(INVISIBILITY_IV, Items.REDSTONE, LONG_INVISIBILITY_IV);
        });
    }
    private static void sort(FabricCreativeModeTabOutput output, Item item) {
        var previous = PotionContents.createItemStack(item, Potions.LONG_INVISIBILITY);
        var stacks = List.of(INVISIBILITY_II, LONG_INVISIBILITY_II, INVISIBILITY_III,
            LONG_INVISIBILITY_III, INVISIBILITY_IV, LONG_INVISIBILITY_IV).stream()
            .filter(potion -> potion.value().isEnabled(output.getEnabledFeatures()))
            .map(potion -> PotionContents.createItemStack(item, potion)).toList();
        for (var entries : List.of(output.getDisplayStacks(), output.getSearchTabStacks())) {
            entries.removeIf(stack -> stacks.stream().anyMatch(value -> ItemStack.isSameItemSameComponents(stack, value)));
        }
        output.insertAfter(previous, stacks);
    }
}
