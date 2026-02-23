/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.ghhccghk.musicplay.util;

import android.annotation.SuppressLint;
import android.os.SystemProperties;

import com.ghhccghk.musicplay.util.log.AndroidLog;

/**
 * 系统属性工具类
 */
@SuppressLint("PrivateApi")
public class PropUtils {
    private static final String TAG = "PropUtils";


    public static boolean getProp(String name, boolean def) {
        try {
            return SystemProperties.getBoolean(name, def);
        } catch (Throwable e) {
            AndroidLog.e(TAG, "getProp boolean: " + name, e);
            return def;
        }
    }

    public static int getProp(String name, int def) {
        try {
            return SystemProperties.getInt(name, def);
        } catch (Throwable e) {
            AndroidLog.e(TAG, "getProp int: " + name, e);
            return def;
        }
    }

    public static long getProp(String name, long def) {
        try {
            return SystemProperties.getLong(name, def);
        } catch (Throwable e) {
            AndroidLog.e(TAG, "getProp long: " + name, e);
            return def;
        }
    }

    public static String getProp(String key, String defaultValue) {
        try {
            return SystemProperties.get(key, defaultValue);
        } catch (Throwable e) {
            AndroidLog.e(TAG, "getProp String: " + key, e);
            return defaultValue;
        }
    }

    public static String getProp(String name) {
        try {
            return SystemProperties.get(name);
        } catch (Throwable e) {
            AndroidLog.e(TAG, "getProp String: " + name, e);
            return "";
        }
    }


}
