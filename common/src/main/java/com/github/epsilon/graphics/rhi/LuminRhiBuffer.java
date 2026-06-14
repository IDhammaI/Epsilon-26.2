package com.github.epsilon.graphics.rhi;

import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class LuminRhiBuffer implements AutoCloseable {

    private static final int BUFFER_COUNT = 3;

    private final RhiBuffer[] buffers = new RhiBuffer[BUFFER_COUNT];
    private final ByteBuffer mappedBuffer;
    private final long size;
    private int current;

    public LuminRhiBuffer(long size, RhiBufferUsage usage) {
        this.size = size;
        this.mappedBuffer = MemoryUtil.memAlloc(Math.toIntExact(size));
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = LuminRhi.get().device().createBuffer(
                    RhiBufferCreateInfo.builder(size)
                            .usage(usage)
                            .usage(RhiBufferUsage.TRANSFER_DST)
                            .memoryUsage(RhiMemoryUsage.CPU_TO_GPU)
                            .build()
            );
        }
    }

    public ByteBuffer getMappedBuffer() {
        return mappedBuffer;
    }

    public void tryMap() {
    }

    public RhiBuffer getGpuBuffer() {
        return buffers[current];
    }

    public void upload(long bytes) {
        if (bytes <= 0) {
            return;
        }
        ByteBuffer upload = mappedBuffer.duplicate();
        upload.position(0);
        upload.limit(Math.toIntExact(bytes));
        getGpuBuffer().write(0, upload);
    }

    public void rotate() {
        current = (current + 1) % buffers.length;
    }

    public long size() {
        return size;
    }

    @Override
    public void close() {
        for (RhiBuffer buffer : buffers) {
            buffer.close();
        }
        MemoryUtil.memFree(mappedBuffer);
    }
}
