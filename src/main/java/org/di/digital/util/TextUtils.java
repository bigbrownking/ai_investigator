package org.di.digital.util;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class TextUtils {

    public static int visibleOffsetToRawOffset(String raw, int visibleOffset) {
        if (raw == null) return 0;
        int visible = 0;
        int rawIndex = 0;
        while (rawIndex < raw.length()) {
            if (raw.charAt(rawIndex) == '<') {
                int closeAngle = raw.indexOf('>', rawIndex);
                if (closeAngle != -1) {
                    rawIndex = closeAngle + 1;
                    continue;
                }
            }
            if (visible == visibleOffset) return rawIndex;
            visible++;
            rawIndex++;
        }
        return raw.length();
    }

    public static String stripHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "");
    }
}