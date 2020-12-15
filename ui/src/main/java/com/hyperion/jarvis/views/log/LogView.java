package com.hyperion.jarvis.views.log;


import com.hyperion.jarvis.backend.model.filter.LogEntry;
import com.hyperion.jarvis.backend.services.LogService;
import com.hyperion.jarvis.views.main.MainView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;


@Route(value = "log", layout = MainView.class)
@PageTitle("Certificate Warnings")
@CssImport(value = "./styles/views/log/log-view.css", include = "lumo-badge")
@JsModule("@vaadin/vaadin-lumo-styles/badge.js")
public class LogView extends VerticalLayout implements AfterNavigationObserver {

    private static final Logger log = LoggerFactory.getLogger(LogView.class);

    private LogService logService;

    private final Grid<LogEntry> certificateWarnings;

    @Autowired
    private void setApplicationContext(ApplicationContext applicationContext) {
        logService = applicationContext.getBean(LogService.class);
    }

    public LogView() {
        setSizeFull();
        setPadding(true);
        certificateWarnings = new Grid<>();

        add(new H3("Certificate Warnings"));
        add(new Label("Here you can see some of the activity of the filter behind the scenes. This screen is provided to help you get an idea of when it may be appropriate to use the passthrough filter mode on the Apps & Websites page. If you added a site on that page using standard mode, but it isn't working and you see a certificate warning for that same site here, try changing the filter mode to passthrough. However, browsers will often renegotiate the connection despite the warning, and succeed in reaching the site, making these warnings redundant. Non-browser applications such as games or other programs typically will not do so; if the host corresponds to some non-browser application traffic it usually does mean the connection failed. Keep in mind that if you whitelist a site using passthrough mode, its traffic will not go through the filter at all and images or video referenced on that site from different domains will not be detected and allowed through without specifying additional manual rules."));
        Button refresh = new Button("Refresh");
        refresh.addClickListener(e -> {
           doAfterNavigation();
        });
        add(refresh);
        certificateWarnings.setSizeFull();
        certificateWarnings.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        initPendingJobs();
        add(certificateWarnings);
    }

    private void initPendingJobs() {
        certificateWarnings.addColumn(LogEntry::getHost).setHeader("Message (later messages are displayed last)");
    }

    @Override
    public void afterNavigation(AfterNavigationEvent afterNavigationEvent) {
        doAfterNavigation();
    }

    private void doAfterNavigation() {
        certificateWarnings.setItems(logService.getLogEntries());
    }
}
