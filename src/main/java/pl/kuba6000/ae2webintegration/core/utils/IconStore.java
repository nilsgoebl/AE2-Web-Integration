package pl.kuba6000.ae2webintegration.core.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import pl.kuba6000.ae2webintegration.core.config.Config;

/**
 * Resolves item ids to pre-rendered icon PNGs from the configured icons directory.
 * <p>
 * The on-disk layout mirrors item ids: every {@code ':'} and {@code '/'} in the id becomes a path
 * separator, and {@code .png} is appended, so {@code gregtech:gt.metaitem.01/32600} lives at
 * {@code <icons_directory>/gregtech/gt.metaitem.01/32600.png}. Anything that does not survive
 * sanitization - including ids trying to escape the directory with {@code ..} - resolves to a
 * bundled placeholder instead of a file read.
 */
public class IconStore {

    /**
     * 16x16 neutral grey placeholder shown whenever no rendered icon exists for an item.
     * Embedded as base64 so the jar stays free of binary resources.
     */
    private static final String PLACEHOLDER_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAGklEQVR42mNYsWLFf0oww6gBowaMGjBcDAAAk9v3H//7IkQAAAAASUVORK5CYII=";

    private static volatile byte[] placeholder;

    public static byte[] getPlaceholder() {
        byte[] bytes = placeholder;
        if (bytes == null) {
            synchronized (IconStore.class) {
                if (placeholder == null) {
                    placeholder = Base64.getDecoder()
                        .decode(PLACEHOLDER_PNG_BASE64);
                }
                bytes = placeholder;
            }
        }
        return bytes;
    }

    /**
     * Turns an item id into a safe relative path, or null when the id cannot be mapped to one.
     * Only the characters that legitimately occur in registry names are accepted; in particular
     * {@code ..} segments and anything outside {@code A-Za-z0-9._-} are rejected outright.
     */
    public static String sanitizeRelativePath(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        String[] segments = itemId.split("[:/]");
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty() || !isSafeSegment(segment)) {
                return null;
            }
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(segment);
        }
        return path.append(".png")
            .toString();
    }

    private static boolean isSafeSegment(String segment) {
        if (".".equals(segment) || "..".equals(segment)) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '.'
                || c == '_'
                || c == '-';
            if (!safe) {
                return false;
            }
        }
        return true;
    }

    /** Reads the icon for an item id, falling back to the placeholder for any miss or error. */
    public static byte[] readIcon(String itemId) {
        String relativePath = sanitizeRelativePath(itemId);
        File iconsDirectory = Config.getIconsDirectory();
        if (relativePath == null || iconsDirectory == null) {
            return getPlaceholder();
        }
        try {
            return Files.readAllBytes(
                iconsDirectory.toPath()
                    .resolve(relativePath));
        } catch (IOException e) {
            return getPlaceholder();
        }
    }
}
