/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.web.widgets.client.grid.selection;

import com.google.gwt.event.shared.HandlerRegistration;
import com.haulmont.cuba.web.widgets.CubaMultiSelectionModel;
import com.haulmont.cuba.web.widgets.client.Tools;
import com.vaadin.client.ServerConnector;
import com.vaadin.client.connectors.grid.MultiSelectionModelConnector;
import com.vaadin.client.renderers.Renderer;
import com.vaadin.client.widget.grid.events.BodyClickHandler;
import com.vaadin.client.widgets.Grid;
import com.vaadin.shared.ui.Connect;
import elemental.json.JsonObject;

@Connect(CubaMultiSelectionModel.class)
public class CubaMultiSelectionModelConnector extends MultiSelectionModelConnector {

    protected HandlerRegistration clickHandler;

    @Override
    protected MultiSelectionModel createSelectionModel() {
        return Tools.isUseSimpleMultiselectForTouchDevice()
                ? super.createSelectionModel()
                : new MultiSelectionModel() {
            @Override
            public Renderer<Boolean> getRenderer() {
                return null;
            }
        };
    }

    @Override
    protected void extend(ServerConnector target) {
        super.extend(target);

        if (!Tools.isUseSimpleMultiselectForTouchDevice()) {
            if (clickHandler != null) {
                clickHandler.removeHandler();
                clickHandler = null;
            }

            Grid<JsonObject> grid = getGrid();
            BodyClickHandler handler = createBodyClickHandler(grid);
            clickHandler = grid.addBodyClickHandler(handler);
        }
    }

    protected MultiSelectionBodyClickHandler createBodyClickHandler(Grid<JsonObject> grid) {
        return new MultiSelectionBodyClickHandler(grid, getRpcProxy(CubaMultiSelectionModelServerRpc.class));
    }

    @Override
    public void onUnregister() {
        if (clickHandler != null) {
            clickHandler.removeHandler();
            clickHandler = null;
        }

        super.onUnregister();
    }
}
