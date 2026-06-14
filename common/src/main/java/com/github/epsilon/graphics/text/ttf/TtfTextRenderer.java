package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.BufferUtils;
import com.github.epsilon.graphics.rhi.LuminRhi;
import com.github.epsilon.graphics.rhi.LuminRhiBuffer;
import com.github.epsilon.graphics.text.GlyphDescriptor;
import com.github.epsilon.graphics.text.ITextRenderer;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class TtfTextRenderer implements ITextRenderer {

    private static final float DEFAULT_SCALE = 0.27f;
    private static final float SPACING = 0f;
    private static final int STRIDE = 24;
    private final long bufferSize;

    private final Map<TtfGlyphAtlas, Batch> batches = new LinkedHashMap<>();

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;

    public TtfTextRenderer(long bufferSize) {
        this.bufferSize = bufferSize;
    }

    public TtfTextRenderer() {
        this(2 * 1024 * 1024);
    }

    @Override
    public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
        final var finalScale = scale * DEFAULT_SCALE;
        fontLoader.requestChars(text);
        fontLoader.drainReadyGlyphs();
        int argb = ARGB.toABGR(color.getRGB());

        float xOffset = 0f;
        float yOffset = 0f;

        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            if (codepoint == ' ') {
                xOffset += 3.0f * scale;
                continue;
            }
            if (codepoint == '\n') {
                xOffset = 0f;
                yOffset += fontLoader.fontFile.fontHeight * finalScale;
                continue;
            }

            GlyphDescriptor glyph = fontLoader.getGlyph(codepoint);
            if (glyph == null) {
                fontLoader.checkAndLoadCodepoint(codepoint);
                glyph = fontLoader.getGlyph(codepoint);
            }
            if (glyph == null) continue;

            TtfGlyphAtlas atlas = glyph.atlas();

            Batch batch = batches.computeIfAbsent(atlas, k -> new Batch(new LuminRhiBuffer(bufferSize, RhiBufferUsage.VERTEX_BUFFER)));

            float baselineY = yOffset + y + (fontLoader.fontFile.pixelAscent * finalScale);
            float x1 = x + xOffset;
            float x2 = x1 + glyph.width() * finalScale;
            float y1 = baselineY + glyph.yOffset() * finalScale;
            float y2 = y1 + glyph.height() * finalScale;

            long baseAddr = MemoryUtil.memAddress(batch.buffer.getMappedBuffer());
            long p = baseAddr + batch.offsetInAtlas;

            BufferUtils.writeUvRectToAddr(p, x1, y1, glyph.uv().u0(), glyph.uv().v0(), argb);
            BufferUtils.writeUvRectToAddr(p + STRIDE, x1, y2, glyph.uv().u0(), glyph.uv().v1(), argb);
            BufferUtils.writeUvRectToAddr(p + STRIDE * 2, x2, y2, glyph.uv().u1(), glyph.uv().v1(), argb);
            BufferUtils.writeUvRectToAddr(p + STRIDE * 3, x2, y1, glyph.uv().u1(), glyph.uv().v0(), argb);

            batch.offsetInAtlas += (STRIDE * 4);
            xOffset += glyph.advance() * finalScale + SPACING * scale;
        }
    }

    @Override
    public void draw() {
        if (batches.isEmpty()) return;

        LuminRhi rhi = LuminRhi.get();

        for (Map.Entry<TtfGlyphAtlas, Batch> entry : batches.entrySet()) {
            final var atlas = entry.getKey();
            final var batch = entry.getValue();

            if (batch.offsetInAtlas == 0) continue;

            int vertexCount = (int) (batch.offsetInAtlas / STRIDE);
            batch.buffer.upload(batch.offsetInAtlas);

            LuminRenderSystem.ScissorRect scissor = scissorEnabled ? new LuminRenderSystem.ScissorRect(scissorX, scissorY, scissorW, scissorH) : null;
            rhi.drawTexturedQuads(
                    "Lumin TTF Draw",
                    ClientSetting.INSTANCE.fontAntiAliasing.getValue() ? rhi.pipelines().ttfAa : rhi.pipelines().ttfNoAa,
                    batch.buffer.getGpuBuffer(),
                    vertexCount,
                    scissor,
                    atlas.getRhiImageView(),
                    atlas.getRhiSampler()
            );
        }
    }

    @Override
    public void clear() {
        for (Batch batch : batches.values()) {
            if (batch.offsetInAtlas > 0) {
                batch.buffer.rotate();
            }
            batch.offsetInAtlas = 0;
        }
    }

    @Override
    public void close() {
        clear();
        for (Batch batch : batches.values()) {
            batch.buffer.close();
        }
        batches.clear();
    }

    @Override
    public float getHeight(float scale, TtfFontLoader fontLoader) {
        return fontLoader.fontFile.pixelAscent * DEFAULT_SCALE * scale;
    }

    @Override
    public float getLineHeight(float scale, TtfFontLoader fontLoader) {
        return fontLoader.fontFile.fontHeight * DEFAULT_SCALE * scale;
    }

    @Override
    public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
        fontLoader.checkAndLoadChars(text);
        final var finalScale = scale * DEFAULT_SCALE;
        float maxLine = 0.0f;
        float currentLine = 0.0f;

        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            if (codepoint == ' ') {
                currentLine += 3.0f * scale;
            } else if (codepoint == '\n') {
                maxLine = Math.max(maxLine, currentLine);
                currentLine = 0.0f;
            } else {
                GlyphDescriptor glyph = fontLoader.getGlyph(codepoint);
                if (glyph != null) {
                    currentLine += glyph.advance() * finalScale + SPACING * scale;
                }
            }
        }
        return Math.max(maxLine, currentLine);
    }

    @Override
    public void setScissor(int x, int y, int width, int height) {
        scissorEnabled = true;
        scissorX = x;
        scissorY = y;
        scissorW = width;
        scissorH = height;
    }

    @Override
    public void clearScissor() {
        scissorEnabled = false;
    }

    private static final class Batch {
        final LuminRhiBuffer buffer;
        long offsetInAtlas = 0;

        private Batch(LuminRhiBuffer buffer) {
            this.buffer = buffer;
        }
    }
}
