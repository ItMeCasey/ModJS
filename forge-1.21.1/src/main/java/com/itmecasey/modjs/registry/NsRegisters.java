package com.itmecasey.modjs.registry;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** One {@link DeferredRegister} per namespace for a registry, so scripts can register {@code mypack:ruby} next to {@code modjs:ruby}. */
final class NsRegisters<T> {
    private final Function<String, DeferredRegister<T>> factory;
    private final Map<String, DeferredRegister<T>> registers = new LinkedHashMap<>();

    NsRegisters(Function<String, DeferredRegister<T>> factory) {
        this.factory = factory;
    }

    <I extends T> RegistryObject<I> register(String namespace, String name, Supplier<? extends I> supplier) {
        return registers.computeIfAbsent(namespace, factory).register(name, supplier);
    }

    /** Registers every namespace's register on the mod bus, as {@code registerAll(r -> r.register(modBus))}. */
    void registerAll(Consumer<DeferredRegister<T>> onBus) {
        registers.values().forEach(onBus);
    }
}
