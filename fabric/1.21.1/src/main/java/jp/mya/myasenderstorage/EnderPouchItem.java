/*
 * Mya's Ender Storage - a Fabric fork of EnderStorage by covers1624, ChickenBones and Ecu
 * https://github.com/TheCBProject/EnderStorage
 * Licensed under the MIT License. See LICENSE.txt.
 */
package jp.mya.myasenderstorage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResultHolder;

/**
 * エンダーポーチ。右クリックで、記録してある色のエンダーチェストの中身をどこでも開ける。
 * スニークしながらエンダーチェストを右クリックすると、そのチェストの色を記録する（MyasEnderStorage の方で処理）。
 */
public class EnderPouchItem extends Item {
    public EnderPouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) EnderChestBlock.open(player, Frequency.of(stack), level, null);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.empty().append(super.getName(stack)).append(Frequency.of(stack).label());
    }
}
