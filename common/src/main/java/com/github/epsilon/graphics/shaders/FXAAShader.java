package com.github.epsilon.graphics.shaders;

import com.github.epsilon.graphics.rhi.LuminRhi;
import com.github.epsilon.graphics.rhi.LuminRhiUniformBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import static com.github.epsilon.Constants.mc;

public class FXAAShader implements AutoCloseable {

    public static final FXAAShader INSTANCE = new FXAAShader();

    private static final int UNIFORMS_SIZE = 16;

    private LuminRhiUniformBuffer uniforms;
    private RenderTarget input;

    private void ensureResources() {
        if (this.uniforms == null) {
            this.uniforms = new LuminRhiUniformBuffer(UNIFORMS_SIZE);
        }
    }

    private void ensureInput(RenderTarget framebuffer) {
        int fbWidth = framebuffer.width;
        int fbHeight = framebuffer.height;

        if (this.input == null) {
            this.input = new TextureTarget("Epsilon FXAA Input", fbWidth, fbHeight, false);
        }

        if (this.input.width != fbWidth || this.input.height != fbHeight) {
            this.input.resize(fbWidth, fbHeight);
        }
    }

    public void renderMainTarget() {
        render(mc.getMainRenderTarget());
    }

    public void render(RenderTarget framebuffer) {
        this.ensureResources();

        if (framebuffer == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
            return;
        }

        if (framebuffer.getColorTexture() == null || framebuffer.getColorTextureView() == null) {
            return;
        }

        this.ensureInput(framebuffer);

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                framebuffer.getColorTexture(),
                this.input.getColorTexture(),
                0, 0, 0, 0, 0,
                framebuffer.width, framebuffer.height
        );

        this.uniforms.clear()
                .putVec4(framebuffer.width, framebuffer.height, 1.0f / framebuffer.width, 1.0f / framebuffer.height)
                .upload();

        LuminRhi rhi = LuminRhi.get();
        rhi.drawFullscreenTexturedTo(
                "Epsilon FXAA",
                rhi.pipelines().fxaa,
                this.uniforms.buffer(),
                6,
                null,
                framebuffer.getColorTextureView(),
                framebuffer.getDepthTextureView(),
                framebuffer.width,
                framebuffer.height,
                this.input.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        );
    }

    @Override
    public void close() {
        if (input != null) {
            input.destroyBuffers();
            input = null;
        }
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

}
