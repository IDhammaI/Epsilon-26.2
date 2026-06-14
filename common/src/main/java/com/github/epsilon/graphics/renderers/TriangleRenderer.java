package com.github.epsilon.graphics.renderers;

import com.github.epsilon.assets.holders.RendererHolder;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.rhi.LuminRhi;
import com.github.epsilon.graphics.rhi.LuminRhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;

public class TriangleRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 64 * 1024;
    private static final int STRIDE = 16;

    private final LuminRhiBuffer buffer = new LuminRhiBuffer(BUFFER_SIZE, RhiBufferUsage.VERTEX_BUFFER);

    private long currentOffset = 0;
    private int vertexCount = 0;

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;

    private TriangleRenderer() {
    }

    public static TriangleRenderer create() {
        return RendererHolder.INSTANCE.register(new TriangleRenderer());
    }

    public void addChevronTriangle(float centerX, float centerY, float size, float progress, Color color) {
        buffer.tryMap();

        int abgr = ARGB.toABGR(color.getRGB());
        float clampedProgress = Math.clamp(progress, 0.0f, 1.0f);

        float tipXR = centerX + size;
        float tipYR = centerY;
        float base1XR = centerX - size;
        float base1YR = centerY - size;
        float base2XR = centerX - size;
        float base2YR = centerY + size;

        float tipXD = centerX;
        float tipYD = centerY + size;
        float base1XD = centerX - size;
        float base1YD = centerY - size;
        float base2XD = centerX + size;
        float base2YD = centerY - size;

        float x1 = base1XR + (base1XD - base1XR) * clampedProgress;
        float y1 = base1YR + (base1YD - base1YR) * clampedProgress;
        float x2 = base2XR + (base2XD - base2XR) * clampedProgress;
        float y2 = base2YR + (base2YD - base2YR) * clampedProgress;
        float x3 = tipXR + (tipXD - tipXR) * clampedProgress;
        float y3 = tipYR + (tipYD - tipYR) * clampedProgress;

        addVertex(x1, y1, abgr);
        addVertex(x2, y2, abgr);
        addVertex(x3, y3, abgr);
    }

    public void addTriangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
        buffer.tryMap();

        int abgr = ARGB.toABGR(color.getRGB());
        addVertex(x1, y1, abgr);
        addVertex(x2, y2, abgr);
        addVertex(x3, y3, abgr);
    }

    private void addVertex(float vx, float vy, int color) {
        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;

        MemoryUtil.memPutFloat(p, vx);
        MemoryUtil.memPutFloat(p + 4, vy);
        MemoryUtil.memPutFloat(p + 8, 0.0f);
        MemoryUtil.memPutInt(p + 12, color);

        currentOffset += STRIDE;
        vertexCount++;
    }

    public void setScissor(int x, int y, int width, int height) {
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
        rhi.drawVertices("Triangle Draw", rhi.pipelines().triangle, buffer.getGpuBuffer(), vertexCount, scissor);
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
        clear();
        buffer.close();
    }

}
