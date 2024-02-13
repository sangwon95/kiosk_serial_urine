package com.onwards.kiosk.utils;

import android.content.Context;
import android.util.Log;

public class Etc {
    /**
     * String to byte
     * @param value
     * @return byte[] data
     */
   static public byte[] hexStringToByteArray(String value) {
        byte[] data = new byte[value.length() / 2]; // 크기를 반으로 자르고

        for (int i = 0; i < value.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(value.charAt(i), 16) << 4) + Character.digit(value.charAt(i + 1), 16));
            // Log.d(TAG,"ByteData:" + data[j]);
        }
        return data;
    }
}
