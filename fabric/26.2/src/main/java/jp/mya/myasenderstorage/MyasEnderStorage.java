/*
 * Mya's Ender Storage - a Fabric fork of EnderStorage by covers1624, ChickenBones and Ecu
 * https://github.com/TheCBProject/EnderStorage
 * Licensed under the MIT License. See LICENSE.txt.
 */
package jp.mya.myasenderstorage;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/** 色でつながるエンダーチェストとエンダーポーチを追加する */
public class MyasEnderStorage implements ModInitializer {
    public static final String MOD_ID = "myas_ender_storage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Block ENDER_CHEST;
    public static Item ENDER_CHEST_ITEM;
    public static Item ENDER_POUCH;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Identifier chestId = id("ender_chest");
        Identifier pouchId = id("ender_pouch");

        BlockBehaviour.Properties blockProps = BlockBehaviour.Properties.of().strength(3.0F, 1200.0F).sound(SoundType.STONE);
        Item.Properties chestProps = new Item.Properties();
        Item.Properties pouchProps = new Item.Properties().stacksTo(1);
        blockProps.setId(ResourceKey.create(Registries.BLOCK, chestId));
        chestProps.setId(ResourceKey.create(Registries.ITEM, chestId)).useBlockDescriptionPrefix();
        pouchProps.setId(ResourceKey.create(Registries.ITEM, pouchId));
        ENDER_CHEST = Registry.register(BuiltInRegistries.BLOCK, chestId, new EnderChestBlock(blockProps));
        ENDER_CHEST_ITEM = Registry.register(BuiltInRegistries.ITEM, chestId, new EnderChestItem(ENDER_CHEST, chestProps));
        ENDER_POUCH = Registry.register(BuiltInRegistries.ITEM, pouchId, new EnderPouchItem(pouchProps));

        // クリエイティブのタブ（機能的ブロック、道具と実用品）に並べる
        ResourceKey<CreativeModeTab> functionalBlocks = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));
        ResourceKey<CreativeModeTab> tools = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
        CreativeModeTabEvents.modifyOutputEvent(functionalBlocks).register(output -> output.accept(ENDER_CHEST_ITEM));
        CreativeModeTabEvents.modifyOutputEvent(tools).register(output -> output.accept(ENDER_POUCH));

        UseBlockCallback.EVENT.register(MyasEnderStorage::onUseBlock);
        ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> EnderStorageManager.save(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(EnderStorageManager::save);
        ServerLifecycleEvents.SERVER_STOPPED.register(EnderStorageManager::onStopped);
    }

    /**
     * エンダーチェストを、物を持って右クリックしたとき。
     * ・染料で上の色パッド → その色に変える
     * ・ダイヤモンド → 個人用にする（スニーク＋素手で元に戻して、ダイヤモンドが返ってくる）
     * ・スニーク＋エンダーポーチ → ポーチにそのチェストの色を記録する
     */
    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EnderChestBlock)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);

        // 染料で色を変える
        DyeColor dye = dyeOf(stack);
        int pad = EnderChestBlock.padAt(state, pos, hit);
        if (dye != null && pad >= 0) {
            if (state.getValue(EnderChestBlock.colorProperty(pad)) == dye) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(EnderChestBlock.colorProperty(pad), dye), 3);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.hasInfiniteMaterials()) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        // ダイヤモンドで個人用にする
        if (stack.is(Items.DIAMOND) && !state.getValue(EnderChestBlock.PERSONAL)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(EnderChestBlock.PERSONAL, true), 3);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.hasInfiniteMaterials()) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        // スニーク＋素手で個人用をやめる
        if (stack.isEmpty() && player.isShiftKeyDown() && state.getValue(EnderChestBlock.PERSONAL)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(EnderChestBlock.PERSONAL, false), 3);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                Block.popResource(level, pos.above(), new ItemStack(Items.DIAMOND));
            }
            return InteractionResult.SUCCESS;
        }

        // スニーク＋ポーチで色を記録する
        if (stack.getItem() instanceof EnderPouchItem && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                Frequency.of(state).writeTo(stack);
                level.playSound(null, pos, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.5F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }

        // スニーク中でなければ、ポーチを持っていてもチェストを開く
        return InteractionResult.PASS;
    }

    /** 染料アイテムなら、その色（アイテムIDの「○○_dye」から判定する） */
    private static DyeColor dyeOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!key.getNamespace().equals("minecraft") || !key.getPath().endsWith("_dye")) return null;
        return DyeColor.byName(key.getPath().substring(0, key.getPath().length() - 4), null);
    }
}
