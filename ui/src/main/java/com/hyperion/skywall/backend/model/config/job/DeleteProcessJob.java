package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.model.config.ActivationStatus;
import com.hyperion.skywall.backend.model.config.JobConstants;
import com.hyperion.skywall.backend.model.config.Process;
import com.hyperion.skywall.backend.services.ConfigService;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

public class DeleteProcessJob extends Job implements ActivatableJob {

    public DeleteProcessJob() {}

    public DeleteProcessJob(LocalDateTime jobLaunchTime, String jobDescription, Process process) {
        super(jobLaunchTime, jobDescription);
        data.put(JobConstants.ACTIVATABLE_ID, process.getId());
        data.put(JobConstants.ACTIVATABLE_OBJECT_VAL, process.getProcess());
    }

    @Override
    public Class<?> getActivatableClass() {
        return Process.class;
    }

    @Override
    public UUID getActivatableId() {
        Object val = data.get(JobConstants.ACTIVATABLE_ID);
        if (val instanceof String) {
            return UUID.fromString((String) val);
        }
        return (UUID) val;
    }

    public ActivationStatus getNewStatus() {
        Object val = data.get(JobConstants.NEW_ACTIVATION_STATUS);
        if (val instanceof String) {
            return ActivationStatus.valueOf((String) val);
        }
        return (ActivationStatus) val;
    }

    public String getProcessName() {
        return (String) data.get(JobConstants.ACTIVATABLE_OBJECT_VAL);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        ConfigService configService = applicationContext.getBean(ConfigService.class);

        configService.withTransaction(config -> {
            config.setTrustedProcesses(config.getTrustedProcesses().stream().filter(process -> !process.getProcess().equals(getProcessName())).collect(Collectors.toList()));
            configService.withProcessTransaction(processes -> processes.getProcesses().remove(getProcessName()));
        });

        return true;
    }
}
