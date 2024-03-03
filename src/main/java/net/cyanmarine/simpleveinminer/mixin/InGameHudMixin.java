package net.cyanmarine.simpleveinminer.mixin;

import net.cyanmarine.simpleveinminer.config.SimpleConfigClient;
import net.cyanmarine.simpleveinminer.config.enums.*;
import net.cyanmarine.simpleveinminer.ifaces.IDrawContext;
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

    // Layer 1 of the frozen message
    @Unique private final String textToShow = "<  >";

    // Text widths (calculated with textRenderer.getWidth() )
    @Unique private final int textToShowWidth = 18; // 26 for "<    >"

    // Snowflake (layer 2 of the frozen message) initial X position
    @Unique private final int frozXOffset = 5; // 9 for "<    >"

    // Vertical space to subtract when the vertical anchor is set to BOTTOM
    // TODO: Draw before the chat messages box
    @Unique private final int HOTBAR_HEIGHT = 48;

    // The only place where client.player can be null is in InGameHud.tick
    // (called when the MinecraftClient just opens)
    //
    // Also, InGameHud.render() is called after ClientPlayConnectionEvents.JOIN event,
    // so client.player will *never* be null here
    @Inject(at = @At("TAIL"), method = "render(Lnet/minecraft/client/gui/DrawContext;F)V")
    public void renderTailInject(DrawContext context, float tickDelta, CallbackInfo ci) {
        boolean serverNOTSneaky = isVeinMiningServerSide && ! client.player.isSneaking();
        if (blocksToHighlight == null || (!veinMineKeybind.isPressed() && serverNOTSneaky)) return;
        SimpleConfigClient.HudDisplay hudDisplay = getConfig().hudDisplay;
        if (!hudDisplay.showCount && !hudDisplay.showBlock) return;
        TextRenderer textRenderer = this.getTextRenderer();

        ItemStack stack = currentlyOutliningState.getBlock().asItem().getDefaultStack();
        boolean showBlock = hudDisplay.showBlock && !stack.isEmpty();

        String countStr = String.valueOf(blocksToHighlight.size());

        // Frozen preview indicator position & text width of count
        float frozX = 0, frozY = 0;
        int countStrWidth = textRenderer.getWidth(countStr);

        int x = 0, y = 0;
        switch (hudDisplay.horizontal_anchor) {
            case CENTER -> x = client.getWindow().getScaledWidth() / 2 - 8;
            case RIGHT -> {
                int countOffset = 6 + (showBlock ? 16 : 0)  + (hudDisplay.showCount ? countStrWidth : 0);
                if (previewFrozen  && hudDisplay.snowflake_anchor == SnowflakeAnchor.RIGHT)
                    countOffset += textToShowWidth + (hudDisplay.showCount ? 4 : 0);
                x = client.getWindow().getScaledWidth() - countOffset;
            }
        }
        switch (hudDisplay.vertical_anchor) {
            case TOP -> {
                if(previewFrozen &&
                        (hudDisplay.snowflake_anchor == SnowflakeAnchor.TOP ||
                                hudDisplay.snowflake_anchor == SnowflakeAnchor.MIXED_TOP))
                    y += textRenderer.fontHeight + 4;
            }
            case CENTER -> y = client.getWindow().getScaledHeight() / 2 - 8;
            case BOTTOM -> {
                y = client.getWindow().getScaledHeight() - 16 - HOTBAR_HEIGHT;
                if(previewFrozen &&
                        (hudDisplay.snowflake_anchor == SnowflakeAnchor.BOTTOM ||
                                hudDisplay.snowflake_anchor == SnowflakeAnchor.MIXED_BOTTOM))
                    y -= textRenderer.fontHeight + 4;
            }
        }
        if (hudDisplay.horizontal_anchor == HorizontalAnchor.RIGHT) x -= hudDisplay.x;
        else x += hudDisplay.x;
        if (hudDisplay.vertical_anchor == VerticalAnchor.BOTTOM) y -= hudDisplay.y;
        else y += hudDisplay.y;

        frozX += x - .5f;
        if (showBlock) {
            context.drawItem(stack, x, y);
            if (hudDisplay.showCount || hudDisplay.snowflake_anchor == SnowflakeAnchor.RIGHT)
                x += hudDisplay.blockNumberSpacing;
        }

        y += textRenderer.fontHeight / 2;
        if (hudDisplay.showCount) {
            x += 6;
            if (hudDisplay.snowflake_anchor != SnowflakeAnchor.TOP
                    && hudDisplay.snowflake_anchor != SnowflakeAnchor.BOTTOM) frozX = x + countStrWidth + 4;

            context.drawText(textRenderer, countStr, x, y, 0xFFFFFF, true);
        } else if (hudDisplay.snowflake_anchor == SnowflakeAnchor.RIGHT) frozX = x + 6;

        if (previewFrozen) {
            frozY += y;
            switch (hudDisplay.snowflake_anchor){
                case MIXED_TOP: if(hudDisplay.showCount) break;
                case TOP: frozY -= 1.5f * textRenderer.fontHeight; break;
                case MIXED_BOTTOM: if(hudDisplay.showCount) break;
                case BOTTOM: frozY += 1.5f * textRenderer.fontHeight; break;
            }

            ((IDrawContext) context).drawText(textRenderer, textToShow, frozX , frozY, 0xFFFFFF, true);

            frozX += frozXOffset;
            ((IDrawContext) context).drawText(textRenderer, "❄", frozX , frozY, 0x7cfaff, true);
        }
    }
}
