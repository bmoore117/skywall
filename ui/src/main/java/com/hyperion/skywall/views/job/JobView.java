package com.hyperion.skywall.views.job;


import com.hyperion.skywall.backend.model.config.BlockedHost;
import com.hyperion.skywall.backend.model.config.Phrase;
import com.hyperion.skywall.backend.model.config.job.ActivatableJob;
import com.hyperion.skywall.backend.model.config.job.Job;
import com.hyperion.skywall.backend.model.config.service.Service;
import com.hyperion.skywall.backend.services.ConfigService;
import com.hyperion.skywall.backend.services.ConfigUtils;
import com.hyperion.skywall.backend.services.JobRunner;
import com.hyperion.skywall.views.main.MainView;
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

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Collectors;


@Route(value = "job", layout = MainView.class)
@PageTitle("Pending Jobs")
@CssImport(value = "./styles/views/job/job-view.css", include = "lumo-badge")
@JsModule("@vaadin/vaadin-lumo-styles/badge.js")
public class JobView extends VerticalLayout implements AfterNavigationObserver {

    private static final Logger log = LoggerFactory.getLogger(JobView.class);

    private ConfigService configService;
    private JobRunner jobRunner;

    private final Grid<Job> pendingJobs;
    private final DateTimeFormatter formatter;

    @Autowired
    private void setApplicationContext(ApplicationContext applicationContext) {
        this.configService = applicationContext.getBean(ConfigService.class);
        this.jobRunner = applicationContext.getBean(JobRunner.class);
    }

    public JobView() {
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a");
        setSizeFull();
        setPadding(true);
        pendingJobs = new Grid<>();

        add(new H3("Pending Jobs"));
        add(new Label("View pending jobs and their launch times here. Cancel if desired"));
        pendingJobs.setSizeFull();
        pendingJobs.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        initPendingJobs();
        add(pendingJobs);
    }

    private void initPendingJobs() {
        pendingJobs.addColumn(Job::getJobDescription).setHeader("Job");
        pendingJobs.addColumn(job -> formatter.format(job.getJobLaunchTime())).setHeader("Launch Time");
        pendingJobs.addComponentColumn(job -> {
            Button cancel = new Button("Cancel");
            cancel.addClickListener(e -> {
                try {
                    Class<?> jobConcreteClass = Class.forName(job.getConcreteClass());
                    if (ActivatableJob.class.isAssignableFrom(jobConcreteClass)) {
                        ActivatableJob converted = (ActivatableJob) JobRunner.convertIfNecessary(job);
                        Class<?> activatableClass = converted.getActivatableClass();
                        configService.withTransaction(config -> {
                            if (Service.class.equals(activatableClass)) {
                                ConfigUtils.findServiceById(config, converted.getActivatableId())
                                        .ifPresent(service -> service.updateCurrentActivationStatus(service.getLastActivationStatus()));
                            } else if (BlockedHost.class.equals(activatableClass)) {
                                ConfigUtils.findHostById(config, converted.getActivatableId())
                                        .ifPresent(obj -> obj.updateCurrentActivationStatus(obj.getLastActivationStatus()));
                            } else if (Phrase.class.equals(activatableClass)) {
                                ConfigUtils.findPhraseById(config, converted.getActivatableId())
                                        .ifPresent(obj -> obj.updateCurrentActivationStatus(obj.getLastActivationStatus()));
                            }
                        });
                    }
                    jobRunner.cancelJobById(job.getId());
                    doAfterNavigation();
                } catch (ClassNotFoundException ignored) {}
            });
            return cancel;
        });
    }

    @Override
    public void afterNavigation(AfterNavigationEvent afterNavigationEvent) {
        doAfterNavigation();
    }

    private void doAfterNavigation() {
        pendingJobs.setItems(configService.getConfig().getPendingJobs().stream()
                .sorted(Comparator.comparing(Job::getJobLaunchTime)).collect(Collectors.toList()));
    }
}

