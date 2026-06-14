package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.rhi.LuminRhi;
import com.github.epsilon.graphics.rhi.LuminRhiMinecraftTexture;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.resource.RhiFilter;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiImageUsage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiImageViewCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerAddressMode;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class TtfGlyphAtlas {

    private static final int SIZE = 512;
    private static final int GLYPH_GUTTER = 2;
    private static final int UV_INSET = 1;
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();

    private final Identifier textureId;
    private final RhiImage image;
    private final RhiImageView imageView;
    private final RhiSampler sampler;
    private final LuminRhiMinecraftTexture minecraftTexture;

    private int currentX = 0;
    private int currentY = 0;
    private int currentRowHeight = 0;

    public TtfGlyphAtlas(int atlasId) {
        this.textureId = Identifier.fromNamespaceAndPath("epsilon", "ttf_atlas/" + NEXT_TEXTURE_ID.getAndIncrement());

        LuminRhi rhi = LuminRhi.get();
        this.image = rhi.device().createImage(
                RhiImageCreateInfo.builder(RhiExtent3D.of2D(SIZE, SIZE))
                        .format(RhiFormat.R8_UNORM)
                        .usage(RhiImageUsage.SAMPLED)
                        .usage(RhiImageUsage.TRANSFER_DST)
                        .memoryUsage(RhiMemoryUsage.GPU_ONLY)
                        .build()
        );
        this.imageView = rhi.device().createImageView(RhiImageViewCreateInfo.of(this.image));
        this.sampler = rhi.device().createSampler(new RhiSamplerCreateInfo(
                RhiFilter.LINEAR,
                RhiFilter.LINEAR,
                RhiSamplerAddressMode.CLAMP_TO_EDGE,
                RhiSamplerAddressMode.CLAMP_TO_EDGE,
                RhiSamplerAddressMode.CLAMP_TO_EDGE,
                0.0f
        ));
        fillTextureWithTransparentDistance(rhi);

        this.minecraftTexture = new LuminRhiMinecraftTexture(
                this.image,
                SIZE,
                SIZE,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        );
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.minecraftTexture.texture());
    }

    private void fillTextureWithTransparentDistance(LuminRhi rhi) {
        ByteBuffer transparent = MemoryUtil.memAlloc(SIZE * SIZE);
        try {
            MemoryUtil.memSet(MemoryUtil.memAddress(transparent), 0xFF, SIZE * SIZE);
            rhi.uploadImageRegion(this.image, 0, 0, SIZE, SIZE, transparent);
        } finally {
            MemoryUtil.memFree(transparent);
        }
    }

    /**
     * 尝试把 glyph 追加到图集。
     * <p>
     * 图集已满时返回 null。
     */
    public GlyphUV appendGlyph(TtfGlyph glyph) {
        if (glyph.glyphData() == null) return null;

        int cellWidth = glyph.width() + GLYPH_GUTTER * 2;
        int cellHeight = glyph.height() + GLYPH_GUTTER * 2;

        if (currentX + cellWidth >= SIZE) {
            currentX = 0;
            currentY += currentRowHeight;
            currentRowHeight = 0;
        }

        // 图集空间不足时返回 null。
        if (currentY + cellHeight >= SIZE) {
            return null;
        }

        int glyphX = currentX + GLYPH_GUTTER;
        int glyphY = currentY + GLYPH_GUTTER;

        LuminRhi.get().uploadImageRegion(this.image, glyphX, glyphY, glyph.width(), glyph.height(), glyph.glyphData());

        GlyphUV uv = new GlyphUV(
                (float) (glyphX + UV_INSET) / SIZE,
                (float) (glyphY + UV_INSET) / SIZE,
                (float) (glyphX + glyph.width() - UV_INSET) / SIZE,
                (float) (glyphY + glyph.height() - UV_INSET) / SIZE
        );

        currentX += cellWidth;
        currentRowHeight = Math.max(currentRowHeight, cellHeight);

        return uv;
    }

    public RhiImageView getRhiImageView() {
        return imageView;
    }

    public RhiSampler getRhiSampler() {
        return sampler;
    }

    public LuminTexture getTexture() {
        return minecraftTexture.texture();
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public void destroy() {
        Minecraft.getInstance().getTextureManager().release(this.textureId);
        this.minecraftTexture.close();
        this.sampler.close();
        this.imageView.close();
        this.image.close();
    }

    public record GlyphUV(float u0, float v0, float u1, float v1) {
    }

}
