package frontend.utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Copyright (C) 2014  Wikimedia Foundation
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Decoder for percent encoded strings
 * <p/>
 * In contrast to URLDecoder, this decoder keeps percent signs that are not
 * followed by hexadecimal digits, and does not convert plus-signs to spaces.
 */
public final class PercentDecoder {

    /**
     * Hex value benchmark.
     */
    private static final char HEX_0 = 0x30,
            HEX_UPPER_A = 0x37,
            HEX_LOWER_A = 0x57;

    /**
     * The offset of the first hex value of
     * percent encoded UTF-8 characters.
     */
    private static final int UTF_8_OFFSET = 4;

    /**
     * Return hex value given a byte.
     *
     * @param b to convert
     * @return hex value, -1 if if invalid
     */
    private static int getHexValue(final byte b) {
        if ('0' <= b && b <= '9') {
            return b - HEX_0;
        } else if ('A' <= b && b <= 'F') {
            return b - HEX_UPPER_A;
        } else if ('a' <= b && b <= 'f') {
            return b - HEX_LOWER_A;
        }
        return -1;
    }

    /**
     * Utility classes should not have a public or default constructor.
     */
    private PercentDecoder() {

    }

    /**
     * Decodes percent encoded strings.
     *
     * @param encoded the percent encoded string
     * @return the decoded string
     */
    public static String decode(final String encoded) {
        if (encoded == null) {
            return null;
        }

        byte[] encodedChars = encoded.getBytes(StandardCharsets.UTF_8);
        int encodedLength = encodedChars.length;
        byte[] decodedChars = new byte[encodedLength];

        int decodedIdx = 0;
        for (int encodedIdx = 0;
             encodedIdx < encodedLength;
             encodedIdx++, decodedIdx++) {
            decodedChars[decodedIdx] = encodedChars[encodedIdx];
            if (decodedChars[decodedIdx] == '%') {
                if (encodedIdx + 2 < encodedLength) {
                    int value1 = getHexValue(
                            encodedChars[encodedIdx + 1]);
                    int value2 = getHexValue(
                            encodedChars[encodedIdx + 2]);
                    if (value1 >= 0 && value2 >= 0) {
                        decodedChars[decodedIdx] =
                                (byte) ((value1 << UTF_8_OFFSET) + value2);
                        encodedIdx += 2;
                    }
                }
            }
        }
        return new String(Arrays.copyOf(decodedChars, decodedIdx),
                StandardCharsets.UTF_8);
    }
}