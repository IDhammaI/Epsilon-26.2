package com.github.epsilon.graphics.rhi;

import com.github.slmpc.prismrhi.command.RhiPrimitiveTopology;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayout;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayoutCreateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorStage;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorType;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiColorBlendAttachmentState;
import com.github.slmpc.prismrhi.pipeline.RhiCullMode;
import com.github.slmpc.prismrhi.pipeline.RhiDynamicRenderingState;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipelineCreateInfo;
import com.github.slmpc.prismrhi.pipeline.RhiRasterizationState;
import com.github.slmpc.prismrhi.pipeline.RhiFrontFace;
import com.github.slmpc.prismrhi.pipeline.RhiPolygonMode;
import com.github.slmpc.prismrhi.shader.RhiShaderModule;
import com.github.slmpc.prismrhi.shader.RhiShaderModuleCreateInfo;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class LuminRhiPipelines implements AutoCloseable {

    private static final RhiDynamicRenderingState GUI_RENDERING = RhiDynamicRenderingState.builder()
            .color(RhiFormat.RGBA8_UNORM)
            .depth(RhiFormat.D32_FLOAT)
            .build();
    private static final RhiRasterizationState NO_CULL = new RhiRasterizationState(
            RhiPolygonMode.FILL,
            RhiCullMode.NONE,
            RhiFrontFace.COUNTER_CLOCKWISE,
            1.0f
    );

    private final RhiDevice device;
    final RhiDescriptorSetLayout transformLayout;
    final RhiDescriptorSetLayout textureLayout;
    final RhiDescriptorSetLayout postUniformLayout;

    public final RhiGraphicsPipeline rectangle;
    public final RhiGraphicsPipeline roundRect;
    public final RhiGraphicsPipeline roundRectOutline;
    public final RhiGraphicsPipeline shadow;
    public final RhiGraphicsPipeline texture;
    public final RhiGraphicsPipeline ttfAa;
    public final RhiGraphicsPipeline ttfNoAa;
    public final RhiGraphicsPipeline triangle;
    public final RhiGraphicsPipeline blur;
    public final RhiGraphicsPipeline fxaa;
    public final RhiGraphicsPipeline filter;

    private final Map<String, RhiGraphicsPipeline> sandboxPipelines = new HashMap<>();

    private LuminRhiPipelines(RhiDevice device) {
        this.device = device;
        this.transformLayout = device.createDescriptorSetLayout(
                RhiDescriptorSetLayoutCreateInfo.builder()
                        .binding(0, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.VERTEX)
                        .build()
        );
        this.textureLayout = device.createDescriptorSetLayout(
                RhiDescriptorSetLayoutCreateInfo.builder()
                        .binding(0, RhiDescriptorType.COMBINED_IMAGE_SAMPLER, 1, RhiDescriptorStage.FRAGMENT)
                        .build()
        );
        this.postUniformLayout = device.createDescriptorSetLayout(
                RhiDescriptorSetLayoutCreateInfo.builder()
                        .binding(0, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.FRAGMENT)
                        .build()
        );

        this.rectangle = createPipeline("position_color.vsh", "rectangle.fsh", 16)
                .vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                .vertexAttribute(1, 0, RhiFormat.RGBA8_UNORM, 12)
                .buildPipeline();
        this.triangle = createPipeline("triangle.vsh", "triangle.fsh", 16)
                .topology(RhiPrimitiveTopology.TRIANGLE_LIST)
                .vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                .vertexAttribute(1, 0, RhiFormat.RGBA8_UNORM, 12)
                .buildPipeline();
        this.roundRect = createPipeline("round_rectangle.vsh", "round_rectangle.fsh", 48)
                .vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                .vertexAttribute(1, 0, RhiFormat.RGBA8_UNORM, 12)
                .vertexAttribute(2, 0, RhiFormat.RGBA32_FLOAT, 16)
                .vertexAttribute(3, 0, RhiFormat.RGBA32_FLOAT, 32)
                .buildPipeline();
        this.shadow = createPipeline("shadow.vsh", "shadow.fsh", 48)
                .vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                .vertexAttribute(1, 0, RhiFormat.RGBA8_UNORM, 12)
                .vertexAttribute(2, 0, RhiFormat.RGBA32_FLOAT, 16)
                .vertexAttribute(3, 0, RhiFormat.RGBA32_FLOAT, 32)
                .buildPipeline();
        this.roundRectOutline = createPipeline("round_rectangle_outline.vsh", "round_rectangle_outline.fsh", 52)
                .vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                .vertexAttribute(1, 0, RhiFormat.RGBA8_UNORM, 12)
                .vertexAttribute(2, 0, RhiFormat.RGBA32_FLOAT, 16)
                .vertexAttribute(3, 0, RhiFormat.RGBA32_FLOAT, 32)
                .vertexAttribute(4, 0, RhiFormat.R32_FLOAT, 48)
                .buildPipeline();
        this.texture = createPipeline("texture.vsh", "texture.fsh", 56)
                .texture()
                .vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                .vertexAttribute(1, 0, RhiFormat.RGBA8_UNORM, 12)
                .vertexAttribute(2, 0, RhiFormat.RG32_FLOAT, 16)
                .vertexAttribute(3, 0, RhiFormat.RGBA32_FLOAT, 24)
                .vertexAttribute(4, 0, RhiFormat.RGBA32_FLOAT, 40)
                .buildPipeline();
        this.ttfAa = createPipeline("ttf_font.vsh", "ttf_font_aa.fsh", 24)
                .texture()
                .vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                .vertexAttribute(1, 0, RhiFormat.RG32_FLOAT, 12)
                .vertexAttribute(2, 0, RhiFormat.RGBA8_UNORM, 20)
                .buildPipeline();
        this.ttfNoAa = createPipeline("ttf_font.vsh", "ttf_font_no_aa.fsh", 24)
                .texture()
                .vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                .vertexAttribute(1, 0, RhiFormat.RG32_FLOAT, 12)
                .vertexAttribute(2, 0, RhiFormat.RGBA8_UNORM, 20)
                .buildPipeline();
        this.blur = createPostPipeline("blur.vsh", "blur.fsh", true, RhiColorBlendAttachmentState.alphaBlend());
        this.fxaa = createPostPipeline("fullscreen.vsh", "fxaa.fsh", true, RhiColorBlendAttachmentState.opaque());
        this.filter = createPostPipeline("fullscreen.vsh", "filter.fsh", true, RhiColorBlendAttachmentState.opaque());
    }

    static LuminRhiPipelines create(RhiDevice device) {
        return new LuminRhiPipelines(device);
    }

    private PipelineBuilder createPipeline(String vertexPath, String fragmentPath, int stride) {
        RhiShaderModule vertexShader = device.createShaderModule(
                RhiShaderModuleCreateInfo.glsl(RhiShaderStage.VERTEX, loadShaderSource(vertexPath))
        );
        RhiShaderModule fragmentShader = device.createShaderModule(
                RhiShaderModuleCreateInfo.glsl(RhiShaderStage.FRAGMENT, loadShaderSource(fragmentPath))
        );
        return new PipelineBuilder(vertexShader, fragmentShader, stride);
    }

    private RhiGraphicsPipeline createPostPipeline(
            String vertexPath,
            String fragmentPath,
            boolean texture,
            RhiColorBlendAttachmentState blend
    ) {
        RhiShaderModule vertexShader = device.createShaderModule(
                RhiShaderModuleCreateInfo.glsl(RhiShaderStage.VERTEX, loadShaderSource(vertexPath))
        );
        RhiShaderModule fragmentShader = device.createShaderModule(
                RhiShaderModuleCreateInfo.glsl(RhiShaderStage.FRAGMENT, loadShaderSource(fragmentPath))
        );
        try {
            RhiGraphicsPipelineCreateInfo.Builder builder = RhiGraphicsPipelineCreateInfo.builder()
                    .shader(RhiShaderStage.VERTEX, vertexShader)
                    .shader(RhiShaderStage.FRAGMENT, fragmentShader)
                    .descriptorSetLayout(postUniformLayout)
                    .rendering(GUI_RENDERING)
                    .rasterization(NO_CULL)
                    .blend(blend);
            if (texture) {
                builder.descriptorSetLayout(textureLayout);
            }
            return device.createGraphicsPipeline(builder.build());
        } finally {
            vertexShader.close();
            fragmentShader.close();
        }
    }

    public RhiGraphicsPipeline sandbox(String fragmentPath) {
        return sandboxPipelines.computeIfAbsent(
                fragmentPath,
                path -> createPostPipeline("fullscreen.vsh", path, false, RhiColorBlendAttachmentState.opaque())
        );
    }

    private String loadShaderSource(String fileName) {
        String path = "assets/epsilon/shaders/rhi/" + fileName;
        try (var stream = LuminRhiPipelines.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing PrismRHI shader: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read PrismRHI shader: " + path, exception);
        }
    }

    @Override
    public void close() {
        rectangle.close();
        roundRect.close();
        roundRectOutline.close();
        shadow.close();
        texture.close();
        ttfAa.close();
        ttfNoAa.close();
        triangle.close();
        blur.close();
        fxaa.close();
        filter.close();
        sandboxPipelines.values().forEach(RhiGraphicsPipeline::close);
        sandboxPipelines.clear();
        postUniformLayout.close();
        textureLayout.close();
        transformLayout.close();
    }

    final class PipelineBuilder {
        private final RhiShaderModule vertexShader;
        private final RhiShaderModule fragmentShader;
        private final RhiGraphicsPipelineCreateInfo.Builder builder;

        private PipelineBuilder(RhiShaderModule vertexShader, RhiShaderModule fragmentShader, int stride) {
            this.vertexShader = vertexShader;
            this.fragmentShader = fragmentShader;
            this.builder = RhiGraphicsPipelineCreateInfo.builder()
                    .shader(RhiShaderStage.VERTEX, vertexShader)
                    .shader(RhiShaderStage.FRAGMENT, fragmentShader)
                    .descriptorSetLayout(transformLayout)
                    .rendering(GUI_RENDERING)
                    .rasterization(NO_CULL)
                    .blend(RhiColorBlendAttachmentState.alphaBlend())
                    .vertexBinding(0, stride);
        }

        PipelineBuilder texture() {
            builder.descriptorSetLayout(textureLayout);
            return this;
        }

        PipelineBuilder topology(RhiPrimitiveTopology topology) {
            builder.topology(topology);
            return this;
        }

        PipelineBuilder vertexAttribute(int location, int binding, RhiFormat format, int offset) {
            builder.vertexAttribute(location, binding, format, offset);
            return this;
        }

        RhiGraphicsPipeline buildPipeline() {
            try {
                return device.createGraphicsPipeline(builder.build());
            } finally {
                vertexShader.close();
                fragmentShader.close();
            }
        }
    }
}
