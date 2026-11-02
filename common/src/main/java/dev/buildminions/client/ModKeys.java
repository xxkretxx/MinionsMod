package dev.buildminions.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLScancode;

/** Key bindings used while positioning the hologram. Registered by each loader. */
public final class ModKeys {
    private static KeyMapping key(String name, int scancode) {
        return new KeyMapping("key.buildminions." + name, InputConstants.Type.KEYBOARD, scancode, KeyMapping.Category.MISC);
    }

    public static final KeyMapping ROTATE = key("rotate", SDLScancode.SDL_SCANCODE_R);
    public static final KeyMapping MIRROR = key("mirror", SDLScancode.SDL_SCANCODE_M);
    public static final KeyMapping RAISE = key("raise", SDLScancode.SDL_SCANCODE_UP);
    public static final KeyMapping LOWER = key("lower", SDLScancode.SDL_SCANCODE_DOWN);
    public static final KeyMapping LOCK = key("lock", SDLScancode.SDL_SCANCODE_K);
    public static final KeyMapping CONFIRM = key("confirm", SDLScancode.SDL_SCANCODE_RETURN);
    public static final KeyMapping CANCEL = key("cancel", SDLScancode.SDL_SCANCODE_BACKSPACE);

    public static final List<KeyMapping> ALL = List.of(ROTATE, MIRROR, RAISE, LOWER, LOCK, CONFIRM, CANCEL);

    private ModKeys() {}
}
