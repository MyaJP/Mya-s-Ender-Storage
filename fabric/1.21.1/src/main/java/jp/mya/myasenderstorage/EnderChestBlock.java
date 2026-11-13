/*
 * Mya's Ender Storage - a Fabric fork of EnderStorage by covers1624, ChickenBones and Ecu
 * https://github.com/TheCBProject/EnderStorage
 * Licensed under the MIT License. See LICENSE.txt.
 */
package jp.mya.myasenderstorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 色でつながるエンダーチェスト。
 * 色3つ・向き・個人用かどうかは全部ブロックの状態として持つ（ブロックエンティティは使わない）。
 */
public class EnderChestBlock extends Block implements WorldlyContainerHolder {
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DyeColor> COLOR1 = EnumProperty.create("color1", DyeColor.class);
    public static final EnumProperty<DyeColor> COLOR2 = EnumProperty.create("color2", DyeColor.class);
    public static final EnumProperty<DyeColor> COLOR3 = EnumProperty.create("color3", DyeColor.class);
    public static final BooleanProperty PERSONAL = BooleanProperty.create("personal");

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public EnderChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(COLOR1, DyeColor.WHITE).setValue(COLOR2, DyeColor.WHITE).setValue(COLOR3, DyeColor.WHITE)
                .setValue(PERSONAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR1, COLOR2, COLOR3, PERSONAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 手に染料などを持っていないときの右クリック：中身を開く（染料やダイヤは MyasEnderStorage の方で処理する） */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) open(player, Frequency.of(state), level, pos);
        return InteractionResult.SUCCESS;
    }

    /** その色の中身を開く（ポーチからも使う。pos が null のときは音を鳴らす場所をプレイヤーにする） */
    public static void open(Player player, Frequency frequency, Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) return;
        EnderStorageManager.Storage storage = EnderStorageManager.get(server, frequency.key(player.getUUID()));
        Component title = Component.translatable("container.myas_ender_storage").append(frequency.label());
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, storage), title));
        BlockPos at = pos != null ? pos : player.blockPosition();
        level.playSound(null, at, SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    /** ホッパーなどからの出し入れ（個人用は誰の中身か決まらないので不可） */
    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        if (level.isClientSide() || state.getValue(PERSONAL)) return null;
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        return EnderStorageManager.get(server, Frequency.of(state).key(null));
    }

    /**
     * 上の面のどの色パッドを押したか（0～2、パッド以外は -1）。
     * パッドは正面から見て横に3つ並んでいる。
     */
    public static int padAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP) return -1;
        double x = hit.getLocation().x - pos.getX();
        double z = hit.getLocation().z - pos.getZ();
        // モデルは北向きが基準。向きに合わせて、モデル上の横位置に直す
        double local = switch (state.getValue(FACING)) {
            case SOUTH -> 1 - x;
            case EAST -> z;
            case WEST -> 1 - z;
            default -> x;
        };
        double px = local * 16;
        if (px < 3 || px > 13) return -1;
        return px < 6.5 ? 0 : px < 9.5 ? 1 : 2;
    }

    public static Property<DyeColor> colorProperty(int pad) {
        return pad == 0 ? COLOR1 : pad == 1 ? COLOR2 : COLOR3;
    }
}
