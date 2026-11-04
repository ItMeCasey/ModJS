package com.itmecasey.modjs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/** Asks {@link ClientTweaks} about every toast before it shows, so scripts can hide or edit it. */
public final class ScriptedToasts extends ToastComponent {
    public ScriptedToasts(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public void addToast(Toast toast) {
        if (ClientTweaks.acceptToast(toast)) {
            super.addToast(toast);
        }
    }
}
