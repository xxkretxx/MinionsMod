package dev.buildminions;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants. Loader-specific entry points live in the fabric / neoforge projects. */
public final class BuildMinions {
    public static final String MOD_ID = "buildminions";
    public static final Logger LOG = LoggerFactory.getLogger("BuildMinions");

    private BuildMinions() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
