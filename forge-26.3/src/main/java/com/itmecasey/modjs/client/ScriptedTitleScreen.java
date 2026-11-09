package com.itmecasey.modjs.client;

import com.itmecasey.modjs.script.ModJSContextFactory;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.gui.ModListScreen;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The main menu described by {@code ClientEvents.titleScreen}. */
public final class ScriptedTitleScreen extends Screen implements ScriptedScreens.Context {
    private final java.util.List<java.util.Map.Entry<Button, TitleScreenConfig.Button>> iconButtons = new java.util.ArrayList<>();

    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private final TitleScreenConfig config;
    private final Screen previous;
    private final java.util.Map<String, net.minecraft.client.gui.components.EditBox> boxes = new java.util.HashMap<>();
    private final java.util.Map<String, String> kept = new java.util.HashMap<>();
    private final LogoRenderer logoRenderer = new LogoRenderer(false);
    private Identifier backgroundTexture;
    private Identifier logoTexture;
    private SplashRenderer splash;

    public ScriptedTitleScreen(TitleScreenConfig config) {
        this(config, null);
    }

    public ScriptedTitleScreen(TitleScreenConfig config, Screen previous) {
        super(Component.translatable("narrator.screen.title"));
        this.config = config;
        this.previous = previous;
        String bg = config.backgroundKind();
        if (bg.contains(":")) {
            backgroundTexture = Identifier.tryParse(bg);
        }
        String logo = config.logoKind();
        if (logo.contains(":")) {
            logoTexture = Identifier.tryParse(logo);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return config.closesOnEsc();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(previous);
    }

    @Override
    public String getValue(String id) {
        net.minecraft.client.gui.components.EditBox box = boxes.get(id);
        return box != null ? box.getValue() : kept.getOrDefault(id, "");
    }

    @Override
    public void setValue(String id, String value) {
        net.minecraft.client.gui.components.EditBox box = boxes.get(id);
        if (box != null) {
            box.setValue(value);
        }
        kept.put(id, value);
    }

    @Override
    public void close() {
        onClose();
    }

    @Override
    public void open(String name) {
        TitleScreenConfig next = ScriptedScreens.get(name);
        if (next != null) {
            minecraft.gui.setScreen(new ScriptedTitleScreen(next, this));
        }
    }

    @Override
    protected void init() {
        if (config.splashShown() && splash == null) {
            splash = minecraft.gui.splashManager().getSplash();
        }
        boxes.forEach((id, box) -> kept.put(id, box.getValue()));
        boxes.clear();
        for (TitleScreenConfig.Input input : config.inputs()) {
            net.minecraft.client.gui.components.EditBox box = new net.minecraft.client.gui.components.EditBox(font,
                width / 2 - input.width() / 2 + input.x(), input.y(), input.width(), input.height(), Component.literal(input.id()));
            box.setMaxLength(input.maxLength());
            box.setHint(Component.literal(input.placeholder()));
            box.setValue(kept.getOrDefault(input.id(), input.initial()));
            addRenderableWidget(box);
            boxes.put(input.id(), box);
        }
        int stackY = height / 4 + 48;
        for (TitleScreenConfig.Button spec : config.buttons()) {
            int w = spec.width();
            int h = spec.height();
            int x = width / 2 - w / 2 + (spec.x() != null ? spec.x() : 0);
            int y;
            if (spec.y() != null) {
                y = spec.y();
            } else {
                y = stackY;
                stackY += h + 4;
            }
            Button widget = Button.builder(Component.literal(spec.label()), button -> run(spec)).bounds(x, y, w, h).build();
            addRenderableWidget(widget);
            if (spec.icon() != null) {
                iconButtons.add(java.util.Map.entry(widget, spec));
            }
        }
    }

    private void run(TitleScreenConfig.Button spec) {
        if (spec.callback() != null) {
            Function fn = spec.callback();
            try {
                ModJSContextFactory.INSTANCE.call(cx -> fn.call(cx, fn.getParentScope(), fn.getParentScope(), new Object[] {this}));
            } catch (RhinoException e) {
                LOGGER.error("Title screen button '{}' failed: {}", spec.label(), e.getMessage());
            }
            return;
        }
        String action = spec.action() == null ? "" : spec.action();
        if (action.startsWith("connect:")) {
            String host = action.substring("connect:".length()).strip();
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(host),
                new ServerData(spec.label(), host, ServerData.Type.OTHER), false, null);
        } else if (action.equals("close")) {
            onClose();
        } else if (action.startsWith("open:")) {
            open(action.substring("open:".length()).strip());
        } else if (action.startsWith("url:")) {
            com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(action.substring("url:".length()).strip()));
        } else {
            switch (action) {
                case "singleplayer" -> minecraft.gui.setScreen(new SelectWorldScreen(this));
                case "multiplayer" -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
                case "options" -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options));
                case "languages" -> minecraft.gui.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
                case "mods" -> minecraft.gui.setScreen(new ModListScreen(this));
                case "quit" -> minecraft.stop();
                default -> LOGGER.warn("Unknown title screen action '{}'", action);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        switch (config.backgroundKind()) {
            case "panorama", "dirt", "default" -> super.extractBackground(graphics, mouseX, mouseY, partialTick);
            case "color" -> graphics.fill(0, 0, width, height, config.backgroundColor());
            default -> {
                if (backgroundTexture != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, backgroundTexture, 0, 0, 0.0f, 0.0f, width, height, width, height);
                } else {
                    graphics.fill(0, 0, width, height, config.backgroundColor());
                }
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        switch (config.logoKind()) {
            case "none" -> {
            }
            case "vanilla" -> logoRenderer.extractRenderState(graphics, width, 1.0f);
            default -> {
                if (logoTexture != null) {
                    int w = Math.min(width - 40, 256);
                    int h = w / 4;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, logoTexture, width / 2 - w / 2, 30, 0.0f, 0.0f, w, h, w, h);
                }
            }
        }
        if (splash != null) {
            splash.extractRenderState(graphics, width, font, 0xFFFFFFFF);
        }
        for (TitleScreenConfig.Text text : config.texts()) {
            if (text.centered()) {
                graphics.centeredText(font, text.text(), width / 2 + text.x(), text.y(), text.color());
            } else {
                graphics.text(font, text.text(), width / 2 + text.x(), text.y(), text.color());
            }
        }
        if (config.versionShown()) {
            graphics.text(font, "Minecraft " + SharedConstants.getCurrentVersion().name(), 2, height - 10, 0xFFFFFFFF);
        }
        drawOverlays(graphics);
    }

    /** Draws button icons and the retained overlay elements made for screens. */
    private void drawOverlays(GuiGraphicsExtractor graphics) {
        GuiOverlayCanvas canvas = new GuiOverlayCanvas(graphics);
        for (java.util.Map.Entry<Button, TitleScreenConfig.Button> entry : iconButtons) {
            Button widget = entry.getKey();
            TitleScreenConfig.Button spec = entry.getValue();
            int size = spec.iconSize();
            int ix = switch (spec.iconSide()) {
                case "right" -> widget.getX() + widget.getWidth() - 4 - size;
                case "center" -> widget.getX() + (widget.getWidth() - size) / 2;
                default -> widget.getX() + 4;
            };
            int iy = widget.getY() + (widget.getHeight() - size) / 2;
            String icon = spec.icon();
            if (icon.contains("/") || icon.endsWith(".png")) {
                canvas.texture(icon, ix, iy, size, size, 0.0f, 0.0f, 1, 1, 1, 1, 0xFFFFFFFF);
            } else {
                canvas.item(icon.contains(":") ? icon : "minecraft:" + icon, ix, iy, size / 16.0f);
            }
        }
        com.itmecasey.modjs.client.overlay.OverlayRenderer.draw(canvas, "screen");
    }
}
