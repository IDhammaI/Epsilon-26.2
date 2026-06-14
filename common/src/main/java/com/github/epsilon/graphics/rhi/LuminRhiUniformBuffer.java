package com.github.epsilon.graphics.rhi;

import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class LuminRhiUniformBuffer implements AutoCloseable {

    private final RhiBuffer buffer;
    private final ByteBuffer data;

    public LuminRhiUniformBuffer(int size) {
        this.data = MemoryUtil.memAlloc(size);
        this.buffer = LuminRhi.get().device().createBuffer(
                RhiBufferCreateInfo.builder(size)
                        .usage(RhiBufferUsage.UNIFORM_BUFFER)
                        .usage(RhiBufferUsage.TRANSFER_DST)
                        .memoryUsage(RhiMemoryUsage.CPU_TO_GPU)
                        .build()
        );
    }

    public LuminRhiUniformBuffer clear() {
        data.clear();
        return this;
    }

    public LuminRhiUniformBuffer putVec3(float x, float y, float z) {
        align(16);
        data.putFloat(x);
        data.putFloat(y);
        data.putFloat(z);
        data.position(data.position() + Float.BYTES);
        return this;
    }

    public LuminRhiUniformBuffer putVec4(float x, float y, float z, float w) {
        align(16);
        data.putFloat(x);
        data.putFloat(y);
        data.putFloat(z);
        data.putFloat(w);
        return this;
    }

    public void upload() {
        ByteBuffer upload = data.duplicate();
        upload.flip();
        buffer.write(0, upload);
    }

    public RhiBuffer buffer() {
        return buffer;
    }

    private void align(int alignment) {
        int position = data.position();
        int aligned = (position + alignment - 1) / alignment * alignment;
        data.position(aligned);
    }

    @Override
    public void close() {
        buffer.close();
        MemoryUtil.memFree(data);
    }
}
