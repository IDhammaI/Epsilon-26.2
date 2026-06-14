package com.github.epsilon.graphics.shaders;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.rhi.LuminRhi;
import com.github.epsilon.graphics.rhi.LuminRhiUniformBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class BlurShader implements AutoCloseable {

    public static final BlurShader INSTANCE = new BlurShader();

    private static final int UNIFORMS_SIZE = 64;

    private LuminRhiUniformBuffer uniforms;
    private RenderTarget input;

    private void ensureResources() {
        if (this.uniforms == null) {
            this.uniforms = new LuminRhiUniformBuffer(UNIFORMS_SIZE);
        }
    }

    public void render(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color color, float blurStrength) {
        this.ensureResources();

        RenderTarget fb = mc.getMainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) {
            return;
        }

        int fbWidth = mc.getWindow().getWidth();
        int fbHeight = mc.getWindow().getHeight();

        if (input == null) {
            input = new TextureTarget("Lumin Blur Input", fbWidth, fbHeight, false);
        }

        if (this.input.width != fbWidth || this.input.height != fbHeight) {
            this.input.resize(fbWidth, fbHeight);
        }

        LuminRenderSystem.ScissorRect blurRect = LuminRenderSystem.toFramebufferScissor(x, y, width, height);
        float scale = (float) LuminRenderSystem.getGuiScale();
        float pxX = blurRect.x();
        float pxY = blurRect.y();
        float pxW = blurRect.width();
        float pxH = blurRect.height();

        float rTLPx = Math.max(0.0f, rTL * scale);
        float rTRPx = Math.max(0.0f, rTR * scale);
        float rBRPx = Math.max(0.0f, rBR * scale);
        float rBLPx = Math.max(0.0f, rBL * scale);

        float quality = Math.max(0.0f, blurStrength);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getColorTexture(),
                input.getColorTexture(),
                0, 0, 0, 0, 0,
                fb.width, fb.height
        );

        this.uniforms.clear()
                .putVec3(fb.width, fb.height, quality)
                .putVec4(pxW, pxH, pxX, pxY)
                .putVec4(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, 1.0f)
                .putVec4(rTLPx, rTRPx, rBRPx, rBLPx)
                .upload();

        LuminRhi rhi = LuminRhi.get();
        rhi.drawFullscreenTexturedTo(
                "Lumin Blur",
                rhi.pipelines().blur,
                this.uniforms.buffer(),
                3,
                blurRect,
                fb.getColorTextureView(),
                fb.getDepthTextureView(),
                fb.width,
                fb.height,
                input.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        );
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength) {
        render(x, y, width, height, radius, radius, radius, radius, new Color(0, 0, 0, 0), blurStrength);
    }

    public void render(float x, float y, float width, float height, float radius, Color color, float blurStrength) {
        render(x, y, width, height, radius, radius, radius, radius, color, blurStrength);
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
