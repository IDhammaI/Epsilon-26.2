package com.github.epsilon.graphics.rhi;

import com.github.epsilon.Constants;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.slmpc.prismrhi.PrismRHI;
import com.github.slmpc.prismrhi.RhiException;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.command.RhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.RhiCommandPool;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetAllocateInfo;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.device.RhiDeviceCreateInfo;
import com.github.slmpc.prismrhi.device.RhiPhysicalDevice;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.instance.RhiInstance;
import com.github.slmpc.prismrhi.instance.RhiInstanceCreateInfo;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.queue.RhiQueue;
import com.github.slmpc.prismrhi.queue.RhiQueueType;
import com.github.slmpc.prismrhi.queue.RhiSubmitInfo;
import com.github.slmpc.prismrhi.rendering.RhiRenderingAttachment;
import com.github.slmpc.prismrhi.rendering.RhiRenderingInfo;
import com.github.slmpc.prismrhi.rendering.RhiRect2D;
import com.github.slmpc.prismrhi.rendering.RhiViewport;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageUploadInfo;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiIndexType;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.state.WindowRenderState;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public final class LuminRhi implements AutoCloseable {

    private static final int TRANSFORM_BUFFER_SIZE = 64;
    private static final int INITIAL_QUAD_INDEX_COUNT = 1536;

    private static LuminRhi instance;

    private final BackendApi api;
    private final RhiInstance rhiInstance;
    private final RhiDevice device;
    private final RhiQueue graphicsQueue;
    private final RhiCommandPool commandPool;
    private final RhiCommandBuffer commandBuffer;
    private final LuminRhiPipelines pipelines;
    private final RhiBuffer transformBuffer;
    private final RhiDescriptorSet transformSet;
    private RhiBuffer quadIndexBuffer;
    private int quadIndexCapacity;

    private LuminRhi(BackendApi api, RhiInstance rhiInstance, RhiDevice device) {
        this.api = api;
        this.rhiInstance = rhiInstance;
        this.device = device;
        this.graphicsQueue = device.queue(RhiQueueType.GRAPHICS);
        this.commandPool = device.createCommandPool(new RhiCommandPoolCreateInfo(RhiQueueType.GRAPHICS, true, true));
        this.commandBuffer = commandPool.allocateCommandBuffer(RhiCommandBufferLevel.PRIMARY);
        this.pipelines = LuminRhiPipelines.create(device);
        this.transformBuffer = device.createBuffer(
                RhiBufferCreateInfo.builder(TRANSFORM_BUFFER_SIZE)
                        .usage(RhiBufferUsage.UNIFORM_BUFFER)
                        .usage(RhiBufferUsage.TRANSFER_DST)
                        .memoryUsage(RhiMemoryUsage.CPU_TO_GPU)
                        .build()
        );
        this.transformSet = device.allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(pipelines.transformLayout));
        this.transformSet.update(writer -> writer.uniformBuffer(0, transformBuffer));
        ensureQuadIndexBuffer(INITIAL_QUAD_INDEX_COUNT);
    }

    public static LuminRhi get() {
        RenderSystem.assertOnRenderThread();
        if (instance == null) {
            instance = create();
        }
        return instance;
    }

    public static void destroy() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    private static LuminRhi create() {
        RuntimeException firstFailure = null;
        for (BackendApi candidate : List.of(BackendApi.OPENGL_DSA, BackendApi.OPENGL_41)) {
            try {
                RhiInstance rhiInstance = PrismRHI.createInstance(
                        RhiInstanceCreateInfo.builder(candidate)
                                .applicationName(Constants.NAME)
                                .build()
                );
                RhiPhysicalDevice physicalDevice = rhiInstance.enumeratePhysicalDevices().getFirst();
                RhiDevice device = rhiInstance.createDevice(
                        physicalDevice,
                        RhiDeviceCreateInfo.builder()
                                .debugName("Lumin Graphics")
                                .queue(RhiQueueType.GRAPHICS, 1)
                                .glStateBridge(MinecraftGlStateBridge.INSTANCE)
                                .build()
                );
                Constants.LOGGER.info("Initialized Lumin Graphics PrismRHI backend: {}", rhiInstance.backendInfo().displayName());
                return new LuminRhi(candidate, rhiInstance, device);
            } catch (RhiException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                }
                Constants.LOGGER.debug("PrismRHI backend {} is unavailable for Lumin Graphics", candidate, exception);
            }
        }
        throw new IllegalStateException("Unable to initialize a PrismRHI OpenGL backend for Lumin Graphics", firstFailure);
    }

    public RhiDevice device() {
        return device;
    }

    public LuminRhiPipelines pipelines() {
        return pipelines;
    }

    public void drawQuads(String label, RhiGraphicsPipeline pipeline, RhiBuffer vertexBuffer, int vertexCount, @Nullable LuminRenderSystem.ScissorRect scissor) {
        if (vertexCount == 0) {
            return;
        }
        int indexCount = vertexCount / 4 * 6;
        ensureQuadIndexBuffer(indexCount);
        draw(label, pipeline, vertexBuffer, vertexCount, indexCount, quadIndexBuffer, scissor, null);
    }

    public void drawTexturedQuads(
            String label,
            RhiGraphicsPipeline pipeline,
            RhiBuffer vertexBuffer,
            int vertexCount,
            @Nullable LuminRenderSystem.ScissorRect scissor,
            GpuTextureView textureView,
            GpuSampler sampler
    ) {
        if (vertexCount == 0) {
            return;
        }
        int indexCount = vertexCount / 4 * 6;
        ensureQuadIndexBuffer(indexCount);
        TextureBinding textureBinding = textureBinding(textureView, sampler);
        draw(label, pipeline, vertexBuffer, vertexCount, indexCount, quadIndexBuffer, scissor, textureBinding);
    }

    public void drawTexturedQuads(
            String label,
            RhiGraphicsPipeline pipeline,
            RhiBuffer vertexBuffer,
            int vertexCount,
            @Nullable LuminRenderSystem.ScissorRect scissor,
            RhiImageView textureView,
            RhiSampler sampler
    ) {
        if (vertexCount == 0) {
            return;
        }
        int indexCount = vertexCount / 4 * 6;
        ensureQuadIndexBuffer(indexCount);
        draw(label, pipeline, vertexBuffer, vertexCount, indexCount, quadIndexBuffer, scissor, new TextureBinding(textureView, sampler));
    }

    public void uploadImageRegion(RhiImage destination, int x, int y, int width, int height, ByteBuffer source) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int uploadSize = Math.multiplyExact(Math.multiplyExact(width, height), destination.format().bytesPerPixel());
        ByteBuffer uploadData = source.duplicate();
        uploadData.limit(uploadData.position() + uploadSize);
        RhiBuffer stagingBuffer = device.createBuffer(
                RhiBufferCreateInfo.builder(uploadSize)
                        .usage(RhiBufferUsage.TRANSFER_SRC)
                        .memoryUsage(RhiMemoryUsage.CPU_TO_GPU)
                        .build()
        );
        try {
            stagingBuffer.write(uploadData);
            commandBuffer.begin();
            commandBuffer.copyBufferToImage(
                    stagingBuffer,
                    destination,
                    RhiImageUploadInfo.builder(width, height)
                            .offset(x, y)
                            .build()
            );
            commandBuffer.end();
            graphicsQueue.submit(RhiSubmitInfo.of(commandBuffer));
        } finally {
            stagingBuffer.close();
        }
    }

    public void drawVertices(String label, RhiGraphicsPipeline pipeline, RhiBuffer vertexBuffer, int vertexCount, @Nullable LuminRenderSystem.ScissorRect scissor) {
        if (vertexCount == 0) {
            return;
        }
        draw(label, pipeline, vertexBuffer, vertexCount, 0, null, scissor, null);
    }

    public void drawFullscreen(
            String label,
            RhiGraphicsPipeline pipeline,
            RhiBuffer uniformBuffer,
            int vertexCount,
            @Nullable LuminRenderSystem.ScissorRect scissor
    ) {
        drawFullscreen(label, pipeline, uniformBuffer, vertexCount, scissor, null);
    }

    public void drawFullscreenTextured(
            String label,
            RhiGraphicsPipeline pipeline,
            RhiBuffer uniformBuffer,
            int vertexCount,
            @Nullable LuminRenderSystem.ScissorRect scissor,
            GpuTextureView textureView,
            GpuSampler sampler
    ) {
        drawFullscreen(label, pipeline, uniformBuffer, vertexCount, scissor, textureBinding(textureView, sampler));
    }

    public void drawFullscreenTexturedTo(
            String label,
            RhiGraphicsPipeline pipeline,
            RhiBuffer uniformBuffer,
            int vertexCount,
            @Nullable LuminRenderSystem.ScissorRect scissor,
            GpuTextureView outputColorView,
            @Nullable GpuTextureView outputDepthView,
            int outputWidth,
            int outputHeight,
            GpuTextureView textureView,
            GpuSampler sampler
    ) {
        drawFullscreen(
                label,
                pipeline,
                uniformBuffer,
                vertexCount,
                scissor,
                outputColorView,
                outputDepthView,
                outputWidth,
                outputHeight,
                textureBinding(textureView, sampler)
        );
    }

    private void drawFullscreen(
            String label,
            RhiGraphicsPipeline pipeline,
            RhiBuffer uniformBuffer,
            int vertexCount,
            @Nullable LuminRenderSystem.ScissorRect scissor,
            @Nullable TextureBinding textureBinding
    ) {
        GpuTextureView outputColorView = LuminRenderSystem.resolveColorView();
        if (outputColorView == null) {
            return;
        }
        drawFullscreen(
                label,
                pipeline,
                uniformBuffer,
                vertexCount,
                scissor,
                outputColorView,
                LuminRenderSystem.resolveDepthView(),
                targetWidth(),
                targetHeight(),
                textureBinding
        );
    }

    private void drawFullscreen(
            String label,
            RhiGraphicsPipeline pipeline,
            RhiBuffer uniformBuffer,
            int vertexCount,
            @Nullable LuminRenderSystem.ScissorRect scissor,
            GpuTextureView outputColorView,
            @Nullable GpuTextureView outputDepthView,
            int outputWidth,
            int outputHeight,
            @Nullable TextureBinding textureBinding
    ) {
        if (vertexCount == 0) {
            return;
        }
        if (scissor != null && (scissor.width() <= 0 || scissor.height() <= 0)) {
            return;
        }

        commandBuffer.begin();
        commandBuffer.beginRendering(renderingInfo(outputColorView, outputDepthView, outputWidth, outputHeight));
        commandBuffer.bindGraphicsPipeline(pipeline);
        commandBuffer.setViewport(RhiViewport.of(outputWidth, outputHeight));
        if (scissor != null && scissor.width() > 0 && scissor.height() > 0) {
            commandBuffer.setScissor(RhiRect2D.of(scissor.x(), scissor.y(), scissor.width(), scissor.height()));
        }
        RhiDescriptorSet uniformSet = device.allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(pipelines.postUniformLayout));
        uniformSet.update(writer -> writer.uniformBuffer(0, uniformBuffer));
        commandBuffer.bindDescriptorSet(pipeline, 0, uniformSet);
        RhiDescriptorSet textureSet = null;
        if (textureBinding != null) {
            textureSet = device.allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(pipelines.textureLayout));
            textureSet.update(writer -> writer.combinedImageSampler(0, 0, textureBinding.view(), textureBinding.sampler()));
            commandBuffer.bindDescriptorSet(pipeline, 1, textureSet);
        }
        commandBuffer.draw(vertexCount);
        commandBuffer.endRendering();
        commandBuffer.end();
        graphicsQueue.submit(RhiSubmitInfo.of(commandBuffer));
        if (textureSet != null) {
            textureSet.close();
        }
        uniformSet.close();
    }

    private void draw(
            String label,
            RhiGraphicsPipeline pipeline,
            RhiBuffer vertexBuffer,
            int vertexCount,
            int indexCount,
            @Nullable RhiBuffer indexBuffer,
            @Nullable LuminRenderSystem.ScissorRect scissor,
            @Nullable TextureBinding textureBinding
    ) {
        if (LuminRenderSystem.resolveColorView() == null) {
            return;
        }
        if (scissor != null && (scissor.width() <= 0 || scissor.height() <= 0)) {
            return;
        }
        writeTransform();

        commandBuffer.begin();
        commandBuffer.beginRendering(renderingInfo());
        commandBuffer.bindGraphicsPipeline(pipeline);
        commandBuffer.setViewport(RhiViewport.of(targetWidth(), targetHeight()));
        if (scissor != null && scissor.width() > 0 && scissor.height() > 0) {
            commandBuffer.setScissor(RhiRect2D.of(scissor.x(), scissor.y(), scissor.width(), scissor.height()));
        }
        commandBuffer.bindDescriptorSet(pipeline, 0, transformSet);
        RhiDescriptorSet textureSet = null;
        if (textureBinding != null) {
            textureSet = device.allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(pipelines.textureLayout));
            textureSet.update(writer -> writer.combinedImageSampler(0, 0, textureBinding.view(), textureBinding.sampler()));
            commandBuffer.bindDescriptorSet(pipeline, 1, textureSet);
        }
        commandBuffer.bindVertexBuffer(0, vertexBuffer, 0);
        if (indexBuffer != null) {
            commandBuffer.bindIndexBuffer(indexBuffer, 0, RhiIndexType.UINT32);
            commandBuffer.drawIndexed(indexCount);
        } else {
            commandBuffer.draw(vertexCount);
        }
        commandBuffer.endRendering();
        commandBuffer.end();
        graphicsQueue.submit(RhiSubmitInfo.of(commandBuffer));
        if (textureSet != null) {
            textureSet.close();
        }
    }

    private RhiRenderingInfo renderingInfo() {
        return renderingInfo(LuminRenderSystem.resolveColorView(), LuminRenderSystem.resolveDepthView(), targetWidth(), targetHeight());
    }

    private RhiRenderingInfo renderingInfo(
            GpuTextureView colorTextureView,
            @Nullable GpuTextureView depthTextureView,
            int width,
            int height
    ) {
        RhiImageView colorView = rhiView(colorTextureView, RhiFormat.RGBA8_UNORM);
        RhiRenderingInfo.Builder builder = RhiRenderingInfo.builder(RhiRect2D.of(width, height))
                .color(RhiRenderingAttachment.color(colorView));
        if (depthTextureView != null) {
            builder.depth(RhiRenderingAttachment.depth(rhiView(depthTextureView, RhiFormat.D32_FLOAT), null, 1.0f));
        }
        return builder.build();
    }

    private void writeTransform() {
        LuminRenderSystem.withOrthoProjection(() -> {
            Projection projection = LuminRenderSystem.guiOrthoProjection;
            Matrix4f matrix = projection.getMatrix(new Matrix4f());
            ByteBuffer data = MemoryUtil.memAlloc(TRANSFORM_BUFFER_SIZE);
            try {
                matrix.get(0, data);
                data.position(0);
                data.limit(TRANSFORM_BUFFER_SIZE);
                transformBuffer.write(0, data);
            } finally {
                MemoryUtil.memFree(data);
            }
        });
    }

    private TextureBinding textureBinding(GpuTextureView textureView, GpuSampler sampler) {
        return new TextureBinding(
                rhiView(textureView, toRhiFormat(textureView.texture().getFormat())),
                rhiSampler(sampler)
        );
    }

    public RhiImageView rhiView(GpuTextureView view, RhiFormat format) {
        GpuTexture texture = view.texture();
        if (!(texture instanceof GlTexture glTexture)) {
            throw new IllegalStateException("Lumin PrismRHI OpenGL backend requires GlTexture-backed Minecraft textures");
        }
        RhiImage image = LuminRhiTextureAdapters.image(
                api,
                glTexture.glId(),
                view.getWidth(0),
                view.getHeight(0),
                format
        );
        return LuminRhiTextureAdapters.view(image, format);
    }

    public RhiSampler rhiSampler(GpuSampler sampler) {
        if (!(sampler instanceof GlSampler glSampler)) {
            throw new IllegalStateException("Lumin PrismRHI OpenGL backend requires GlSampler-backed Minecraft samplers");
        }
        return LuminRhiTextureAdapters.sampler(api, glSampler.getId());
    }

    public static RhiFormat toRhiFormat(TextureFormat format) {
        return switch (format) {
            case RGBA8 -> RhiFormat.RGBA8_UNORM;
            case RED8, RED8I -> RhiFormat.R8_UNORM;
            case DEPTH32 -> RhiFormat.D32_FLOAT;
            default -> RhiFormat.RGBA8_UNORM;
        };
    }

    public int targetWidth() {
        LuminRenderSystem.LuminRenderTarget activeTarget = LuminRenderSystem.getActiveTarget();
        if (activeTarget != null) {
            return activeTarget.width();
        }
        WindowRenderState windowState = mc.gameRenderer.getGameRenderState().windowRenderState;
        return windowState.width;
    }

    public int targetHeight() {
        LuminRenderSystem.LuminRenderTarget activeTarget = LuminRenderSystem.getActiveTarget();
        if (activeTarget != null) {
            return activeTarget.height();
        }
        WindowRenderState windowState = mc.gameRenderer.getGameRenderState().windowRenderState;
        return windowState.height;
    }

    private void ensureQuadIndexBuffer(int requiredIndexCount) {
        if (quadIndexBuffer != null && quadIndexCapacity >= requiredIndexCount) {
            return;
        }
        int quadCount = Math.max(1, (Math.max(requiredIndexCount, INITIAL_QUAD_INDEX_COUNT) + 5) / 6);
        quadCount = Integer.highestOneBit(quadCount - 1) << 1;
        int indexCount = quadCount * 6;
        ByteBuffer indices = MemoryUtil.memAlloc(indexCount * Integer.BYTES);
        try {
            int vertices = 0;
            for (int i = 0; i < indexCount; i += 6) {
                indices.putInt(vertices);
                indices.putInt(vertices + 1);
                indices.putInt(vertices + 2);
                indices.putInt(vertices + 2);
                indices.putInt(vertices + 3);
                indices.putInt(vertices);
                vertices += 4;
            }
            indices.flip();
            if (quadIndexBuffer != null) {
                quadIndexBuffer.close();
            }
            quadIndexBuffer = device.createBuffer(
                    RhiBufferCreateInfo.builder(indices.remaining())
                            .usage(RhiBufferUsage.INDEX_BUFFER)
                            .usage(RhiBufferUsage.TRANSFER_DST)
                            .memoryUsage(RhiMemoryUsage.CPU_TO_GPU)
                            .build()
            );
            quadIndexBuffer.write(indices);
            quadIndexCapacity = indexCount;
        } finally {
            MemoryUtil.memFree(indices);
        }
    }

    @Override
    public void close() {
        device.waitIdle();
        if (quadIndexBuffer != null) {
            quadIndexBuffer.close();
            quadIndexBuffer = null;
        }
        transformSet.close();
        transformBuffer.close();
        pipelines.close();
        commandBuffer.close();
        commandPool.close();
        device.close();
        rhiInstance.close();
    }

    private record TextureBinding(RhiImageView view, RhiSampler sampler) {
    }
}
