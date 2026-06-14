package com.github.epsilon.graphics.renderers;

import com.github.epsilon.assets.holders.RendererHolder;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.elements.ShadowElement;
import com.github.epsilon.graphics.rhi.LuminRhi;
import com.github.epsilon.graphics.rhi.LuminRhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;

public class ShadowRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 256 * 1024;
    private final LuminRhiBuffer buffer = new LuminRhiBuffer(BUFFER_SIZE, RhiBufferUsage.VERTEX_BUFFER);

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private long currentOffset = 0;
    private int vertexCount = 0;

    private ShadowRenderer() {
    }

    public static ShadowRenderer create() {
        return RendererHolder.INSTANCE.register(new ShadowRenderer());
    }

    public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
        addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
    }

    public void addShadow(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurRadius, Color color) {
        buffer.tryMap();

        float vx = x - blurRadius;
        float vy = y - blurRadius;
        float vx2 = x + width + blurRadius;
        float vy2 = y + height + blurRadius;

        float bx2 = x + width;
        float by2 = y + height;

        int argb = ARGB.toABGR(color.getRGB());

        addVertex(vx, vy, x, y, bx2, by2, rTL, rTR, rBR, rBL, blurRadius, argb);
        addVertex(vx, vy2, x, y, bx2, by2, rTL, rTR, rBR, rBL, blurRadius, argb);
        addVertex(vx2, vy2, x, y, bx2, by2, rTL, rTR, rBR, rBL, blurRadius, argb);
        addVertex(vx2, vy, x, y, bx2, by2, rTL, rTR, rBR, rBL, blurRadius, argb);
    }

    public void addElement(ShadowElement element) {
        addShadow(
                element.x(),
                element.y(),
                element.width(),
                element.height(),
                element.radiusTopLeft(),
                element.radiusTopRight(),
                element.radiusBottomRight(),
                element.radiusBottomLeft(),
                element.blurRadius(),
                element.color()
        );
    }

    public void addElements(Iterable<ShadowElement> elements) {
        for (ShadowElement element : elements) {
            addElement(element);
        }
    }

    private void addVertex(float vx, float vy, float rx1, float ry1, float rx2, float ry2, float r1, float r2, float r3, float r4, float blurRadius, int color) {
        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;

        MemoryUtil.memPutFloat(p, vx);
        MemoryUtil.memPutFloat(p + 4, vy);
        MemoryUtil.memPutFloat(p + 8, blurRadius);
        MemoryUtil.memPutInt(p + 12, color);

        MemoryUtil.memPutFloat(p + 16, rx1);
        MemoryUtil.memPutFloat(p + 20, ry1);
        MemoryUtil.memPutFloat(p + 24, rx2);
        MemoryUtil.memPutFloat(p + 28, ry2);

        MemoryUtil.memPutFloat(p + 32, r1);
        MemoryUtil.memPutFloat(p + 36, r2);
        MemoryUtil.memPutFloat(p + 40, r3);
        MemoryUtil.memPutFloat(p + 44, r4);

        currentOffset += 48;
        vertexCount++;
    }

    public void setScissor(int x, int y, int width, int height) {
        if (x < 0 || y < 0) {
            return;
        }

        scissorEnabled = true;
        scissorX = x;
        scissorY = y;
        scissorW = width;
        scissorH = height;
    }

    public void clearScissor() {
        scissorEnabled = false;
    }

    @Override
    public void draw() {
        if (vertexCount == 0) return;
        buffer.upload(currentOffset);
        LuminRenderSystem.ScissorRect scissor = scissorEnabled ? new LuminRenderSystem.ScissorRect(scissorX, scissorY, scissorW, scissorH) : null;
        LuminRhi rhi = LuminRhi.get();
        rhi.drawQuads("Lumin Shadow Draw", rhi.pipelines().shadow, buffer.getGpuBuffer(), vertexCount, scissor);
    }

    @Override
    public void clear() {
        if (vertexCount > 0) {
            buffer.rotate();
        }
        vertexCount = 0;
        currentOffset = 0;
    }

    @Override
    public void close() {
        buffer.close();
    }

}
