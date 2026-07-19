/*
 * MCDLSSG - Runtime library download screen (vanilla style)
 */

package com.dgtdi.mcdlssg.common.gui;

import com.dgtdi.mcdlssg.common.download.RuntimeLibraryDownloader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class DevDownloadScreen extends Screen implements RuntimeLibraryDownloader.ProgressListener {
    private final Screen parent;
    private final RuntimeLibraryDownloader.Feature feature;
    private final Runnable onDone;
    private volatile String currentFile = "";
    private volatile long downloadedBytes;
    private volatile long totalBytes;
    private volatile String status = "准备下载...";
    private volatile boolean finished;
    private volatile boolean failed;
    private long lastBytes;
    private long lastTime;
    private double speed;

    public DevDownloadScreen(Screen parent, RuntimeLibraryDownloader.Feature feature, Runnable onDone) {
        super(Component.literal("下载运行库"));
        this.parent = parent;
        this.feature = feature;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 34, 200, 20)
                .build());
        status = "正在下载 " + feature.displayName + " ...";
        RuntimeLibraryDownloader.download(
                feature,
                this,
                () -> finished = true,
                throwable -> {
                    failed = true;
                    status = "下载失败: " + throwable.getMessage();
                }
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.drawCenteredString(this.font, this.title, centerX, centerY - 60, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal(feature.displayName), centerX, centerY - 42, 0xFFAAAAAA);
        guiGraphics.drawCenteredString(this.font, Component.literal(status), centerY > 0 ? centerX : centerX, centerY - 24, failed ? 0xFFFF5555 : 0xFFFFFFFF);

        int barWidth = 320;
        int barX = centerX - barWidth / 2;
        int barY = centerY - 6;
        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + 11, 0xFF000000);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + 10, 0xFF404040);
        if (totalBytes > 0) {
            int progressWidth = (int) (barWidth * Math.min(1.0, (double) downloadedBytes / totalBytes));
            guiGraphics.fill(barX, barY, barX + progressWidth, barY + 10, failed ? 0xFFAA3333 : 0xFF33AA55);
        }
        String progressText = currentFile + "  " + formatBytes(downloadedBytes) + " / " + formatBytes(totalBytes)
                + "  (" + formatBytes((long) speed) + "/s)";
        guiGraphics.drawCenteredString(this.font, Component.literal(progressText), centerX, centerY + 16, 0xFFCCCCCC);

        if (finished) {
            guiGraphics.drawCenteredString(this.font, Component.literal("下载完成！"), centerX, centerY + 34, 0xFF55FF55);
        }
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (lastTime != 0) {
            long dt = now - lastTime;
            if (dt >= 500) {
                speed = (downloadedBytes - lastBytes) * 1000.0 / dt;
                lastBytes = downloadedBytes;
                lastTime = now;
            }
        } else {
            lastTime = now;
        }
        if (finished) {
            onDone.run();
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }
    }

    @Override
    public void onProgress(String fileName, long downloaded, long total) {
        currentFile = fileName;
        downloadedBytes = downloaded;
        totalBytes = total;
    }

    @Override
    public void onExtract(String entryName) {
        currentFile = "解压: " + entryName;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1 << 20) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        }
        return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
    }
}
