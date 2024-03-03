package net.cyanmarine.simpleveinminer.client;

import net.cyanmarine.simpleveinminer.Constants;
import net.cyanmarine.simpleveinminer.SimpleVeinminer;
import net.cyanmarine.simpleveinminer.commands.CommandRegisterClient;
import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.cyanmarine.simpleveinminer.config.SimpleConfigClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.StickyKeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public class SimpleVeinminerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(SimpleVeinminer.MOD_ID + " - client");
    public static boolean isVeinMiningServerSide = false;
    public static AtomicBoolean isInstalledOnServerSide = new AtomicBoolean(false);
    static SimpleConfig.SimpleConfigCopy worldConfig;
    private static SimpleConfigClient config;
    public static KeyBinding veinMineKeybind = KeyBindingHelper.registerKeyBinding(new StickyKeyBinding("key.simpleveinminer.veinminingKey", GLFW.GLFW_KEY_GRAVE_ACCENT, "key.simpleveinminer.veinminerCategory", () -> config.keybindToggles));
    public static KeyBinding increaseRadius = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.simpleveinminer.increaseRadius", GLFW.GLFW_KEY_RIGHT_BRACKET, "key.simpleveinminer.veinminerCategory"));
    public static KeyBinding decreaseRadius = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.simpleveinminer.decreaseRadius", GLFW.GLFW_KEY_LEFT_BRACKET, "key.simpleveinminer.veinminerCategory"));
    public static KeyBinding freezePreview = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.simpleveinminer.freezePreview", GLFW.GLFW_KEY_UNKNOWN, "key.simpleveinminer.veinminerCategory"));
    public static boolean veinMining;
    public static boolean previewFrozen = false;
    public static BlockState currentlyOutliningState;
    public static ArrayList<BlockPos> blocksToHighlight;

    public static SimpleConfig.SimpleConfigCopy getWorldConfig() {
        if (worldConfig == null) return SimpleConfig.SimpleConfigCopy.from(config);
        return worldConfig;
    }

    public static SimpleConfigClient getConfig() {
        return config;
    }

    public static void drawBox(BufferBuilder buffer, Matrix4f positionMatrix, float red, float green, float blue, float alpha, Box box, @Nullable BlockPos pos, @Nullable List<BlockPos> blocks, boolean ignoreNeighbors) {
        float minX = (float) box.minX;
        float maxX = (float) box.maxX;
        float minY = (float) box.minY;
        float maxY = (float) box.maxY;
        float minZ = (float) box.minZ;
        float maxZ = (float) box.maxZ;

        boolean b = ignoreNeighbors || pos == null || blocks == null;

        if (b || minZ > 0 || !blocks.contains(pos.north())) {
            buffer.vertex(positionMatrix, minX, maxY, minZ).color(red, green, blue, alpha).texture(minX, minY).next();
            buffer.vertex(positionMatrix, minX, minY, minZ).color(red, green, blue, alpha).texture(minX, maxY).next();
            buffer.vertex(positionMatrix, maxX, minY, minZ).color(red, green, blue, alpha).texture(maxX, maxY).next();
            buffer.vertex(positionMatrix, maxX, maxY, minZ).color(red, green, blue, alpha).texture(maxX, minY).next();
        }

        if (b || maxZ < 1 || !blocks.contains(pos.south())) {
            buffer.vertex(positionMatrix, minX, maxY, maxZ).color(red, green, blue, alpha).texture(minX, minY).next();
            buffer.vertex(positionMatrix, minX, minY, maxZ).color(red, green, blue, alpha).texture(minX, maxY).next();
            buffer.vertex(positionMatrix, maxX, minY, maxZ).color(red, green, blue, alpha).texture(maxX, maxY).next();
            buffer.vertex(positionMatrix, maxX, maxY, maxZ).color(red, green, blue, alpha).texture(maxX, minY).next();
        }

        if (b || maxX < 1 || !blocks.contains(pos.east())) {
            buffer.vertex(positionMatrix, maxX, minY, maxZ).color(red, green, blue, alpha).texture(minY, maxZ).next();
            buffer.vertex(positionMatrix, maxX, minY, minZ).color(red, green, blue, alpha).texture(minY, minZ).next();
            buffer.vertex(positionMatrix, maxX, maxY, minZ).color(red, green, blue, alpha).texture(maxY, minZ).next();
            buffer.vertex(positionMatrix, maxX, maxY, maxZ).color(red, green, blue, alpha).texture(maxY, maxZ).next();
        }

        if (b || minX > 0 || !blocks.contains(pos.west())) {
            buffer.vertex(positionMatrix, minX, minY, maxZ).color(red, green, blue, alpha).texture(minY, maxZ).next();
            buffer.vertex(positionMatrix, minX, minY, minZ).color(red, green, blue, alpha).texture(minY, minZ).next();
            buffer.vertex(positionMatrix, minX, maxY, minZ).color(red, green, blue, alpha).texture(maxY, minZ).next();
            buffer.vertex(positionMatrix, minX, maxY, maxZ).color(red, green, blue, alpha).texture(maxY, maxZ).next();
        }

        if (b || maxY < 1 || !blocks.contains(pos.up())) {
            buffer.vertex(positionMatrix, maxX, maxY, minZ).color(red, green, blue, alpha).texture(maxX, minZ).next();
            buffer.vertex(positionMatrix, minX, maxY, minZ).color(red, green, blue, alpha).texture(minX, minZ).next();
            buffer.vertex(positionMatrix, minX, maxY, maxZ).color(red, green, blue, alpha).texture(minX, maxZ).next();
            buffer.vertex(positionMatrix, maxX, maxY, maxZ).color(red, green, blue, alpha).texture(maxX, maxZ).next();
        }

        if (b || minY > 0 || !blocks.contains(pos.down())) {
            buffer.vertex(positionMatrix, maxX, minY, minZ).color(red, green, blue, alpha).texture(maxX, minZ).next();
            buffer.vertex(positionMatrix, minX, minY, minZ).color(red, green, blue, alpha).texture(minX, minZ).next();
            buffer.vertex(positionMatrix, minX, minY, maxZ).color(red, green, blue, alpha).texture(minX, maxZ).next();
            buffer.vertex(positionMatrix, maxX, minY, maxZ).color(red, green, blue, alpha).texture(maxX, maxZ).next();
        }
    }

    public static void drawOutline(BufferBuilder buffer, Matrix4f positionMatrix, float red, float green, float blue, float alpha, VoxelShape shape, @Nullable BlockPos pos, @Nullable List<BlockPos> blocks, boolean ignoreNeighbors, MatrixStack matrices) {
        shape.forEachEdge((_minX, _minY, _minZ, _maxX, _maxY, _maxZ) -> {
            float minX = (float)_minX;
            float minY = (float)_minY;
            float minZ = (float)_minZ;
            float maxX = (float)_maxX;
            float maxY = (float)_maxY;
            float maxZ = (float)_maxZ;

            float size = 0.01f;

            buffer.vertex(positionMatrix, minX, minY + size, minZ).color(red, green, blue, alpha).texture(minX, minY).next();
            buffer.vertex(positionMatrix, minX, minY - size, minZ).color(red, green, blue, alpha).texture(minX, maxY).next();
            buffer.vertex(positionMatrix, maxX, minY - size, minZ).color(red, green, blue, alpha).texture(maxX, maxY).next();
            buffer.vertex(positionMatrix, maxX, minY + size, minZ).color(red, green, blue, alpha).texture(maxX, minY).next();

            buffer.vertex(positionMatrix, minX - size, maxY, maxZ).color(red, green, blue, alpha).texture(minX, minY).next();
            buffer.vertex(positionMatrix, minX - size, minY, maxZ).color(red, green, blue, alpha).texture(minX, maxY).next();
            buffer.vertex(positionMatrix, minX + size, minY, maxZ).color(red, green, blue, alpha).texture(maxX, maxY).next();
            buffer.vertex(positionMatrix, minX + size, maxY, maxZ).color(red, green, blue, alpha).texture(maxX, minY).next();

            buffer.vertex(positionMatrix, minX, minY + size, minZ).color(red, green, blue, alpha).texture(minX, minY).next();
            buffer.vertex(positionMatrix, minX, minY - size, minZ).color(red, green, blue, alpha).texture(minX, maxY).next();
            buffer.vertex(positionMatrix, minX, minY - size, maxZ).color(red, green, blue, alpha).texture(maxX, maxY).next();
            buffer.vertex(positionMatrix, minX, minY + size, maxZ).color(red, green, blue, alpha).texture(maxX, minY).next();
        });
    }

    private void changeClientRadius(MinecraftClient client, int change) {
        SimpleConfigClient config = SimpleVeinminerClient.getConfig();

        int newRadius = MathHelper.clamp(config.clientRadius + change, 1, config.limits.radius);
        config.setClientRadius(newRadius);
        client.player.sendMessage(Text.translatable("messages.simpleveinminer.radiusChanged", newRadius), true);


        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(newRadius);
        ClientPlayNetworking.send(Constants.NETWORKING_RADIUS, buf);
    }

    @Override
    public void onInitializeClient() {
        veinMining = false;
        config = new SimpleConfigClient();
        config.load();
        worldConfig = SimpleConfig.SimpleConfigCopy.from(config);

        new CommandRegisterClient();


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (veinMining != veinMineKeybind.isPressed()) {
                veinMining = !veinMining;
                if (config.keybindToggles)
                    client.player.sendMessage(veinMining ? Text.translatable("messages.simpleveinminer.veinminingToggled.on") : Text.translatable("messages.simpleveinminer.veinminingToggled.off"), true);

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBoolean(veinMining);
                ClientPlayNetworking.send(Constants.NETWORKING_VEINMINE, buf);
            }
            if (increaseRadius.wasPressed())
                changeClientRadius(client, 1);
            if (decreaseRadius.wasPressed())
                changeClientRadius(client, -1);
            if (freezePreview.wasPressed()) {
                previewFrozen = !previewFrozen;
                client.player.sendMessage(previewFrozen ? Text.translatable("messages.simpleveinminer.previewFrozen") : Text.translatable("messages.simpleveinminer.previewUnfrozen"), true);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            worldConfig = null;
            SimpleVeinminerClient.isInstalledOnServerSide.set(false);
        });

        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            worldConfig = null;
            SimpleVeinminerClient.isInstalledOnServerSide.set(false);
        });

        ClientPlayNetworking.registerGlobalReceiver(Constants.CONFIG_SYNC, (client, handler, buf, responseSender) -> {
            buf.retain();

            client.execute(() -> {
                worldConfig = SimpleConfig.copy(buf);
                SimpleVeinminerClient.isInstalledOnServerSide.set(true);
                buf.release();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Constants.SERVERSIDE_UPDATE, (client, handler, buf, responseSender) -> {
            boolean newValue = buf.readBoolean();

            client.execute(() -> isVeinMiningServerSide = newValue);
        });

        LOGGER.info("Simple VeinMiner initialized");
    }
}
