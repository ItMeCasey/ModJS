package com.itmecasey.modjs.compat.emi;

import com.itmecasey.modjs.compat.viewer.ViewerItems;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** EMI discovers this adapter only when EMI is installed. */
@EmiEntrypoint
public final class ModJSEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        var hidden = ViewerItems.hiddenItems();
        registry.removeEmiStacks(stack -> hidden.contains(stack.getItemStack().getItem()));
        int index = 0;
        for (var info : ViewerItems.information().entrySet()) {
            List<EmiIngredient> stacks = new ArrayList<>();
            ViewerItems.resolve(info.getKey()).forEach(item -> stacks.add(EmiStack.of(item)));
            if (!stacks.isEmpty()) registry.addRecipe(new EmiInfoRecipe(stacks,
                info.getValue().stream().<Component>map(Component::literal).toList(),
                new ResourceLocation("modjs", "information/" + index++)));
        }
        var categories = ViewerItems.hiddenCategories();
        registry.removeRecipes(recipe -> categories.contains(recipe.getCategory().getId().toString()));
    }
}
