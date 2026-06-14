package com.github.epsilon.graphics.rhi;

import com.github.epsilon.graphics.LuminTexture;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

public final class LuminRhiMinecraftTexture implements AutoCloseable {

    private final WrappedGlTexture texture;
    private final GpuTextureView textureView;
    private final LuminTexture minecraftTexture;
    private boolean closed;

    public LuminRhiMinecraftTexture(RhiImage image, int width, int height, GpuSampler sampler) {
        this.texture = new WrappedGlTexture(image.nativeHandle(), width, height);
        this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
        this.minecraftTexture = new LuminTexture(this.texture, this.textureView, sampler, false, false);
    }

    public LuminTexture texture() {
        return minecraftTexture;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            this.textureView.close();
            this.texture.close();
        }
    }

    private static final class WrappedGlTexture extends GlTexture {
        private WrappedGlTexture(long handle, int width, int height) {
            super(
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    "Lumin-RHI-TtfGlyphAtlas-MinecraftView",
                    TextureFormat.RED8,
                    width,
                    height,
                    1,
                    1,
                    Math.toIntExact(handle)
            );
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public void addViews() {
        }

        @Override
        public void removeViews() {
        }
    }
}
