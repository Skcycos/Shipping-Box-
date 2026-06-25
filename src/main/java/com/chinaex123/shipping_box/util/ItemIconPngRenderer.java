package com.chinaex123.shipping_box.util;

import com.chinaex123.shipping_box.web.EditorIconCacheManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将 ItemStack 通过离屏 RenderTarget 渲染为 PNG 字节数组。
 */
public class ItemIconPngRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemIconPngRenderer.class);
    public static final int DEFAULT_SIZE = 32;

    private static RenderTarget renderTarget;
    private static int currentSize = 0;

    /**
     * 将给定的 ItemStack 渲染为指定尺寸的 PNG 字节数组。
     */
    public static byte[] renderStackToPng(ItemStack stack, int size) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }

        try {
            return renderInternal(stack, size, mc);
        } catch (Exception e) {
            LOGGER.warn("[IconRenderer] Failed to render icon for {}",
                    stack.getHoverName().getString(), e);
            return EditorIconCacheManager.createPlaceholderPng(size, stack.hashCode());
        }
    }

    /**
     * 释放当前缓存的 RenderTarget。
     */
    public static void disposeRenderTarget() {
        if (renderTarget != null) {
            renderTarget.destroyBuffers();
            renderTarget = null;
            currentSize = 0;
        }
    }

    // ==================== 内部实现 ====================

    /** 内部渲染尺寸固定为 16×16（物品 GUI 原生尺寸），之后用最近邻放大 */
    private static final int RENDER_SIZE = 16;

    private static byte[] renderInternal(ItemStack stack, int targetSize, Minecraft mc) {
        if (!RenderSystem.isOnRenderThread()) {
            LOGGER.warn("[IconRenderer] renderStackToPng called outside render thread");
            return EditorIconCacheManager.createPlaceholderPng(targetSize, stack.hashCode());
        }

        ensureRenderTarget(RENDER_SIZE, mc);

        RenderTarget mainTarget = mc.getMainRenderTarget();
        NativeImage raw16 = null;
        NativeImage finalImage = null;

        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            // 1. 绑定 16×16 离屏 RenderTarget
            renderTarget.bindWrite(true);

            // 2. 清屏：透明背景
            RenderSystem.clearColor(0f, 0f, 0f, 0f);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

            // 3. 16×16 正交投影
            Matrix4f projectionMatrix = new Matrix4f().setOrtho(
                    0.0F, RENDER_SIZE, RENDER_SIZE, 0.0F,
                    -10000.0F, 10000.0F
            );
            RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);

            // 4. 重置 model-view matrix
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            // 5. 基础渲染状态
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            GuiGraphics guiGraphics = new GuiGraphics(mc, buffers);

            // 在 16×16 画布原点渲染，不缩放
            Lighting.setupFor3DItems();
            guiGraphics.renderFakeItem(stack, 0, 0);
            guiGraphics.flush();
            Lighting.setupLevel();

            // 6. 读回 16×16 颜色纹理
            renderTarget.bindRead();
            raw16 = new NativeImage(NativeImage.Format.RGBA, RENDER_SIZE, RENDER_SIZE, false);
            raw16.downloadTexture(0, false);
            raw16.flipY();

            // 7. 最近邻放大到目标尺寸
            if (targetSize == RENDER_SIZE) {
                finalImage = raw16;
            } else {
                finalImage = upscaleNearest(raw16, targetSize);
            }

            byte[] png = encodePng(finalImage);
            if (png == null || png.length == 0) {
                return EditorIconCacheManager.createPlaceholderPng(targetSize, stack.hashCode());
            }
            return png;
        } finally {
            if (finalImage != null && finalImage != raw16) {
                finalImage.close();
            }
            if (raw16 != null) {
                raw16.close();
            }
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.disableBlend();
            mainTarget.bindWrite(true);
        }
    }

    /** 最近邻放大，保持像素锐利不模糊 */
    private static NativeImage upscaleNearest(NativeImage src, int targetSize) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        NativeImage dst = new NativeImage(NativeImage.Format.RGBA, targetSize, targetSize, false);

        for (int y = 0; y < targetSize; y++) {
            int srcY = y * srcH / targetSize;
            for (int x = 0; x < targetSize; x++) {
                int srcX = x * srcW / targetSize;
                dst.setPixelRGBA(x, y, src.getPixelRGBA(srcX, srcY));
            }
        }

        return dst;
    }

    private static void ensureRenderTarget(int size, Minecraft mc) {
        if (renderTarget != null && currentSize == size) {
            return;
        }
        disposeRenderTarget();

        // 必须带深度附件
        renderTarget = new RenderTarget(true) {};
        renderTarget.createBuffers(size, size, Minecraft.ON_OSX);
        currentSize = size;
    }

    private static byte[] encodePng(NativeImage image) {
        try {
            Path tempFile = Files.createTempFile("shipping_box_icon_", ".png");
            image.writeToFile(tempFile);
            byte[] result = Files.readAllBytes(tempFile);
            Files.deleteIfExists(tempFile);
            if (result.length > 0) {
                return result;
            }
        } catch (Exception e) {
            LOGGER.error("[IconRenderer] Failed to encode PNG", e);
        }
        return null;
    }
}
