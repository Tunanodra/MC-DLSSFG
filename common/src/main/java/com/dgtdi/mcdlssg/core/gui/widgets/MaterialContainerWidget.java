/*
 * Super Resolution
 * Copyright (c) 2025-2026. 187J3X1-114514
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.dgtdi.mcdlssg.core.gui.widgets;

import com.dgtdi.mcdlssg.core.gui.MaterialScheme;
import com.dgtdi.mcdlssg.core.gui.MaterialUI;
import com.dgtdi.mcdlssg.core.gui.core.AbstractContainerWidget;
import com.dgtdi.mcdlssg.core.gui.core.layout.ILayoutElement;

public abstract class MaterialContainerWidget<T extends MaterialContainerWidget<T>> extends AbstractContainerWidget<T> {
    public MaterialScheme scheme() {
        return MaterialUI.Scheme;
    }

    @Override
    public void addChild(ILayoutElement element) {
        super.addChild(element);
    }
}
