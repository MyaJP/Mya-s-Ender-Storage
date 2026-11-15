/*
 * Mya's Ender Storage - a Fabric fork of EnderStorage by covers1624, ChickenBones and Ecu
 * https://github.com/TheCBProject/EnderStorage
 * Licensed under the MIT License. See LICENSE.txt.
 */
package jp.mya.myasenderstorage;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.UUID;

/**
 * 色でつながるエンダータンク（液体版のエンダーチェスト）。
 * 色3つ・向き・個人用はエンダーチェストと同じプロパティを使う。PUMP がオンだと真下へ液体を流し出す。
 * 中身は EnderStorageManager に色ごとに保存する（エンダーチェストとは別の置き場所）。
 */
public class EnderTankBlock extends Block {
    public static final BooleanProperty PUMP = BooleanProperty.create("pump");

    /** 1つの色の組み合わせに入る量（16バケツ） */
    public static final long CAPACITY = 16 * FluidConstants.BUCKET;
    /** 自動排出の間隔（tick）と、1回に流す量 */
    private static final int PUMP_INTERVAL = 10;
    private static final long PUMP_AMOUNT = FluidConstants.BUCKET;

    private static final VoxelShape SHAPE = Block.box(2.5, 0, 2.5, 13.5, 15, 13.5);

    public EnderTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(EnderChestBlock.FACING, Direction.NORTH)
                .setValue(EnderChestBlock.COLOR1, DyeColor.WHITE).setValue(EnderChestBlock.COLOR2, DyeColor.WHITE)
                .setValue(EnderChestBlock.COLOR3, DyeColor.WHITE).setValue(EnderChestBlock.PERSONAL, false)
                .setValue(PUMP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EnderChestBlock.FACING, EnderChestBlock.COLOR1, EnderChestBlock.COLOR2, EnderChestBlock.COLOR3,
                EnderChestBlock.PERSONAL, PUMP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(EnderChestBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 素手で右クリック：正面のバルブなら自動排出の切り替え、それ以外なら中身を表示 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (isValve(state, pos, hit)) {
            boolean on = !state.getValue(PUMP);
            level.setBlock(pos, state.setValue(PUMP, on), 3);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, on ? 0.6F : 0.5F);
            if (on) level.scheduleTick(pos, this, PUMP_INTERVAL);
            player.displayClientMessage(Component.translatable(on ? "myas_ender_storage.tank.pump_on" : "myas_ender_storage.tank.pump_off"), true);
        } else {
            Storage<FluidVariant> tank = tankFor(level, state, player.getUUID());
            if (tank != null) player.displayClientMessage(describe(tank, Frequency.of(state)), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** 自動排出：真下の液体を受け取れるブロックへ流す（個人用は誰の中身か決まらないので流さない） */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PUMP)) return;
        level.scheduleTick(pos, this, PUMP_INTERVAL);
        if (state.getValue(EnderChestBlock.PERSONAL)) return;
        Storage<FluidVariant> tank = tankFor(level, state, null);
        Storage<FluidVariant> below = FluidStorage.SIDED.find(level, pos.below(), Direction.UP);
        if (tank == null || below == null) return;
        try (Transaction tx = Transaction.openOuter()) {
            if (StorageUtil.move(tank, below, v -> true, PUMP_AMOUNT, tx) > 0) tx.commit();
        }
    }

    /** その色のタンク（個人用は player が null だと取れない） */
    public static Storage<FluidVariant> tankFor(Level level, BlockState state, UUID player) {
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        Frequency frequency = Frequency.of(state);
        if (frequency.personal() && player == null) return null;
        return EnderStorageManager.tank(server, frequency.key(player));
    }

    /** 「エンダータンク (白/白/白)：水 8000 / 16000 mB」のような表示 */
    private static Component describe(Storage<FluidVariant> tank, Frequency frequency) {
        Component contents = Component.translatable("myas_ender_storage.tank.empty");
        long amount = 0;
        if (tank instanceof EnderStorageManager.Tank t && !t.isResourceBlank() && t.getAmount() > 0) {
            contents = FluidVariantAttributes.getName(t.getResource());
            amount = t.getAmount();
        }
        return Component.translatable("myas_ender_storage.tank.contents",
                Component.empty().append(Component.translatable("block.myas_ender_storage.ender_tank")).append(frequency.label()),
                contents, amount * 1000 / FluidConstants.BUCKET, CAPACITY * 1000 / FluidConstants.BUCKET);
    }

    /** 正面のバルブ（モデル上 x 6～10、y 4.5～8.5）を押したか */
    static boolean isValve(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(EnderChestBlock.FACING);
        if (hit.getDirection() != facing) return false;
        double x = hit.getLocation().x - pos.getX();
        double z = hit.getLocation().z - pos.getZ();
        double y = (hit.getLocation().y - pos.getY()) * 16;
        // モデルは北向きが基準。正面から見たときの横位置（左端が0）に直す
        double local = switch (facing) {
            case SOUTH -> 1 - x;
            case EAST -> z;
            case WEST -> 1 - z;
            default -> x;
        } * 16;
        return local >= 5.5 && local <= 10.5 && y >= 4 && y <= 9;
    }
}
