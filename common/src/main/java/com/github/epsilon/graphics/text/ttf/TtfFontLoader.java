package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.Constants;
import com.github.epsilon.graphics.text.GlyphDescriptor;
import com.github.epsilon.graphics.text.IFontLoader;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBTruetype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class TtfFontLoader implements IFontLoader {

    private static final AtomicInteger WORKER_ID = new AtomicInteger();
    private static final ExecutorService GLYPH_WORKER = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Epsilon TTF Glyph Worker " + WORKER_ID.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    public final TtfFontFile fontFile;

    private final HashMap<Integer, GlyphDescriptor> glyphMap = new HashMap<>();
    private final HashMap<Integer, CompletableFuture<TtfGlyph>> pendingGlyphs = new HashMap<>();
    private final Set<Integer> missingGlyphs = new HashSet<>();
    private final List<TtfGlyphAtlas> atlases = new ArrayList<>();

    private TtfGlyphAtlas currentAtlas;
    private int atlasId = 0;

    public TtfFontLoader(Identifier ttfFile) {
        this.fontFile = new TtfFontFile(ttfFile, 64, 6);
    }

    @Override
    public void checkAndLoadChar(char ch) {
        checkAndLoadCodepoint(ch);
    }

    public void checkAndLoadCodepoint(int codepoint) {
        if (glyphMap.containsKey(codepoint) || missingGlyphs.contains(codepoint)) return;

        CompletableFuture<TtfGlyph> pending = pendingGlyphs.remove(codepoint);
        TtfGlyph glyph;
        try {
            glyph = pending != null ? pending.join() : fontFile.generateGlyph(codepoint);
        } catch (RuntimeException ignored) {
            glyph = fontFile.generateGlyph(codepoint);
        }
        appendGlyph(codepoint, glyph);
    }

    public void requestChars(String chars) {
        drainReadyGlyphs();

        for (int i = 0; i < chars.length(); ) {
            int codepoint = chars.codePointAt(i);
            i += Character.charCount(codepoint);
            if (codepoint == ' ' || codepoint == '\n'
                    || glyphMap.containsKey(codepoint)
                    || missingGlyphs.contains(codepoint)
                    || pendingGlyphs.containsKey(codepoint)) {
                continue;
            }

            pendingGlyphs.put(codepoint, CompletableFuture.supplyAsync(() -> fontFile.generateGlyph(codepoint), GLYPH_WORKER));
        }
    }

    public void drainReadyGlyphs() {
        if (pendingGlyphs.isEmpty()) {
            return;
        }

        List<Integer> readyCodepoints = null;
        for (Map.Entry<Integer, CompletableFuture<TtfGlyph>> entry : pendingGlyphs.entrySet()) {
            if (entry.getValue().isDone()) {
                if (readyCodepoints == null) {
                    readyCodepoints = new ArrayList<>();
                }
                readyCodepoints.add(entry.getKey());
            }
        }

        if (readyCodepoints == null) {
            return;
        }

        for (int codepoint : readyCodepoints) {
            CompletableFuture<TtfGlyph> future = pendingGlyphs.remove(codepoint);
            if (future != null && !future.isCompletedExceptionally() && !future.isCancelled()) {
                appendGlyph(codepoint, future.join());
            }
        }
    }

    private void appendGlyph(int codepoint, TtfGlyph glyph) {
        if (glyph == null || glyph.glyphData() == null) {
            missingGlyphs.add(codepoint);
            return;
        }

        if (currentAtlas == null) {
            createNewAtlas();
        }

        TtfGlyphAtlas.GlyphUV uv = null;
        for (int attempt = 0; attempt < 2 && uv == null; attempt++) {
            uv = currentAtlas.appendGlyph(glyph);
            if (uv == null && attempt == 0) {
                createNewAtlas();
            }
        }

        if (uv == null) {
            Constants.LOGGER.warn("Failed to place TTF glyph U+{} ({}x{}) into atlas", String.format("%04X", codepoint), glyph.width(), glyph.height());
            missingGlyphs.add(codepoint);
        } else {
            glyphMap.put(codepoint, new GlyphDescriptor(
                    currentAtlas, uv,
                    glyph.width(), glyph.height(),
                    glyph.xOffset(), glyph.yOffset(),
                    glyph.advance()
            ));
        }

        freeGlyph(glyph);
    }

    private void createNewAtlas() {
        currentAtlas = new TtfGlyphAtlas(atlasId);
        atlases.add(currentAtlas);
        atlasId++;
    }

    @Override
    public void checkAndLoadChars(String chars) {
        for (int i = 0; i < chars.length(); ) {
            int codepoint = chars.codePointAt(i);
            i += Character.charCount(codepoint);
            checkAndLoadCodepoint(codepoint);
        }
    }


    @Override
    public void destroy() {
        fontFile.destroy();
        for (TtfGlyphAtlas atlas : atlases) {
            atlas.destroy();
        }
        atlases.clear();
        glyphMap.clear();
        missingGlyphs.clear();
        for (CompletableFuture<TtfGlyph> future : pendingGlyphs.values()) {
            if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                freeGlyph(future.join());
            } else {
                future.cancel(false);
            }
        }
        pendingGlyphs.clear();
    }

    private void freeGlyph(TtfGlyph glyph) {
        if (glyph != null && glyph.glyphData() != null) {
            STBTruetype.stbtt_FreeSDF(glyph.glyphData());
        }
    }

    @Override
    public GlyphDescriptor getGlyph(char ch) {
        return glyphMap.get(ch);
    }

    public GlyphDescriptor getGlyph(int codepoint) {
        return glyphMap.get(codepoint);
    }
}
