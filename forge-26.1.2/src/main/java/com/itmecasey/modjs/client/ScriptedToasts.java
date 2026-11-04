package com.itmecasey.modjs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/** Asks {@link ClientTweaks} about every toast before it shows, so scripts can hide or edit it. */
public final class ScriptedToasts extends ToastManager {
    public ScriptedToasts(Minecraft minecraft) {
        super(minecraft, minecraft.options);
    }

    @Override
    public void addToast(Toast toast) {
        if (ClientTweaks.acceptToast(toast)) {
            super.addToast(toast);
        }
    }
}
