/*
 * Mya's Ender Storage - a Fabric fork of EnderStorage by covers1624, ChickenBones and Ecu
 * https://github.com/TheCBProject/EnderStorage
 * Licensed under the MIT License. See LICENSE.txt.
 */
package jp.mya.myasenderstorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 色ごとの中身の置き場所。ワールドの data フォルダに myas_ender_storage.dat（チェスト）と
 * myas_ender_storage_tanks.dat（タンク）として保存する。
 * ディメンションに関係なく、サーバー全体で1つ。
 */
public final class EnderStorageManager {
    private EnderStorageManager() {}

    public static final int SIZE = 27;

    /** 1つの色の組み合わせの中身（27マス）。ホッパーからも出し入れできる */
    public static class Storage extends SimpleContainer implements WorldlyContainer {
        private static final int[] SLOTS = new int[SIZE];

        static {
            for (int i = 0; i < SIZE; i++) SLOTS[i] = i;
        }

        Storage() {
            super(SIZE);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            dirty = true;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return SLOTS;
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
            return true;
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return true;
        }
    }

    /** 1つの色の組み合わせのタンク（液体1種類、16バケツまで）。パイプなどからも出し入れできる */
    public static class Tank extends SingleVariantStorage<FluidVariant> {
        @Override
        protected FluidVariant getBlankVariant() {
            return FluidVariant.blank();
        }

        @Override
        protected long getCapacity(FluidVariant variant) {
            return EnderTankBlock.CAPACITY;
        }

        @Override
        protected void onFinalCommit() {
            dirty = true;
        }
    }

    private record TankEntry(FluidVariant fluid, long amount) {
        static final Codec<TankEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                FluidVariant.CODEC.fieldOf("fluid").forGetter(TankEntry::fluid),
                Codec.LONG.fieldOf("amount").forGetter(TankEntry::amount)
        ).apply(i, TankEntry::new));
    }

    private record SavedTanks(Map<String, TankEntry> tanks) {
        static final Codec<SavedTanks> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, TankEntry.CODEC).fieldOf("tanks").forGetter(SavedTanks::tanks)
        ).apply(i, SavedTanks::new));
    }

    private record Saved(Map<String, List<ItemStack>> storages) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf()).fieldOf("storages").forGetter(Saved::storages)
        ).apply(i, Saved::new));
    }

    private static MinecraftServer current;
    private static Map<String, Storage> storages = new HashMap<>();
    private static Map<String, Tank> tanks = new HashMap<>();
    private static boolean dirty;

    private static void ensure(MinecraftServer server) {
        if (current != server) {
            current = server;
            storages = new HashMap<>();
            tanks = new HashMap<>();
            load(server);
            loadTanks(server);
            dirty = false;
        }
    }

    public static synchronized Storage get(MinecraftServer server, String key) {
        ensure(server);
        return storages.computeIfAbsent(key, k -> new Storage());
    }

    public static synchronized Tank tank(MinecraftServer server, String key) {
        ensure(server);
        return tanks.computeIfAbsent(key, k -> new Tank());
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("myas_ender_storage.dat");
    }

    private static Path tankFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("myas_ender_storage_tanks.dat");
    }

    private static void loadTanks(MinecraftServer server) {
        try {
            Path path = tankFile(server);
            if (!Files.exists(path)) return;
            CompoundTag tag = NbtIo.read(path);
            if (tag == null) return;
            SavedTanks.CODEC.parse(ops(server), tag).resultOrPartial(err ->
                    MyasEnderStorage.LOGGER.warn("Failed to read ender tanks: {}", err)).ifPresent(saved -> {
                saved.tanks().forEach((key, entry) -> {
                    Tank tank = new Tank();
                    tank.variant = entry.fluid();
                    tank.amount = Math.min(entry.amount(), EnderTankBlock.CAPACITY);
                    tanks.put(key, tank);
                });
            });
        } catch (Exception e) {
            MyasEnderStorage.LOGGER.error("Failed to load ender tanks", e);
        }
    }

    private static void saveTanks(MinecraftServer server) throws Exception {
        Map<String, TankEntry> out = new HashMap<>();
        tanks.forEach((key, tank) -> {
            if (tank.isResourceBlank() || tank.amount <= 0) return; // 空のタンクは保存しない
            out.put(key, new TankEntry(tank.variant, tank.amount));
        });
        Tag tag = SavedTanks.CODEC.encodeStart(ops(server), new SavedTanks(out)).getOrThrow();
        if (tag instanceof CompoundTag compound) {
            Path path = tankFile(server);
            Files.createDirectories(path.getParent());
            NbtIo.write(compound, path);
        }
    }

    private static DynamicOps<Tag> ops(MinecraftServer server) {
        return server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static void load(MinecraftServer server) {
        try {
            Path path = file(server);
            if (!Files.exists(path)) return;
            CompoundTag tag = NbtIo.read(path);
            if (tag == null) return;
            Saved.CODEC.parse(ops(server), tag).resultOrPartial(err ->
                    MyasEnderStorage.LOGGER.warn("Failed to read ender storage: {}", err)).ifPresent(saved -> {
                saved.storages().forEach((key, items) -> {
                    Storage storage = new Storage();
                    for (int i = 0; i < SIZE && i < items.size(); i++) storage.setItem(i, items.get(i));
                    storages.put(key, storage);
                });
            });
        } catch (Exception e) {
            MyasEnderStorage.LOGGER.error("Failed to load ender storage", e);
        }
    }

    /** 変更があったときだけファイルに書く（ワールドの保存時とサーバーを止めるときに呼ばれる） */
    public static synchronized void save(MinecraftServer server) {
        if (current != server || !dirty) return;
        try {
            Map<String, List<ItemStack>> out = new HashMap<>();
            storages.forEach((key, storage) -> {
                if (storage.isEmpty()) return; // 空の組み合わせは保存しない
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < SIZE; i++) items.add(storage.getItem(i).copy());
                out.put(key, items);
            });
            Tag tag = Saved.CODEC.encodeStart(ops(server), new Saved(out)).getOrThrow();
            if (tag instanceof CompoundTag compound) {
                Path path = file(server);
                Files.createDirectories(path.getParent());
                NbtIo.write(compound, path);
            }
            saveTanks(server);
            dirty = false;
        } catch (Exception e) {
            MyasEnderStorage.LOGGER.error("Failed to save ender storage", e);
        }
    }

    public static synchronized void onStopped(MinecraftServer server) {
        if (current == server) {
            current = null;
            storages = new HashMap<>();
            tanks = new HashMap<>();
            dirty = false;
        }
    }
}
