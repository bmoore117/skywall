package com.hyperion.skywall.views.unlock;


import com.hyperion.skywall.backend.model.config.Delay;
import com.hyperion.skywall.backend.model.config.Location;
import com.hyperion.skywall.backend.model.config.job.AddTrustedLocationJob;
import com.hyperion.skywall.backend.model.config.job.SetDelayJob;
import com.hyperion.skywall.backend.services.ConfigService;
import com.hyperion.skywall.backend.services.GPSAccessDeniedException;
import com.hyperion.skywall.backend.services.JobRunner;
import com.hyperion.skywall.backend.services.WinUtils;
import com.hyperion.skywall.views.main.MainView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


@Route(value = "unlock", layout = MainView.class)
@PageTitle("Unlock & Delay")
@CssImport(value = "./styles/views/unlock/unlock-view.css", include = "lumo-badge")
@JsModule("@vaadin/vaadin-lumo-styles/badge.js")
public class UnlockView extends VerticalLayout implements AfterNavigationObserver {

    private static final Logger log = LoggerFactory.getLogger(UnlockView.class);
    public static final String DISABLE_STRICT_MODE = "Disable Strict Mode";
    public static final String ENABLE_STRICT_MODE = "Enable Strict Mode";

    private ConfigService configService;
    private JobRunner jobRunner;
    private WinUtils winUtils;

    private final Select<Delay> delay;
    private final Button saveDelay;

    private final Grid<Location> knownLocations;
    private final Binder<Location> binder;

    private final TextField name;
    private final NumberField latitude;
    private final NumberField longitude;

    private final Button save;
    private final Button unlock;
    private final Button activate;
    private final Button toggleStrictMode;
    private final Button getCurrentLocation;
    private final Button abortDelayChange;
    private final Button toggleFilter;
    private final Button restartFilter;

    private Notification notification;

    public UnlockView() {
        setSizeFull();
        delay = new Select<>();
        delay.setItems(Delay.values());
        delay.setLabel("Current Delay");
        delay.setItemLabelGenerator(Delay::toString);
        abortDelayChange = new Button("Abort Delay Change");
        abortDelayChange.addClickListener(e -> {
            if (jobRunner.cancelSetDelayJobs()) {
                showNotification("Pending delay change cancelled");
            } else {
                showNotification("No current pending delay changes");
            }
        });
        saveDelay = new Button("Save Delay");
        saveDelay.addClickListener(e -> {
            Delay value = delay.getValue();
            if (value != null) {
                if (Delay.valueInSeconds(value) < configService.getDelaySeconds()) {
                    SetDelayJob job = new SetDelayJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Set Delay: " + value.label, value);
                    jobRunner.queueJob(job);
                    showNotification("Your new delay will be set after waiting for the current delay");
                    abortDelayChange.setEnabled(true);
                } else {
                    SetDelayJob job = new SetDelayJob(LocalDateTime.now(), "Set Delay: " + value.label, value);
                    Delay current = configService.getCurrentDelay();
                    if (current == Delay.ZERO) {
                        showNotification("Your computer may restart if the admin password is still the stock password");
                    }
                    jobRunner.queueJob(job);
                }
            }
        });
        toggleFilter = new Button();
        toggleFilter.addClickListener(e -> {
            try {
                if (configService.getFilterConfig().isFilterActive()) {
                    winUtils.stopService(WinUtils.FILTER_SERVICE_NAME);
                    configService.withFilterTransaction(filterConfig -> filterConfig.setFilterActive(false));
                    toggleFilter.setText("Turn Filter On");
                } else {
                    winUtils.startService(WinUtils.FILTER_SERVICE_NAME);
                    configService.withFilterTransaction(filterConfig -> filterConfig.setFilterActive(true));
                    toggleFilter.setText("Turn Filter Off");
                }
            } catch (InterruptedException | IOException ex) {
                log.error("Error interacting with filter", ex);
                showNotification("Error interacting with filter");
            }
        });
        restartFilter = new Button("Restart Filter");
        restartFilter.addClickListener(e -> {
            try {
                winUtils.restartService(WinUtils.FILTER_SERVICE_NAME);
            } catch (IOException | InterruptedException ex) {
                String message = "Error restarting filter";
                log.error(message, ex);
                showNotification(message);
            }
        });

        name = new TextField();
        name.setLabel("Name");
        latitude = new NumberField();
        latitude.setLabel("Latitude");
        longitude = new NumberField();
        longitude.setLabel("Longitude");
        save = new Button("Save Location");
        unlock = new Button("Unlock");
        unlock.addClickListener(buttonClickEvent -> {
            try {
                Optional<Double[]> latLonOpt = winUtils.getGpsCoordinates();
                if (latLonOpt.isPresent()) {
                    Double[] latLon = latLonOpt.get();
                    Location location = new Location(null, latLon[0], latLon[1]);
                    if (inTrustedLocation(location)) {
                        SetDelayJob job = new SetDelayJob(null, "Location-based unlock", Delay.ZERO);
                        boolean result = jobRunner.runJob(job);
                        if (result) {
                            showNotification("Unlock successful. Password changed to stock value of " + ConfigService.STOCK_PASSWORD);
                        } else {
                            showErrorNotification("An error occurred");
                        }
                    } else {
                        showNotification("You do not appear to be in a trusted location");
                    }
                } else {
                    showErrorNotification("Error fetching location information: no data returned");
                }
            } catch (IOException | InterruptedException e) {
                log.error("Error fetching current location", e);
                showErrorNotification("Error fetching current location");
            } catch (GPSAccessDeniedException e) {
                log.error("Error fetching current location", e);
                showErrorNotification("Unable to fetch location: please enable location services");
            }
        });
        getCurrentLocation = new Button("Get Current Location");
        getCurrentLocation.addClickListener(buttonClickEvent -> {
            try {
                Optional<Double[]> latLonOpt = winUtils.getGpsCoordinates();
                if (latLonOpt.isPresent()) {
                    Double[] latLon = latLonOpt.get();
                    latitude.setValue(latLon[0]);
                    longitude.setValue(latLon[1]);
                } else {
                    showErrorNotification("Error fetching location information: no data returned");
                }
            } catch (IOException | InterruptedException e) {
                showNotification("Error fetching location information");
                log.error("Error fetching location information", e);
            }  catch (GPSAccessDeniedException e) {
                log.error("Error fetching current location", e);
                showErrorNotification("Unable to fetch location: please enable location services");
            }
        });

        // Configure Form
        binder = new Binder<>(Location.class);

        // Bind fields. This where you'd define e.g. validation rules
        binder.forField(name).asRequired("Mandatory field")
                .withValidator(Objects::nonNull, "Mandatory field").bind(Location::getName, Location::setName);
        binder.forField(latitude).asRequired("Mandatory field")
                .withValidator(Objects::nonNull, "Mandatory field").bind(Location::getLat, Location::setLat);
        binder.forField(longitude).asRequired("Mandatory field")
                .withValidator(Objects::nonNull, "Mandatory field").bind(Location::getLon, Location::setLon);

        binder.setBean(new Location());

        // Configure Grid
        knownLocations = new Grid<>();
        knownLocations.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        knownLocations.setWidthFull();
        knownLocations.addColumn(Location::getName).setHeader("Name");
        knownLocations.addColumn(Location::getLat).setHeader("Latitude");
        knownLocations.addColumn(Location::getLon).setHeader("Longitude");
        knownLocations.addComponentColumn(location -> {
            Button delete = new Button(new Icon(VaadinIcon.CLOSE));
            delete.addClickListener(e -> {
                binder.setBean(new Location());
                if (location != null) {
                    configService.withTransaction(config -> config.getKnownLocations().remove(location));
                    knownLocations.setItems(configService.getConfig().getKnownLocations());
                }
            });
            return delete;
        });

        //when a row is selected or deselected, populate form
        knownLocations.asSingleSelect().addValueChangeListener(event -> populateForm(event.getValue()));

        // the grid valueChangeEvent will clear the form too
        save.addClickListener(e -> {
            BinderValidationStatus<Location> validate = binder.validate();
            if (validate.isOk()) {
                Location location = binder.getBean();
                AddTrustedLocationJob job = new AddTrustedLocationJob(LocalDateTime.now()
                        .plusSeconds(configService.getDelaySeconds()), "Add new location: " + location.getName(),
                        location);
                if (jobRunner.queueJob(job)) {
                    List<Location> items = knownLocations.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                    if (!items.contains(location)) {
                        items.add(location);
                    }
                    knownLocations.setItems(items);
                } else {
                    showNotification("Job queued. The new location will be added after the current delay expires");
                }
                binder.setBean(new Location());
            }
        });

        activate = new Button("Activate Weekend Hall Pass");
        activate.addClickListener(buttonClickEvent -> {
            SetDelayJob job = new SetDelayJob(null, null, Delay.ZERO);
            boolean result = jobRunner.runJob(job);
            if (result) {
                showNotification("Password changed to stock value of " + ConfigService.STOCK_PASSWORD);
                activate.setEnabled(false);
                configService.withTransaction(config -> config.setHallPassUsed(true));
            } else {
                showNotification("Check logs for error");
            }
        });

        toggleStrictMode = new Button();
        toggleStrictMode.addClickListener(e -> configService.withTransaction(config -> {
            try {
                List<String> formerAdminUsers = winUtils.toggleStrictMode(!config.isStrictModeEnabled(),
                        config.getFormerAdminUsers());
                config.setFormerAdminUsers(formerAdminUsers);
                config.setStrictModeEnabled(!config.isStrictModeEnabled());
                if (config.isStrictModeEnabled()) {
                    toggleStrictMode.setText(DISABLE_STRICT_MODE);
                } else {
                    toggleStrictMode.setText(ENABLE_STRICT_MODE);
                }
            } catch (InterruptedException | IOException ex) {
                showNotification("Error enabling strict mode");
            }
        }));

        Tabs sections = new Tabs();
        sections.setWidthFull();
        Map<Tab, Component> tabsToPages = new HashMap<>();

        Tab delayTab = new Tab("Delay & Filter Control");
        Div delayPage = generateDelayPage();
        tabsToPages.put(delayTab, delayPage);

        Tab gpsTab = new Tab("GPS Unlock");
        tabsToPages.put(gpsTab, generateGPSPage());

        VerticalLayout tabActivePage = new VerticalLayout();
        tabActivePage.setPadding(false);
        tabActivePage.add(delayPage);

        sections.add(delayTab, gpsTab);
        sections.setFlexGrowForEnclosedTabs(1);
        sections.addSelectedChangeListener(event -> {
            Component selectedPage = tabsToPages.get(sections.getSelectedTab());
            selectedPage.setVisible(true);
            tabActivePage.removeAll();
            tabActivePage.add(selectedPage);
        });

        add(sections);
        add(tabActivePage);
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.configService = applicationContext.getBean(ConfigService.class);
        this.jobRunner = applicationContext.getBean(JobRunner.class);
        this.winUtils = applicationContext.getBean(WinUtils.class);
    }

    private Div generateDelayPage() {
        Div page = new Div();
        H3 delayTitle = new H3("Delay");
        page.add(delayTitle);
        Label label = new Label("This program is controlled by a delay: you can add a new website at any time but it will only take effect after the current delay. If you set the delay to zero in a moment of weakness but rally and decide not to, abort changes using the button below.");
        page.add(label);
        HorizontalLayout buttonRow = new HorizontalLayout();
        buttonRow.setPadding(false);
        buttonRow.setSpacing(true);
        buttonRow.setWidthFull();
        buttonRow.add(delay, saveDelay, abortDelayChange);
        delay.getElement().getStyle().set("width", "20%");
        saveDelay.getElement().getStyle().set("width", "20%");
        abortDelayChange.getElement().getStyle().set("width", "20%");
        buttonRow.setVerticalComponentAlignment(Alignment.END, saveDelay);
        buttonRow.setVerticalComponentAlignment(Alignment.END, abortDelayChange);
        page.add(buttonRow);

        H3 utility = new H3("Utility");
        page.add(utility);
        Label utilityLabel = new Label("Below are some useful functions for controlling the filter. When delay is zero, you can turn the filter off completely, and it will stay that way until you either turn it back on from this screen, raise the delay from zero, or take some other action to turn it on, such as activating a service from the whitelisting screen");
        page.add(utilityLabel);
        HorizontalLayout utilityButtonRow = new HorizontalLayout();
        page.add(utilityButtonRow);
        utilityButtonRow.add(toggleFilter, restartFilter);
        toggleFilter.getElement().getStyle().set("width", "20%");
        restartFilter.getElement().getStyle().set("width", "20%");
        utilityButtonRow.setVerticalComponentAlignment(Alignment.END, toggleFilter);
        utilityButtonRow.setVerticalComponentAlignment(Alignment.END, restartFilter);

        H3 strictMode = new H3("Strict Mode");
        page.add(strictMode);
        Label strictModeLabel = new Label("The default mode of operation does not guard the config files for skywall. If you are computer savvy enough to find them, you should consider running with strict mode enabled. Strict mode protects the files so you can't access them by removing administrator access for all accounts except the skywall service account. When delay is zero, the skywall account will have a password of " + ConfigService.STOCK_PASSWORD + " and can be used for tasks requiring admin access, but when it is above zero, it will be randomised. You thus effectively become a guest of your own PC: you cannot edit the skywall files, you cannot uninstall skywall, or do any admin tasks without first setting delay to 0 and using the skywall account. Additionally, your PC will restart whenever you raise the delay above 0, to ensure all admin processes such as terminals and command lines you may have had open are closed. You can still whitelist and make changes within the skywall software as normal, subject to the delay.");
        page.add(strictModeLabel);
        page.add(new Html("<br />"));
        toggleStrictMode.getElement().getStyle().set("width", "20%");
        page.add(toggleStrictMode);

        H3 hallPass = new H3("Weekend Hall Pass");
        page.add(hallPass);
        Label hallPassLabel = new Label("On weekends, you can set delay to 0 once for free. Weekends are defined as any time past 5 pm on Friday, and before Monday");
        page.add(hallPassLabel);
        page.add(new Html("<br />"));
        activate.getElement().getStyle().set("width", "20%");
        page.add(activate);

        return page;
    }

    private Div generateGPSPage() {
        Div page = new Div();
        page.setWidthFull();
        H3 gpsUnlockTitle = new H3("GPS-Based Unlocking");
        page.add(gpsUnlockTitle);
        Label info = new Label("Click to get your current location. Then save to add a new trusted location from which you can instantly unlock your device. Simply click the unlock button when you are in the same general area as a trusted location; if you are close enough, delay will automatically be set to 0");
        page.add(info);
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setPadding(false);
        row1.setSpacing(true);
        row1.setClassName("padding-top");
        row1.add(getCurrentLocation, save, unlock);
        getCurrentLocation.getStyle().set("width", "33%");
        save.getStyle().set("width", "33%");
        unlock.getStyle().set("width", "33%");
        row1.setWidthFull();
        page.add(row1);
        HorizontalLayout row2 = new HorizontalLayout();
        row2.setPadding(false);
        row2.setSpacing(true);
        row2.add(name, latitude, longitude);
        name.getStyle().set("width", "33%");
        latitude.getStyle().set("width", "33%");
        longitude.getStyle().set("width", "33%");
        row2.setWidthFull();
        page.add(row2);
        page.add(new Hr());
        page.add(knownLocations);
        return page;
    }

    private boolean inTrustedLocation(Location location) {
        return configService.getConfig().getKnownLocations().stream()
                .anyMatch(loc -> Math.abs(loc.getLon() - location.getLon()) <= 0.0002
                        && Math.abs(loc.getLat() - location.getLat()) <= 0.0002);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        delay.setValue(configService.getCurrentDelay());

        // Lazy init of the grid items, happens only when we are sure the view will be
        // shown to the user
        knownLocations.setItems(configService.getConfig().getKnownLocations());
        toggleFilter.setEnabled(configService.getCurrentDelay() == Delay.ZERO);
        if (configService.getFilterConfig().isFilterActive()) {
            toggleFilter.setText("Turn Filter Off");
        } else {
            toggleFilter.setText("Turn Filter On");
        }
        abortDelayChange.setEnabled(jobRunner.pendingDelayChangeExists());

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        boolean isWeekend = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                .contains(now.getDayOfWeek());

        LocalDateTime fivePMOnFriday = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 17, 0);
        boolean afterFiveOnFriday = EnumSet.of(DayOfWeek.FRIDAY).contains(now.getDayOfWeek()) && now.isAfter(fivePMOnFriday);

        activate.setEnabled(!configService.isHallPassUsed() && (isWeekend || afterFiveOnFriday));

        if (configService.getConfig().isStrictModeEnabled()) {
            toggleStrictMode.setText(DISABLE_STRICT_MODE);
            if (configService.getDelaySeconds() > 0) {
                toggleStrictMode.setEnabled(false);
            }
        } else {
            toggleStrictMode.setText(ENABLE_STRICT_MODE);
            toggleStrictMode.setEnabled(true);
        }
    }

    private void populateForm(Location value) {
        // Value can be null as well, that clears the form, unless the form has validators,
        // in which case a new empty bean is required
        binder.readBean(value == null ? new Location() : value);
    }

    private void showNotification(String message) {
        if (notification != null && notification.isOpened()) {
            notification.close();
        }
        notification = new Notification(message, 5000, Notification.Position.BOTTOM_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        notification.open();
    }

    private void showErrorNotification(String message) {
        if (notification != null && notification.isOpened()) {
            notification.close();
        }
        notification = new Notification(message, 5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.open();
    }
}

