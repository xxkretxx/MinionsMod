package dev.buildminions.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Key bindings used while positioning the hologram. Registered by each loader. */
public final class ModKeys {
    private static KeyMapping key(String name, int scancode) {
        return new KeyMapping("key.buildminions." + name, InputConstants.Type.KEYSYM, scancode, "key.categories.misc");
    }

    public static final KeyMapping ROTATE = key("rotate", InputConstants.KEY_R);
    public static final KeyMapping MIRROR = key("mirror", InputConstants.KEY_M);
    public static final KeyMapping RAISE = key("raise", InputConstants.KEY_UP);
    public static final KeyMapping LOWER = key("lower", InputConstants.KEY_DOWN);
    public static final KeyMapping LOCK = key("lock", InputConstants.KEY_K);
    public static final KeyMapping CONFIRM = key("confirm", InputConstants.KEY_RETURN);
    public static final KeyMapping CANCEL = key("cancel", InputConstants.KEY_BACKSPACE);

    public static final List<KeyMapping> ALL = List.of(ROTATE, MIRROR, RAISE, LOWER, LOCK, CONFIRM, CANCEL);

    private ModKeys() {}
}
