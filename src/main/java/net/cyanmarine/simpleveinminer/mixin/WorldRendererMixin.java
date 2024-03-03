package net.cyanmarine.simpleveinminer.mixin;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.shedaniel.math.Color;
import net.cyanmarine.simpleveinminer.SimpleVeinminer;
import net.cyanmarine.simpleveinminer.client.SimpleVeinminerClient;
import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.cyanmarine.simpleveinminer.config.SimpleConfigClient;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Set;
import java.util.SortedSet;

import static net.cyanmarine.simpleveinminer.client.SimpleVeinminerClient.*;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Unique
    Item holding;
    @Unique
    BlockPos currentlyOutliningPos;
    @Unique
    ArrayList<BlockPos> beingBroken;
    @Unique
    float red, green, blue, alpha;
    @Unique
    int delay1 = 0;
    @Unique
    int delay2 = 0;
    @Unique
    boolean resetCountdown = false;
    @Shadow
    @Nullable
    private ClientWorld world;
    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    protected static void drawCuboidShapeOutline(MatrixStack matrices, VertexConsumer vertexConsumer, VoxelShape shape, double offsetX, double offsetY, double offsetZ, float red, float green, float blue, float alpha) {
    }

    @Shadow
    protected abstract void removeBlockBreakingInfo(BlockBreakingInfo info);

    @Unique
    private void renderPreview(PlayerEntity player, MatrixStack matrices) {
            assert world != null;
            assert blocksToHighlight != null;
            SimpleConfigClient config = SimpleVeinminerClient.getConfig();
            SimpleConfigClient.Highlight outline = config.highlight;

          /*if (outline.mode == SimpleConfigClient.Highlight.MODES.OUTLINE)
                    for (BlockPos currentPos : blocksToHighlight)
                        outline(matrices, vertexConsumer, entity, d, e, f, currentPos, world.getBlockState(currentPos), red, green, blue, alpha);
                else*/ {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();



            RenderSystem.setShader(GameRenderer::getPositionColorTexProgram);
            RenderSystem.setShaderTexture(0, SimpleVeinminer.getId("highlight.png"));
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);

            Camera camera = client.gameRenderer.getCamera();

            alpha = Math.max(0.11f, alpha); // Prevents the highlight from being invisible

            boolean b = SimpleVeinminerClient.getConfig().highlight.highlightAllSides;



            for (BlockPos highlight : blocksToHighlight) {
                Vec3d targetPosition = new Vec3d(highlight.getX(), highlight.getY(), highlight.getZ());
                Vec3d transformedPosition = targetPosition.subtract(camera.getPos());

                MatrixStack matrixStack = new MatrixStack();
                matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
                matrixStack.translate(transformedPosition.x, transformedPosition.y, transformedPosition.z);
                Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();

                VoxelShape shape = world.getBlockState(highlight).getOutlineShape(world, highlight, ShapeContext.of(player));


                switch (outline.mode) {
                    case SHAPE -> shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
                        drawBox(buffer, positionMatrix, red, green, blue, alpha, box, highlight, blocksToHighlight, b);
                    });
                    case CUBE ->
                        drawBox(buffer, positionMatrix, red, green, blue, alpha, new Box(0, 0, 0, 1, 1, 1), highlight, blocksToHighlight, b);
                    case CUBE_SHAPE ->
                        drawBox(buffer, positionMatrix, red, green, blue, alpha, shape.getBoundingBox(), highlight, blocksToHighlight, b);
                    case OUTLINE -> drawOutline(buffer, positionMatrix, red, green, blue, alpha, shape, highlight, blocksToHighlight, b, matrices);
                }
            }

            tessellator.draw();

            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
        }
    }

    @Inject(at = @At("HEAD"), method = "drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;DDDLnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", cancellable = true)
    public void drawBlockOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity, double d, double e, double f, BlockPos pos, BlockState state, CallbackInfo ci) {
        //SimpleVeinminerClient.LOGGER.info(SimpleVeinminerClient.isInstalledOnServerSide.get()?"true":"false");
        if (SimpleVeinminerClient.isInstalledOnServerSide.get() && entity.isPlayer()) {
            PlayerEntity player = (PlayerEntity) entity;
            SimpleConfigClient config = SimpleVeinminerClient.getConfig();
            SimpleConfigClient.Highlight outline = config.highlight;
            SimpleConfig.SimpleConfigCopy worldConfig = SimpleVeinminerClient.getWorldConfig();
            int updateRate = SimpleVeinminerClient.getConfig().highlight.updateRate;
            if ((outline.doHighlight || config.hudDisplay.showCount || config.hudDisplay.showBlock) && (SimpleVeinminerClient.veinMineKeybind.isPressed() || (SimpleVeinminerClient.isVeinMiningServerSide && player.isSneaking())) && SimpleVeinminer.canVeinmine(player, world, pos, state, worldConfig.restrictions)) {
                ci.cancel();

                assert client.player != null;
                Item hand = client.player.getMainHandStack().getItem();

                if (blocksToHighlight == null || (!previewFrozen && (config.isChanged() || (updateRate > 0 && delay1 % updateRate == 0) || !pos.equals(currentlyOutliningPos) || !state.equals(currentlyOutliningState) || !hand.equals(holding)))) {
                    reset();
                    holding = hand;
                    Color outlineColor = outline.color;

                    blocksToHighlight = getBlocksToOutline(pos, state, player, /*outline.onlyExposed*/ false);
                    currentlyOutliningPos = pos;
                    currentlyOutliningState = state;

                    red = outlineColor.getRed() / 255.0f;
                    green = outlineColor.getGreen() / 255.0f;
                    blue = outlineColor.getBlue() / 255.0f;
                    alpha = ((float) outline.opacity) / 100.0f;

                    resetCountdown = true;
                }
                if (outline.doHighlight && outline.mode == SimpleConfigClient.Highlight.MODES.OUTLINE) {
                    for (BlockPos currentPos : blocksToHighlight)
                        outline(matrices, vertexConsumer, entity, d, e, f, currentPos, world.getBlockState(currentPos), red, green, blue, alpha);
                }
                delay1++;
            } else if (!previewFrozen && currentlyOutliningPos != null) {
                reset();
            }
        }
    }

    @Unique
    private void reset() {
        clearBlockBreakingProgressions();
        currentlyOutliningPos = null;
        currentlyOutliningState = null;
        blocksToHighlight = null;
        holding = null;
        delay1 = 0;
        delay2 = 0;
        resetCountdown = false;
    }

    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V")
    public void renderInject(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, CallbackInfo ci) {
        ClientPlayerEntity player = client.player;
        SimpleConfigClient config = SimpleVeinminerClient.getConfig();
        if (player == null || world == null) return;
        if (!previewFrozen && resetCountdown && (config.highlight.updateRate > 0 && ++delay2 % config.highlight.updateRate == 0)) {
            if (world.getBlockState(SimpleVeinminer.getBlockHitResult(client.player).getBlockPos()).isAir()) {
                reset();
                return;
            }
            delay2 = 0;
        }

        clearBlockBreakingProgressions();
        if (blocksToHighlight != null && currentlyOutliningPos != null && SimpleVeinminerClient.veinMining && config.showMiningProgress) {
            SortedSet<BlockBreakingInfo> blockBreakingSet = blockBreakingProgressions.get(currentlyOutliningPos.asLong());
            if (blockBreakingSet != null) {
                BlockBreakingInfo blockBreakingProgress = blockBreakingSet.last();
                int stage = blockBreakingProgress.getStage();

                if (beingBroken == null)
                    beingBroken = (ArrayList<BlockPos>) blocksToHighlight.clone();

                for (BlockPos currentPos : blocksToHighlight) {
                    if (currentPos.equals(currentlyOutliningPos)) continue;
                    BlockBreakingInfo newBlockBreakingProgress = new BlockBreakingInfo(blockBreakingProgress.hashCode(), currentPos);
                    newBlockBreakingProgress.setStage(stage);
                    (blockBreakingProgressions.computeIfAbsent(currentPos.asLong(), (l) -> Sets.newTreeSet())).add(newBlockBreakingProgress);
                }

            }
        }
    }
    @Inject(at = @At("TAIL"), method = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V")
    public void renderInjectTail(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, CallbackInfo ci) {
        SimpleConfigClient config = SimpleVeinminerClient.getConfig();
        if (blocksToHighlight != null && config.highlight.doHighlight) {
            HitResult hitResult = client.crosshairTarget;
            if (!previewFrozen && (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK)) reset();
            else {
                if (config.highlight.mode != SimpleConfigClient.Highlight.MODES.OUTLINE)
                    renderPreview(client.player, matrices);
            }
        }
    }


    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/client/render/WorldRenderer;removeBlockBreakingInfo(Lnet/minecraft/client/render/BlockBreakingInfo;)V", cancellable = true)
    public void removeBlockBreakingInfoReplace(BlockBreakingInfo info, CallbackInfo ci) {
        ci.cancel();
        long l = info.getPos().asLong();
        Set<BlockBreakingInfo> set = this.blockBreakingProgressions.get(l);
        if (set != null) {
            set.remove(info);
            if (set.isEmpty()) this.blockBreakingProgressions.remove(l);
        }
    }

    @Unique
    private void clearBlockBreakingProgressions() {
        if (beingBroken != null && blockBreakingProgressions != null)
            for (BlockPos pos : beingBroken) {
                if (pos.equals(currentlyOutliningPos)) continue;
                long l = pos.asLong();
                SortedSet<BlockBreakingInfo> set = this.blockBreakingProgressions.get(l);
                if (set != null) {
                    BlockBreakingInfo info = set.last();
                    removeBlockBreakingInfo(info);
                }
            }
        beingBroken = null;
    }

    @Unique
    private ArrayList<BlockPos> getBlocksToOutline(BlockPos pos, BlockState state, PlayerEntity player, boolean onlyExposed) {
        ArrayList<BlockPos> willVeinmine = SimpleVeinminer.getBlocksToVeinmine(pos, state, SimpleVeinminer.getMaxBlocks(holding), SimpleVeinminer.getVeinminingRadius(player), SimpleVeinminer.getSpreadAccuracy(), player, SimpleVeinminer.isDebug(), getConfig().restrictions.onlyBreakBottomBlockForChainReactions);
        if (!onlyExposed) return willVeinmine;
        ArrayList<BlockPos> willOutline = new ArrayList<>();

        for (BlockPos currentPos : willVeinmine) {
            if (hasFaceVisible(currentPos)) willOutline.add(currentPos);
        }

        return willOutline;
    }

    @Unique
    private boolean hasFaceVisible(BlockPos pos) {
        if (world == null) return false;
        BlockPos[] neighbors = SimpleVeinminer.getNeighbors(pos);
        for (BlockPos neighbor : neighbors)
            if (!world.getBlockState(neighbor).isSolidBlock(world, pos)) return true;
        return false;
    }

    @Unique
    private void outline(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity, double d, double e, double f, BlockPos pos, BlockState state, float r, float g, float b, float a) {
        drawCuboidShapeOutline(matrices, vertexConsumer, state.getOutlineShape(world, pos, ShapeContext.of(entity)), (double) pos.getX() - d, (double) pos.getY() - e, (double) pos.getZ() - f, r, g, b, a);
    }
}

