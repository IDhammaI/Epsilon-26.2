package com.github.epsilon.graphics.rhi;

import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageAspect;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiSampler;

import java.util.Set;

final class LuminRhiTextureAdapters {

    private LuminRhiTextureAdapters() {
    }

    static RhiImage image(BackendApi api, int nativeHandle, int width, int height, RhiFormat format) {
        return new WrappedImage(api, nativeHandle, width, height, format);
    }

    static RhiImageView view(RhiImage image, RhiFormat format) {
        return new WrappedImageView(image, format);
    }

    static RhiSampler sampler(BackendApi api, int nativeHandle) {
        return new WrappedSampler(api, nativeHandle);
    }

    private record WrappedImage(
            BackendApi api,
            int handle,
            int width,
            int height,
            RhiFormat format
    ) implements RhiImage {
        @Override
        public RhiExtent3D extent() {
            return RhiExtent3D.of2D(width, height);
        }

        @Override
        public long nativeHandle() {
            return handle;
        }

        @Override
        public void close() {
        }
    }

    private record WrappedImageView(
            RhiImage image,
            RhiFormat format
    ) implements RhiImageView {
        @Override
        public RhiImage image() {
            return image;
        }

        @Override
        public RhiFormat format() {
            return format;
        }

        @Override
        public BackendApi api() {
            return image.api();
        }

        @Override
        public long nativeHandle() {
            return image.nativeHandle();
        }

        @Override
        public Set<RhiImageAspect> aspects() {
            return format == RhiFormat.D32_FLOAT ? Set.of(RhiImageAspect.DEPTH) : Set.of(RhiImageAspect.COLOR);
        }

        @Override
        public void close() {
        }
    }

    private record WrappedSampler(
            BackendApi api,
            int handle
    ) implements RhiSampler {
        @Override
        public long nativeHandle() {
            return handle;
        }

        @Override
        public void close() {
        }
    }
}
