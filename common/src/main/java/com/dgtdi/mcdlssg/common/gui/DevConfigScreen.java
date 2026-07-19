/*
 * MCDLSSG - Vanilla-style configuration screen
 */

package com.dgtdi.mcdlssg.common.gui;

import com.dgtdi.mcdlssg.api.registry.AlgorithmDescription;
import com.dgtdi.mcdlssg.api.registry.AlgorithmRegistry;
import com.dgtdi.mcdlssg.common.config.MCDLSSGConfig;
import com.dgtdi.mcdlssg.common.download.RuntimeLibraryDownloader;
import com.dgtdi.mcdlssg.common.upscale.AlgorithmDescriptions;
import com.dgtdi.mcdlssg.common.config.enums.CaptureMode;
import com.dgtdi.mcdlssg.common.config.enums.DLSSGMotionVectorMode;
import com.dgtdi.mcdlssg.common.config.enums.InternalTextureFormat;
import com.dgtdi.mcdlssg.common.config.enums.InteropSyncMode;
import com.dgtdi.mcdlssg.core.streamline.DLSSGRuntime;
import com.dgtdi.mcdlssg.core.streamline.Streamline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DevConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int COLUMN_WIDTH = 180;
    private static final int HEADER_GAP = 14;

    private final Screen parent;
    private final List<Section> sections = new ArrayList<>();

    public DevConfigScreen(Screen parent) {
        super(Component.literal("MCDLSSG 设置"));
        this.parent = parent;
    }

    private record Section(String title, int x, int startY, List<AbstractWidget> widgets) {
    }

    @Override
    protected void init() {
        sections.clear();
        int centerX = this.width / 2;
        int leftX = centerX - COLUMN_WIDTH - 5;
        int rightX = centerX + 5;

        List<AbstractWidget> upscale = new ArrayList<>();
        upscale.add(onOff("启用超分辨率", MCDLSSGConfig.isEnableUpscale(), MCDLSSGConfig::setEnableUpscale));
        List<AlgorithmDescription<?>> algorithms = new ArrayList<>(AlgorithmRegistry.getAlgorithmMap().values());
        upscale.add(CycleButton.builder((AlgorithmDescription<?> algo) -> Component.literal(algo.getBriefName()))
                .withValues(algorithms)
                .withInitialValue(MCDLSSGConfig.getUpscaleAlgorithm())
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.literal("算法"),
                        (button, value) -> selectAlgorithm(value)));
        upscale.add(new PercentSlider("倍率", MCDLSSGConfig.getUpscaleRatio(), 1.0f, 3.0f, MCDLSSGConfig::setUpscaleRatio));
        upscale.add(new PercentSlider("锐化", MCDLSSGConfig.getSharpness(), 0.0f, 1.0f, MCDLSSGConfig::setSharpness));
        upscale.add(CycleButton.builder((CaptureMode mode) -> Component.literal(mode.name()))
                .withValues(CaptureMode.values())
                .withInitialValue(MCDLSSGConfig.getCaptureMode())
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.literal("捕获模式"),
                        (button, value) -> MCDLSSGConfig.setCaptureMode(value)));
        upscale.add(onOff("原版画面禁用超分", MCDLSSGConfig.isDisableUpscaleOnVanilla(), MCDLSSGConfig::setDisableUpscaleOnVanilla));
        upscale.add(onOff("打开界面时暂停", MCDLSSGConfig.isPauseGameOnGui(), MCDLSSGConfig::setPauseGameOnGui));
        sections.add(new Section("超分辨率", leftX, 56, upscale));

        List<AbstractWidget> frameGen = new ArrayList<>();
        frameGen.add(onOff("启用 DLSS 帧生成", MCDLSSGConfig.isEnableDLSSFrameGeneration(), value -> {
            if (value && !RuntimeLibraryDownloader.isFeatureReady(RuntimeLibraryDownloader.Feature.DLSS_G)) {
                openDownloadScreen(RuntimeLibraryDownloader.Feature.DLSS_G,
                        () -> MCDLSSGConfig.setEnableDLSSFrameGeneration(true));
            } else {
                MCDLSSGConfig.setEnableDLSSFrameGeneration(value);
            }
        }));
        frameGen.add(CycleButton.builder((Integer mult) -> Component.literal("x" + mult))
                .withValues(2, 3, 4, 5, 6)
                .withInitialValue(Math.round(MCDLSSGConfig.DLSS_FRAME_GENERATION_MULTIPLIER.get()))
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.literal("生成倍率"),
                        (button, value) -> MCDLSSGConfig.setDLSSFrameGenerationMultiplier(value.floatValue())));
        frameGen.add(CycleButton.builder((DLSSGMotionVectorMode mode) -> Component.literal(mode.name()))
                .withValues(DLSSGMotionVectorMode.values())
                .withInitialValue(MCDLSSGConfig.getDLSSFrameGenerationMotionVectorMode())
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.literal("运动矢量"),
                        (button, value) -> MCDLSSGConfig.setDLSSFrameGenerationMotionVectorMode(value)));
        frameGen.add(onOff("矢量膨胀", MCDLSSGConfig.isDLSSFrameGenerationMotionVectorDilationEnabled(),
                value -> MCDLSSGConfig.DLSS_FRAME_GENERATION_MV_DILATION.set(value)));
        frameGen.add(onOff("矢量死区", MCDLSSGConfig.isDLSSFrameGenerationMotionVectorDeadzoneEnabled(),
                value -> MCDLSSGConfig.DLSS_FRAME_GENERATION_MV_DEADZONE.set(value)));
        frameGen.add(onOff("UI 重组", MCDLSSGConfig.isDLSSFrameGenerationUIRecompositionEnabled(), MCDLSSGConfig::setDLSSFrameGenerationUIRecompositionEnabled));
        frameGen.add(onOff("状态指示器", MCDLSSGConfig.isDLSSFrameGenerationIndicatorVisible(), MCDLSSGConfig::setDLSSFrameGenerationIndicatorVisible));
        sections.add(new Section("DLSS 帧生成", rightX, 56, frameGen));

        int advancedY = 56 + upscale.size() * (ROW_HEIGHT + ROW_GAP) + HEADER_GAP + ROW_GAP;
        List<AbstractWidget> advanced = new ArrayList<>();
        advanced.add(CycleButton.builder((InteropSyncMode mode) -> Component.literal(mode.name()))
                .withValues(InteropSyncMode.values())
                .withInitialValue(MCDLSSGConfig.getInteropSyncMode())
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.literal("互操作同步"),
                        (button, value) -> MCDLSSGConfig.setInteropSyncMode(value)));
        advanced.add(CycleButton.builder((InternalTextureFormat format) -> Component.literal(format.name()))
                .withValues(InternalTextureFormat.values())
                .withInitialValue(MCDLSSGConfig.INTERNAL_TEXTURE_FORMAT.get())
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.literal("内部纹理格式"),
                        (button, value) -> MCDLSSGConfig.setInternalTextureFormat(value)));
        advanced.add(onOff("跳过 Vulkan 初始化", MCDLSSGConfig.isSkipInitVulkan(), MCDLSSGConfig::setSkipInitVulkan));
        advanced.add(onOff("兼容着色器编译器", MCDLSSGConfig.isEnableCompatShaderCompiler(), MCDLSSGConfig::setEnableCompatShaderCompiler));
        sections.add(new Section("高级", leftX, advancedY, advanced));

        int debugY = 56 + frameGen.size() * (ROW_HEIGHT + ROW_GAP) + HEADER_GAP + ROW_GAP;
        List<AbstractWidget> debug = new ArrayList<>();
        debug.add(onOff("调试模式", MCDLSSGConfig.isEnableDebug(), MCDLSSGConfig::setEnableDebug));
        debug.add(onOff("详细性能分析", MCDLSSGConfig.isEnableDetailedProfiling(), MCDLSSGConfig::setEnableDetailedProfiling));
        debug.add(onOff("导出着色器", MCDLSSGConfig.isDebugDumpShader(), MCDLSSGConfig::setDebugDumpShader));
        debug.add(onOff("RenderDoc", MCDLSSGConfig.isEnableRenderDoc(), MCDLSSGConfig::setEnableRenderDoc));
        sections.add(new Section("调试", rightX, debugY, debug));

        for (Section section : sections) {
            int y = section.startY();
            for (AbstractWidget widget : section.widgets()) {
                widget.setX(section.x());
                widget.setY(y);
                this.addRenderableWidget(widget);
                y += ROW_HEIGHT + ROW_GAP;
            }
        }

        int doneY = this.height - 30;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            MCDLSSGConfig.SPEC.save();
            this.onClose();
        }).bounds(centerX - 110, doneY, 100, ROW_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.literal("保存"), button -> MCDLSSGConfig.SPEC.save())
                .bounds(centerX + 10, doneY, 100, ROW_HEIGHT).build());
    }

    private CycleButton<Boolean> onOff(String label, boolean current, java.util.function.Consumer<Boolean> consumer) {
        return CycleButton.onOffBuilder(current)
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.literal(label),
                        (button, value) -> consumer.accept(value));
    }

    private void selectAlgorithm(AlgorithmDescription<?> algo) {
        RuntimeLibraryDownloader.Feature feature = null;
        if (algo.equals(AlgorithmDescriptions.DLSS)) {
            feature = RuntimeLibraryDownloader.Feature.DLSS_SR;
        } else if (algo.equals(AlgorithmDescriptions.XESS)) {
            feature = RuntimeLibraryDownloader.Feature.XESS;
        }
        if (feature != null && !RuntimeLibraryDownloader.isFeatureReady(feature)) {
            RuntimeLibraryDownloader.Feature finalFeature = feature;
            openDownloadScreen(feature, () -> MCDLSSGConfig.setUpscaleAlgorithm(finalFeature == RuntimeLibraryDownloader.Feature.DLSS_SR
                    ? AlgorithmDescriptions.DLSS
                    : AlgorithmDescriptions.XESS));
            return;
        }
        MCDLSSGConfig.setUpscaleAlgorithm(algo);
    }

    private void openDownloadScreen(RuntimeLibraryDownloader.Feature feature, Runnable onDone) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DevDownloadScreen(this, feature, onDone));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        String status = buildStatusText();
        int statusColor = DLSSGRuntime.isPresentingActive() ? 0xFF55FF55 : 0xFFFFFF55;
        guiGraphics.drawCenteredString(this.font, Component.literal(status), this.width / 2, 34, statusColor);

        for (Section section : sections) {
            guiGraphics.drawCenteredString(this.font, Component.literal("§n" + section.title()),
                    section.x() + COLUMN_WIDTH / 2, section.startY() - HEADER_GAP + 2, 0xFFFFFFFF);
        }
    }

    private String buildStatusText() {
        if (!MCDLSSGConfig.isEnableDLSSFrameGeneration()) {
            return "DLSS G: OFF";
        }
        if (!Streamline.isDLSSGAvailable()) {
            return "DLSS G: UNAVAILABLE";
        }
        if (DLSSGRuntime.isPresentingActive()) {
            return String.format("DLSS G: ON x%d  %.0f -> %.0f FPS",
                    DLSSGRuntime.getGeneratedFrameCount() + 1,
                    DLSSGRuntime.getRealFps(),
                    DLSSGRuntime.getDisplayedFps());
        }
        return "DLSS G: STANDBY";
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static final class PercentSlider extends AbstractSliderButton {
        private final String label;
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> consumer;

        PercentSlider(String label, float value, float min, float max, java.util.function.Consumer<Float> consumer) {
            super(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.empty(), (value - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.consumer = consumer;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float value = (float) (this.value * (max - min) + min);
            setMessage(Component.literal(label + ": " + String.format(java.util.Locale.ROOT, "%.2f", value)));
        }

        @Override
        protected void applyValue() {
            consumer.accept((float) (this.value * (max - min) + min));
        }
    }
}
