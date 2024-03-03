package net.cyanmarine.simpleveinminer.mixin;

import net.cyanmarine.simpleveinminer.config.SimpleConfigClient;
import net.cyanmarine.simpleveinminer.config.enums.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.cyanmarine.simpleveinminer.client.SimpleVeinminerClient.*;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow public abstract TextRenderer getTextRenderer();

    @Shadow @Final private MinecraftClient client;

    // Value to subtract when the vertical anchor is set to BOTTOM
    // TODO: Draw the block item before the chat box
    @Unique private final int HOTBAR_HEIGHT = 48;
    @Inject(at = @At("TAIL"), method = "render(Lnet/minecraft/client/gui/DrawContext;F)V")
    public void renderTailInject(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (blocksToHighlight == null) return;
        SimpleConfigClient.HudDisplay hudDisplay = getConfig().hudDisplay;
        if (!hudDisplay.showCount && !hudDisplay.showBlock) return;
        TextRenderer textRenderer = this.getTextRenderer();

        ItemStack stack = currentlyOutliningState.getBlock().asItem().getDefaultStack();
        boolean showBlock = hudDisplay.showBlock && !stack.isEmpty();

        int count = blocksToHighlight.size();

        int x = 0, y = 0;
        switch (hudDisplay.horizontal_anchor) {
            case CENTER -> x = client.getWindow().getScaledWidth() / 2 - 8;
            case RIGHT ->
                x = client.getWindow().getScaledWidth() - ((showBlock ? 16 : 0) + (hudDisplay.showCount ? textRenderer.getWidth(String.valueOf(count)) + 6: 0));

        }
        switch (hudDisplay.vertical_anchor) {
            case CENTER -> y = client.getWindow().getScaledHeight() / 2 - 8;
            case BOTTOM -> y = client.getWindow().getScaledHeight()  - 16 - HOTBAR_HEIGHT;
        }
        if (hudDisplay.horizontal_anchor == HorizontalAnchor.RIGHT) x -= hudDisplay.x;
        else x += hudDisplay.x;
        if (hudDisplay.vertical_anchor == VerticalAnchor.BOTTOM) y -= hudDisplay.y;
        else y += hudDisplay.y;

        if (showBlock) {
            context.drawItem(stack, x, y);
        }

        if (hudDisplay.showCount) {
            context.drawText(textRenderer, String.valueOf(count), x + 6 + (showBlock ? hudDisplay.blockNumberSpacing : 0), y + textRenderer.fontHeight / 2, 0xFFFFFF, true);
        }
    }
}
