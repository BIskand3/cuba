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
import com.haulmont.cuba.gui.Notifications;
import com.haulmont.cuba.gui.Screens;
import com.haulmont.cuba.gui.WindowParams;
import com.haulmont.cuba.gui.components.RootWindow;
import com.haulmont.cuba.gui.components.Window;
import com.haulmont.cuba.gui.components.mainwindow.AppWorkArea;
import com.haulmont.cuba.gui.config.WindowConfig;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.history.History;
import com.haulmont.cuba.gui.navigation.Navigation;
import com.haulmont.cuba.gui.navigation.UriState;
import com.haulmont.cuba.gui.navigation.UriStateChangedEvent;
import com.haulmont.cuba.gui.screen.*;
import com.haulmont.cuba.gui.sys.UiControllerDefinition;
import com.haulmont.cuba.gui.xml.layout.ScreenXmlLoader;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.controllers.ControllerUtils;
import com.haulmont.cuba.web.gui.WebWindow;
import com.haulmont.cuba.web.gui.components.mainwindow.WebAppWorkArea;
import com.haulmont.cuba.web.navigation.IdToBase64Converter;
import com.haulmont.cuba.web.sys.TabWindowContainer;
import com.haulmont.cuba.web.sys.VaadinSessionScope;
import com.haulmont.cuba.web.widgets.TabSheetBehaviour;
import com.vaadin.server.Page;
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
    protected History history;

    @Inject
    protected ScreenXmlLoader screenXmlLoader;

    @Inject
    protected Scripting scripting;

    @Inject
    protected Metadata metadata;

    @Inject
    protected DataManager dataManager;

    @Inject
    protected Notifications notifications;

    @Order(Events.LOWEST_PLATFORM_PRECEDENCE)
    @EventListener
    @SuppressWarnings("unused")
    protected void handleUriStateChanged(UriStateChangedEvent event) {
        // TODO: handle?
    }

    @SuppressWarnings("unused")
    public void handleUriChange(String uri) {
        UriState uriState = navigation.getState();

        if (historyNavigation(uriState)) {
            handleHistoryNavigation(uriState);
        } else {
            handleScreenNavigation(uriState);
        }
    }

    protected boolean historyNavigation(UriState uriState) {
        return history.searchBackward(uriState) || history.searchForward(uriState);
    }

    protected void handleHistoryNavigation(UriState uriState) {
        if (jumpOverHistory(uriState)) {
            reloadApp();
            return;
        }

        boolean backward = uriState.equals(history.lookBackward());
        boolean forward = uriState.equals(history.lookForward());

        if (!backward && !forward) {
            throw new RuntimeException("Unable to handle history navigation");
        }

        if (backward) {
            handleHistoryBackward();
        } else {
            handleHistoryForward();
        }
    }

    protected boolean jumpOverHistory(UriState uriState) {
        if (history.searchBackward(uriState) && !uriState.equals(history.lookBackward())) {
            return true;
        }
        if (history.searchForward(uriState) && !uriState.equals(history.lookForward())) {
            return true;
        }
        return false;
    }

    protected void reloadApp() {
        AppUI ui = AppUI.getCurrent();
        if (ui != null) {
            String url = ControllerUtils.getLocationWithoutParams() + "?restartApp";
            ui.getPage().open(url, "_self");
        }
    }

    protected void handleHistoryBackward() {
        Window nowWindow = findWindowByHistory(history.now());
        if (nowWindow != null) {
            nowWindow.getFrameOwner()
                    .close(FrameOwner.WINDOW_CLOSE_ACTION)
                    .then(this::proceedHistoryBackward)
                    .otherwise(this::revertHistoryBackward);
        } else {
            proceedHistoryBackward();
        }
    }

    protected void proceedHistoryBackward() {
        UriState prevState = history.backward();

        Window prevWindow = findWindowByHistory(prevState);
        if (prevWindow != null) {
            selectWindow(prevWindow);
        }

        String state;
        if (prevState.getNestedRoute() != null && !prevState.getNestedRoute().isEmpty()) {
            state = prevState.toString();
        } else {
            state = AppUI.getCurrent().getTopLevelWindow().getFrameOwner()
                    .getScreenContext().getWindowInfo().getPageDefinition()
                    .getRoute();
        }

        Page.getCurrent().replaceState("#!" + state);
    }

    protected void handleHistoryForward() {
        Window window = findWindowByHistory(history.now());
        if (window == null) {
            return;
        }

        String route = window.getFrameOwner()
                .getScreenContext()
                .getNavigationInfo()
                .getResolvedRoute();
        Page.getCurrent().replaceState("#!" + route);

        notifications.create()
                .setCaption("Unable to navigate forward through history")
                .setType(Notifications.NotificationType.HUMANIZED)
                .show();
    }

    protected void selectWindow(Window window) {
        WebAppWorkArea workArea = getConfiguredWorkArea();
        if (workArea.getMode() == AppWorkArea.Mode.TABBED) {
            TabWindowContainer windowContainer = (TabWindowContainer) window.unwrapComposition(com.vaadin.ui.Component.class)
                    .getParent();

            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();
            String tabId = tabSheet.getTab(windowContainer);
            if (tabId == null || tabId.isEmpty()) {
                throw new IllegalStateException("Unable to find tab");
            }

            tabSheet.setSelectedTab(tabId);
        }
    }

    protected void revertHistoryBackward() {
        String route = findWindowByHistory(history.now()).getFrameOwner()
                .getScreenContext()
                .getNavigationInfo()
                .getResolvedRoute();
        Page.getCurrent().replaceState("#!" + route);
    }

    protected Window findWindowByHistory(UriState uriState) {
        WebAppWorkArea workArea = getConfiguredWorkArea();
        String stateMark = uriState.getStateMark();

        if (workArea.getMode() == AppWorkArea.Mode.SINGLE) {
            Window window = ((TabWindowContainer) workArea.getSingleWindowContainer()
                    .getComponent(0))
                    .getBreadCrumbs()
                    .getCurrentWindow();

            return stateMark.equals(getWindowStateMark(window)) ?
                    window : null;
        } else {
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();
            for (int i = 0; i < tabSheet.getComponentCount(); i++) {
                String tabId = tabSheet.getTab(i);
                Window window = ((TabWindowContainer) tabSheet.getTabComponent(tabId))
                        .getBreadCrumbs()
                        .getCurrentWindow();

                if (stateMark.equals(getWindowStateMark(window))) {
                    return window;
                }
            }
        }
        return null;
    }

    protected String getWindowStateMark(Window window) {
        return String.valueOf(((WebWindow) window).getStateMark());
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
                    (Entity) screenOptions.get(WindowParams.ITEM.name()));
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
}
