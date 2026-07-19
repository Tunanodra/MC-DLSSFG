/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.dgtdi.mcdlssg.thirdparty.yoga.appliedenergistics.yoga;

import java.util.Objects;

/**
 * Java conversion of YGValue
 */
public class YogaValue {
    // Constants
    public static final YogaValue ZERO = new YogaValue(0, com.dgtdi.mcdlssg.thirdparty.yoga.appliedenergistics.yoga.YogaUnit.POINT);
    public static final YogaValue UNDEFINED = new YogaValue(YogaConstants.UNDEFINED, com.dgtdi.mcdlssg.thirdparty.yoga.appliedenergistics.yoga.YogaUnit.UNDEFINED);
    public static final YogaValue AUTO = new YogaValue(YogaConstants.UNDEFINED, com.dgtdi.mcdlssg.thirdparty.yoga.appliedenergistics.yoga.YogaUnit.AUTO);
    public final float value;
    public final com.dgtdi.mcdlssg.thirdparty.yoga.appliedenergistics.yoga.YogaUnit unit;

    public YogaValue(float value, com.dgtdi.mcdlssg.thirdparty.yoga.appliedenergistics.yoga.YogaUnit unit) {
        this.value = value;
        this.unit = unit;
    }

    public static YogaValue point(float value) {
        return new YogaValue(value, com.dgtdi.mcdlssg.thirdparty.yoga.appliedenergistics.yoga.YogaUnit.POINT);
    }

    public static YogaValue percent(float value) {
        return new YogaValue(value, YogaUnit.PERCENT);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        YogaValue yogaValue = (YogaValue) o;

        if (unit != yogaValue.unit) {
            return false;
        }

        return switch (unit) {
            case UNDEFINED, AUTO, FIT_CONTENT, MAX_CONTENT, STRETCH -> true;
            case POINT, PERCENT -> Float.compare(value, yogaValue.value) == 0;
        };
    }

    @Override
    public String toString() {
        return switch (unit) {
            case UNDEFINED -> "undefined";
            case POINT -> value + "pt";
            case PERCENT -> value + "%";
            case AUTO -> "auto";
            case MAX_CONTENT -> "max-content";
            case FIT_CONTENT -> "fit-content";
            case STRETCH -> "stretch";
        };
    }

    public YogaValue neg() {
        return new YogaValue(-value, unit);
    }
}
