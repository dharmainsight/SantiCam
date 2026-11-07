package jp.dharmainsight.shizukacamera;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Only show tap-to-open links that are normal HTTP(S) destinations. */
final class QrLinkPolicy {
    private QrLinkPolicy() {}

    static String accept(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty() || text.length() > 2048) return null;
        for (int i = 0; i < text.length(); i++)
            if (Character.isISOControl(text.charAt(i)) || Character.isWhitespace(text.charAt(i))) return null;
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme();
            if (scheme == null) return null;
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) return null;
            if (uri.isOpaque() || uri.getHost() == null || uri.getHost().isEmpty() ||
                uri.getRawUserInfo() != null) return null;
            return text;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
