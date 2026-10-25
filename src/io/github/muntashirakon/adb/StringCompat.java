// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.adb;

import androidx.annotation.NonNull;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;

final class StringCompat {
    @NonNull
    public static byte[] getBytes(@NonNull String text, @NonNull String charsetName) {
        return text.getBytes(Charset.forName(charsetName));
    }
}
