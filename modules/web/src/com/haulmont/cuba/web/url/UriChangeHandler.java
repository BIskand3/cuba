/*
 * Copyright (c) 2008-2018 Haulmont.
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

package com.haulmont.cuba.web.url;

import com.haulmont.bali.util.ParamsMap;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.Screens;
import com.haulmont.cuba.gui.WindowParams;
import com.haulmont.cuba.gui.components.RootWindow;
import com.haulmont.cuba.gui.components.Window;
import com.haulmont.cuba.gui.components.mainwindow.AppWorkArea;
import com.haulmont.cuba.gui.config.WindowConfig;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.navigation.Navigation;
import com.haulmont.cuba.gui.navigation.Navigation.UriState;
import com.haulmont.cuba.gui.navigation.UriStateChangedEvent;
import com.haulmont.cuba.gui.screen.EditorScreen;
import com.haulmont.cuba.gui.screen.MapScreenOptions;
import com.haulmont.cuba.gui.screen.OpenMode;
import com.haulmont.cuba.gui.screen.Screen;
import com.haulmont.cuba.gui.sys.UiControllerDefinition;
import com.haulmont.cuba.gui.xml.layout.ScreenXmlLoader;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.gui.components.mainwindow.WebAppWorkArea;
import com.haulmont.cuba.web.navigation.IdToBase64Converter;
import com.haulmont.cuba.web.sys.TabWindowContainer;
import com.haulmont.cuba.web.sys.VaadinSessionScope;
import com.haulmont.cuba.web.widgets.TabSheetBehaviour;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

@Component(UriChangeHandler.NAME)
@Scope(VaadinSessionScope.NAME)
public class UriChangeHandler {

    public static final String NAME = "cuba_UriChangeHandler";

    private static final Logger log = LoggerFactory.getLogger(UriChangeHandler.class);

    @Inject
    protected Navigation navigation;

    @Inject
    protected WindowConfig windowConfig;

    @Inject
    protected Screens screens;

    @Inject
    protected ScreenXmlLoader screenXmlLoader;
    @Inject
    protected Scripting scripting;
    @Inject
    protected Metadata metadata;
    @Inject
    protected DataManager dataManager;

    @SuppressWarnings("unused")
    public void handleUriChange(String uri) {
        UriState state = navigation.getState();

        if (historyNavigation(state)) {
            handleHistoryNavigation(state);
        } else {
            handleScreenNavigation(state);
        }
    }

    protected boolean historyNavigation(UriState state) {
        // TODO: implement
        return false;
    }

    protected void handleHistoryNavigation(UriState state) {
        // TODO: implement
    }

    protected void handleScreenNavigation(UriState state) {
        if (rootChanged(state)) {
            handleRootChange(state);
            return;
        }

        if (screenChanged(state)) {
            handleScreenChange(state);
            return;
        }

        if (paramsChanged(state)) {
            handleParamsChange(state);
        }
    }

    protected boolean rootChanged(UriState state) {
        UiControllerDefinition.PageDefinition page = AppUI.getCurrent()
                .getTopLevelWindow()
                .getScreenContext()
                .getWindowInfo()
                .getPageDefinition();

        if (page == null) {
            throw new RuntimeException("Unable to determine route for current root window");
        }

        return !page.getRoute().equals(state.getRoot());
    }

    protected void handleRootChange(UriState state) {
        // TODO: implement
    }

    protected boolean screenChanged(UriState state) {
        WebAppWorkArea workArea = getConfiguredWorkArea();
        TabWindowContainer windowContainer;

        if (workArea.getMode() == AppWorkArea.Mode.TABBED) {
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();
            windowContainer = (TabWindowContainer) tabSheet.getSelectedTab();
        } else {
            windowContainer = (TabWindowContainer) workArea.getSingleWindowContainer().getComponent(0);
        }

        if (windowContainer == null) {
            return true;
        }

        Screen currentScreen = windowContainer.getBreadCrumbs().getCurrentWindow().getFrameOwner();
        UiControllerDefinition.PageDefinition page = currentScreen.getScreenContext()
                .getWindowInfo()
                .getPageDefinition();

        if (page == null) {
            throw new RuntimeException("Unable to determine route for current screen");
        }

        // TODO: handle complex cases

        return !page.getRoute().equals(state.getNestedRoute());
    }

    // Copied from WebScreens
    protected WebAppWorkArea getConfiguredWorkArea() {
        RootWindow topLevelWindow = AppUI.getCurrent().getTopLevelWindow();

        Screen controller = topLevelWindow.getFrameOwner();

        if (controller instanceof Window.HasWorkArea) {
            AppWorkArea workArea = ((Window.HasWorkArea) controller).getWorkArea();
            if (workArea != null) {
                return (WebAppWorkArea) workArea;
            }
        }

        throw new IllegalStateException("RootWindow does not have any configured work area");
    }

    protected void handleScreenChange(UriState state) {
        String route = state.getNestedRoute();

        WindowInfo windowInfo = windowConfig.findWindowInfoByRoute(route);
        if (windowInfo == null) {
            log.debug("Unable to find WindowInfo for route: {}", route);
            return;
        }

        Map<String, Object> screenOptions = null;
        if (EditorScreen.class.isAssignableFrom(windowInfo.getControllerClass())) {
            screenOptions = createEditorScreenOptions(windowInfo, state);
        }

        Screen screen = screens.create(windowInfo, OpenMode.THIS_TAB, new MapScreenOptions(screenOptions));

        if (screen instanceof EditorScreen) {
            //noinspection unchecked
            ((EditorScreen) screen).setEntityToEdit(
                    (com.haulmont.cuba.core.entity.Entity) screenOptions.get(WindowParams.ITEM.name()));
        }

        screens.show(screen);
    }

    protected Map<String, Object> createEditorScreenOptions(WindowInfo windowInfo, UriState state) {
        Element screenTemplate = screenXmlLoader.load(windowInfo.getTemplate(), windowInfo.getId(), Collections.emptyMap());

        Element dsElement = screenTemplate.element("dsContext")
                .element("datasource");

        /*
         * TODO: rewrite with reflection (see UiControllerReflectionInspector.getAddListenerMethodsNotCached)
         * for legacy screens and with "setEntityToEdit" for new screens
         */

        String entityClassFqn = dsElement.attributeValue("class");
        Class<?> entityClass = scripting.loadClassNN(entityClassFqn);
        Object id = IdToBase64Converter.deserialize(findIdType(entityClass), state.getParams().get("id"));

        LoadContext<?> ctx = new LoadContext(metadata.getClassNN(entityClass));
        ctx.setId(id);
        ctx.setView(dsElement.attributeValue("view"));

        Entity entity = dataManager.load(ctx);
        if (entity == null) {
            throw new RuntimeException("Failed to load entity!");
        }

        return ParamsMap.of(WindowParams.ITEM.name(), entity);
    }

    protected Class findIdType(Class entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if ("id".equals(field.getName())) {
                return field.getType();
            }
        }
        return findIdType(entityClass.getSuperclass());
    }

    protected boolean paramsChanged(UriState state) {
        // TODO: check if new root and nestedRoute are the same as current
        return false;
    }

    protected void handleParamsChange(UriState state) {
        // TODO: invoke urlParamsChanged screen hook
    }

    @Order(Events.LOWEST_PLATFORM_PRECEDENCE)
    @EventListener
    protected void handleUriStateChanged(UriStateChangedEvent event) {
        // TODO: handle?
    }
}
