package com.itmecasey.modjs.client;

import com.itmecasey.modjs.script.ModJSContextFactory;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.ModListScreen;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The main menu described by {@code ClientEvents.titleScreen}. */
public final class ScriptedTitleScreen extends Screen implements ScriptedScreens.Context {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private final TitleScreenConfig config;
    private final Screen previous;
    private final java.util.Map<String, net.minecraft.client.gui.components.EditBox> boxes = new java.util.HashMap<>();
    private final java.util.Map<String, String> kept = new java.util.HashMap<>();
    private final PanoramaRenderer panorama = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    private final LogoRenderer logoRenderer = new LogoRenderer(false);
    private ResourceLocation backgroundTexture;
    private ResourceLocation logoTexture;
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
            backgroundTexture = ResourceLocation.tryParse(bg);
        }
        String logo = config.logoKind();
        if (logo.contains(":")) {
            logoTexture = ResourceLocation.tryParse(logo);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return config.closesOnEsc();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
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
            minecraft.setScreen(new ScriptedTitleScreen(next, this));
        }
    }

    @Override
    protected void init() {
        if (config.splashShown() && splash == null) {
            splash = minecraft.getSplashManager().getSplash();
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
            addRenderableWidget(Button.builder(Component.literal(spec.label()), button -> run(spec)).bounds(x, y, w, h).build());
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
            Util.getPlatform().openUri(action.substring("url:".length()).strip());
        } else {
            switch (action) {
                case "singleplayer" -> minecraft.setScreen(new SelectWorldScreen(this));
                case "multiplayer" -> minecraft.setScreen(new JoinMultiplayerScreen(this));
                case "options" -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
                case "languages" -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
                case "mods" -> minecraft.setScreen(new ModListScreen(this));
                case "quit" -> minecraft.stop();
                default -> LOGGER.warn("Unknown title screen action '{}'", action);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        switch (config.backgroundKind()) {
            case "panorama" -> panorama.render(graphics, width, height, 1.0f, partialTick);
            case "dirt" -> renderMenuBackground(graphics);
            case "default" -> super.renderBackground(graphics, mouseX, mouseY, partialTick);
            case "color" -> graphics.fill(0, 0, width, height, config.backgroundColor());
            default -> {
                if (backgroundTexture != null) {
                    graphics.blit(backgroundTexture, 0, 0, 0, 0.0f, 0.0f, width, height, width, height);
                } else {
                    graphics.fill(0, 0, width, height, config.backgroundColor());
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        switch (config.logoKind()) {
            case "none" -> {
            }
            case "vanilla" -> logoRenderer.renderLogo(graphics, width, 1.0f);
            default -> {
                if (logoTexture != null) {
                    int w = Math.min(width - 40, 256);
                    int h = w / 4;
                    graphics.blit(logoTexture, width / 2 - w / 2, 30, 0, 0.0f, 0.0f, w, h, w, h);
                }
            }
        }
        if (splash != null) {
            splash.render(graphics, width, font, 0xFFFFFFFF);
        }
        for (TitleScreenConfig.Text text : config.texts()) {
            if (text.centered()) {
                graphics.drawCenteredString(font, text.text(), width / 2 + text.x(), text.y(), text.color());
            } else {
                graphics.drawString(font, text.text(), width / 2 + text.x(), text.y(), text.color());
            }
        }
        if (config.versionShown()) {
            graphics.drawString(font, "Minecraft " + SharedConstants.getCurrentVersion().getName(), 2, height - 10, 0xFFFFFFFF);
        }
    }
}
