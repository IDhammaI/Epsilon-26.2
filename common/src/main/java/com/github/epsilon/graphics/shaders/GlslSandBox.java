package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.rhi.LuminRhi;
import com.github.epsilon.graphics.rhi.LuminRhiUniformBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import static com.github.epsilon.Constants.mc;

public class GlslSandBox implements AutoCloseable {

    public static final GlslSandBox INSTANCE = new GlslSandBox();

    public static final Identifier BLACK_HOLE = ResourceLocationUtils.getIdentifier("menu/black_hole");
    public static final Identifier MINECRAFT = ResourceLocationUtils.getIdentifier("menu/minecraft");
    public static final Identifier PLANET = ResourceLocationUtils.getIdentifier("menu/planet");

    private static final int SANDBOX_INFO_SIZE = 32;

    private LuminRhiUniformBuffer sandboxInfoUniformBuf;
    private long initTime = Util.getMillis();

    private void ensureUniformBuffer() {
        if (sandboxInfoUniformBuf == null) {
            sandboxInfoUniformBuf = new LuminRhiUniformBuffer(SANDBOX_INFO_SIZE);
        }
    }

    public void resetTime() {
        initTime = Util.getMillis();
    }

    public void render(Identifier fragmentShader, double mouseX, double mouseY) {
        render(fragmentShader, mouseX, mouseY, initTime);
    }

    public void render(Identifier fragmentShader, double mouseX, double mouseY, long startTimeMs) {
        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        if (colorView == null) return;

        ensureUniformBuffer();

        final var activeTarget = LuminRenderSystem.getActiveTarget();
        final int targetWidth = activeTarget != null ? activeTarget.width() : mc.getMainRenderTarget().width;
        final int targetHeight = activeTarget != null ? activeTarget.height() : mc.getMainRenderTarget().height;

        if (targetWidth <= 0 || targetHeight <= 0) return;

        float scaleX = targetWidth / LuminRenderSystem.getScaledWidth();
        float scaleY = targetHeight / LuminRenderSystem.getScaledHeight();

        float mousePxX = (float) mouseX * scaleX;
        float mousePxY = (float) mouseY * scaleY;
        float mouseUvX = mousePxX / targetWidth;
        float mouseUvY = (targetHeight - 1.0f - mousePxY) / targetHeight;
        float elapsedTime = (Util.getMillis() - startTimeMs) / 1000.0f;

        sandboxInfoUniformBuf.clear()
                .putVec4(targetWidth, targetHeight, elapsedTime, 0.0f)
                .putVec4(mouseUvX, mouseUvY, mousePxX, mousePxY)
                .upload();

        LuminRhi rhi = LuminRhi.get();
        rhi.drawFullscreen(
                "Lumin GLSL Sandbox",
                rhi.pipelines().sandbox(toShaderPath(fragmentShader)),
                sandboxInfoUniformBuf.buffer(),
                6,
                null
        );
    }

    private String toShaderPath(Identifier shader) {
        return shader.getPath() + ".fsh";
    }

    @Override
    public void close() {
        if (sandboxInfoUniformBuf != null) {
            sandboxInfoUniformBuf.close();
            sandboxInfoUniformBuf = null;
        }
    }

}
