/*
 * Mya's Ender Storage - a Fabric fork of EnderStorage by covers1624, ChickenBones and Ecu
 * https://github.com/TheCBProject/EnderStorage
 * Licensed under the MIT License. See LICENSE.txt.
 */
package jp.mya.myasenderstorage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 色3つの組み合わせ（周波数）。同じ組み合わせのチェストやポーチは中身がつながる。
 * personal が true のときは「個人用」で、開いた人ごとに別の中身になる。
 */
public record Frequency(DyeColor c1, DyeColor c2, DyeColor c3, boolean personal) {
    public static final Frequency DEFAULT = new Frequency(DyeColor.WHITE, DyeColor.WHITE, DyeColor.WHITE, false);

    public static Frequency of(BlockState state) {
        return new Frequency(state.getValue(EnderChestBlock.COLOR1), state.getValue(EnderChestBlock.COLOR2),
                state.getValue(EnderChestBlock.COLOR3), state.getValue(EnderChestBlock.PERSONAL));
    }

    /** アイテムに記録された色（ブロックの状態と同じ形で持っている。無ければ白・白・白） */
    public static Frequency of(ItemStack stack) {
        Map<String, String> p = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).properties();
        return new Frequency(color(p.get("color1")), color(p.get("color2")), color(p.get("color3")), "true".equals(p.get("personal")));
    }

    private static DyeColor color(String name) {
        return name == null ? DyeColor.WHITE : DyeColor.byName(name, DyeColor.WHITE);
    }

    /** アイテムに色を記録する */
    public void writeTo(ItemStack stack) {
        Map<String, String> p = new HashMap<>();
        p.put("color1", c1.getName());
        p.put("color2", c2.getName());
        p.put("color3", c3.getName());
        p.put("personal", Boolean.toString(personal));
        stack.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(p));
    }

    /** 保存用のキー。個人用は開いた人のUUIDを頭に付ける */
    public String key(UUID player) {
        String colors = c1.getName() + "," + c2.getName() + "," + c3.getName();
        return personal ? player + ":" + colors : colors;
    }

    /** 「 (赤/青/緑)」のような表示 */
    public MutableComponent label() {
        MutableComponent c = Component.literal(" (")
                .append(Component.translatable("color.minecraft." + c1.getName())).append("/")
                .append(Component.translatable("color.minecraft." + c2.getName())).append("/")
                .append(Component.translatable("color.minecraft." + c3.getName())).append(")");
        if (personal) c.append(" ").append(Component.translatable("myas_ender_storage.personal"));
        return c;
    }
}
