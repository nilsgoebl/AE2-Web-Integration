package pl.kuba6000.ae2webintegration.core.ae2request.async;

import static pl.kuba6000.ae2webintegration.core.AE2Controller.hashcodeToStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

import pl.kuba6000.ae2webintegration.core.interfaces.IAEGenericStack;
import pl.kuba6000.ae2webintegration.core.utils.HTTPUtils;
import pl.kuba6000.ae2webintegration.core.utils.IconStore;

/**
 * Serves pre-rendered item icons for the terminal view. The website batches the hashcodes of every
 * displayed item that it has no icon for; each hash is resolved through the same map that
 * {@code GetItems} populated, then mapped to a PNG from the configured icons directory.
 * <p>
 * Runs directly on the HTTP worker thread: icons never touch live AE2 state, and a large batch must
 * not occupy the server tick. Unknown or stale hashes answer with the placeholder instead of an
 * error so one outdated entry cannot fail a whole batch.
 */
public class GetIcons extends IAsyncRequest {

    /** Bounds both the request URL and the response size; the website paginates well below this. */
    public static final int MAX_BATCH_SIZE = 128;

    private static class JSON_IconEntry {

        public int hashcode;
        public String pngData;
    }

    @Override
    public void handle(Map<String, String> getParams) {
        String items = getParams.get("items");
        if (items == null || items.isEmpty()) {
            noParam("items");
            return;
        }
        String[] hashes = items.split(",");
        if (hashes.length > MAX_BATCH_SIZE) {
            deny("TOO_MANY_ITEMS");
            return;
        }
        ArrayList<JSON_IconEntry> icons = new ArrayList<>(hashes.length);
        Base64.Encoder encoder = Base64.getEncoder();
        for (String hashString : hashes) {
            Integer hash = HTTPUtils.parseInt(hashString.trim());
            if (hash == null) {
                continue;
            }
            IAEGenericStack stack = hashcodeToStack.get(hash);
            byte[] png = stack == null ? IconStore.getPlaceholder()
                : IconStore.readIcon(
                    stack.web$what()
                        .web$getItemID());
            JSON_IconEntry entry = new JSON_IconEntry();
            entry.hashcode = hash;
            entry.pngData = encoder.encodeToString(png);
            icons.add(entry);
        }
        succeed(icons);
    }
}
