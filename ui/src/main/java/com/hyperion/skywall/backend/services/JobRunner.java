package com.hyperion.skywall.backend.services;

import com.hyperion.skywall.backend.model.config.Delay;
import com.hyperion.skywall.backend.model.config.JobConstants;
import com.hyperion.skywall.backend.model.config.job.ActivateServiceJob;
import com.hyperion.skywall.backend.model.config.job.Job;
import com.hyperion.skywall.backend.model.config.job.SetDelayJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    public static final String TOGGLE_INTERNET_OFF = "Toggle internet off";
    private final ApplicationContext applicationContext;

    private final ConfigService configService;
    private final WinUtils winUtils;

    private static List<CustomTimerTask> tasks = new LinkedList<>();

    private ActivateServiceJob lastActivateServiceJob;

    @Autowired
    public JobRunner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.configService = applicationContext.getBean(ConfigService.class);
        this.winUtils = applicationContext.getBean(WinUtils.class);
    }

    class CustomTimerTask extends TimerTask {

        private final LocalDateTime runAt;
        private final String jobDescription;
        private final Class<?> jobClass;
        private final UUID jobId;

        public CustomTimerTask(LocalDateTime runAt, String jobDescription, Class<?> jobClass, UUID jobId) {
            this.runAt = runAt;
            this.jobDescription = jobDescription;
            this.jobClass = jobClass;
            this.jobId = jobId;
        }

        public LocalDateTime getRunAt() {
            return runAt;
        }

        public String getJobDescription() {
            return jobDescription;
        }

        public Class<?> getJobClass() {
            return jobClass;
        }

        public UUID getJobId() {
            return jobId;
        }

        @Override
        public void run() {
            // this is added so we don't block the Timer thread, since runReadyJobs may take a while to complete and
            // is synchronized
            CompletableFuture.runAsync(() -> {
                runReadyJobs();
                tasks.remove(this);
            });
        }
    }

    public void onWake() {
        log.info("Adjusting job timers in onWake");
        List<CustomTimerTask> remainingTasks = new LinkedList<>();
        for (CustomTimerTask task : tasks) {
            task.cancel(); // all existing timers are invalid, time has shifted under them by however much we slept for
            if (task.getRunAt().isAfter(LocalDateTime.now())) {
                CustomTimerTask newTask = new CustomTimerTask(task.getRunAt(), task.getJobDescription(), task.getJobClass(), task.getJobId());
                remainingTasks.add(newTask);
                long epochMilli = task.getRunAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long timerDuration = epochMilli - System.currentTimeMillis();
                Timer timer = new Timer(task.getJobDescription());
                log.info("Task {} scheduled to be run at {}, rescheduling with timer of {}ms", task.getJobDescription(), task.getRunAt(), timerDuration);
                timer.schedule(newTask, timerDuration);
            }
        }
        tasks = remainingTasks;
        runReadyJobs();
    }

    public boolean queueJob(Job job) {
        if (configService.getDelaySeconds() == 0) {
            runJob(job);
            return true;
        }
        else {
            queueJobInternal(job, true);
            return false;
        }
    }

    private void queueJobInternal(Job job, boolean writeFile) {
        log.info("Scheduling job: {}", job.getJobDescription());
        // if re-queueing job, it is already in pending jobs, do not re-add
        if (!configService.getConfig().getPendingJobs().contains(job)) {
            if (ActivateServiceJob.class.getName().equals(job.getConcreteClass())) {
                // used in whitelist view
                lastActivateServiceJob = (ActivateServiceJob) job;
            }
            configService.getConfig().getPendingJobs().add(job.dehydrateJob());
            if (writeFile) {
                configService.writeFile();
            }
        }
        CustomTimerTask task = new CustomTimerTask(job.getJobLaunchTime(), job.getJobDescription(), job.getClass(), job.getId());
        Timer timer = new Timer(job.getJobDescription());
        long epochMilli = task.getRunAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long timerDuration = epochMilli - System.currentTimeMillis();
        if (timerDuration < 0) {
            timerDuration = 0;
        }
        tasks.add(task);
        timer.schedule(task, timerDuration);
    }

    public void requeuePendingJobs() {
        for (Job job : configService.getConfig().getPendingJobs()) {
            if (LocalDateTime.now().isBefore(job.getJobLaunchTime())) {
                queueJobInternal(job, false);
            }
        }
        runReadyJobs();
    }

    /*
        Of note, if other jobs become ready while the current set of ready jobs is still running, we expect to still
        catch it because of the fact that there will be blocking threads calling this method waiting in line for their
        turn to run it, due to the job-timer association
     */
    public synchronized void runReadyJobs() {
        retryFailedJobsInternal(false);

        List<Job> jobs = configService.getConfig().getPendingJobs().stream()
                .filter(job -> !job.getJobLaunchTime().isAfter(LocalDateTime.now()))
                .collect(Collectors.toList());

        if (!jobs.isEmpty()) {
            configService.withTransaction(config -> {
                for (Job job : jobs) {
                    if (!runJob(job)) {
                        if (!config.getRetryJobs().contains(job)) {
                            config.getRetryJobs().add(job);
                        }
                    }

                    // this is safe despite being in a for loop because it's not the collection we are iterating over
                    config.getPendingJobs().remove(job);
                }
            });
            configService.reloadConfig();
        }
    }

    public void retryFailedJobs() {
        retryFailedJobsInternal(true);
    }

    public void deleteFailedJobs() {
        configService.withTransaction(config -> config.getRetryJobs().clear());
    }

    public void cancelJobById(UUID jobId) {
        log.info("Canceling job: {}", jobId);
        Iterator<CustomTimerTask> it = tasks.iterator();
        while (it.hasNext()) {
            CustomTimerTask task = it.next();
            if (!task.getJobId().equals(jobId)) {
                task.cancel();
                it.remove();
            }
        }
        configService.withTransaction(config -> config.setPendingJobs(config.getPendingJobs().stream()
                .filter(job -> !job.getId().equals(jobId)).collect(Collectors.toList())));
    }

    public void cancelPendingJobsForActivatable(UUID uuid) {
        log.info("Canceling job for activatable: {}", uuid);
        Set<UUID> jobIds = new HashSet<>();
        configService.withTransaction(config -> {
            Iterator<Job> it = config.getPendingJobs().iterator();
            while (it.hasNext()) {
                Job job = it.next();
                if (uuid.equals(job.getData().get(JobConstants.ACTIVATABLE_ID))) {
                    jobIds.add(job.getId());
                    it.remove();
                }
            }
        });
        Iterator<CustomTimerTask> it = tasks.iterator();
        while (it.hasNext()) {
            CustomTimerTask task = it.next();
            if (jobIds.contains(task.getJobId())) {
                task.cancel();
                it.remove();
            }
        }
    }

    public void cancelPendingJobs() {
        log.info("Canceling all jobs");
        Iterator<CustomTimerTask> it = tasks.iterator();
        List<UUID> jobIds = new LinkedList<>();
        while (it.hasNext()) {
            CustomTimerTask task = it.next();
            if (!task.getJobDescription().equals(TOGGLE_INTERNET_OFF)) {
                task.cancel();
                jobIds.add(task.getJobId());
                it.remove();
            }
        }
        configService.withTransaction(config -> jobIds.forEach(id -> configService.getConfig().getPendingJobs()
                .removeIf(job -> job.getId().equals(id))));
    }

    public boolean cancelSetDelayJobs() {
        log.info("Canceling set delay jobs");
        Iterator<CustomTimerTask> it = tasks.iterator();
        List<UUID> jobIds = new LinkedList<>();
        while (it.hasNext()) {
            CustomTimerTask task = it.next();
            if (task.getJobClass().equals(SetDelayJob.class)) {
                task.cancel();
                jobIds.add(task.getJobId());
                it.remove();
            }
        }
        if (jobIds.isEmpty()) {
            return false;
        } else {
            configService.withTransaction(config -> jobIds.forEach(id -> configService.getConfig().getPendingJobs()
                    .removeIf(job -> job.getId().equals(id))));
            return true;
        }
    }

    public boolean pendingDelayChangeExists() {
        return configService.getConfig().getPendingJobs().stream().anyMatch(job -> SetDelayJob.class.getName().equals(job.getConcreteClass()));
    }

    public boolean cancelPendingServiceActivations() {
        Iterator<CustomTimerTask> it = tasks.iterator();
        List<UUID> jobIds = new LinkedList<>();
        while (it.hasNext()) {
            CustomTimerTask task = it.next();
            if (task.getJobClass().equals(ActivateServiceJob.class)) {
                task.cancel();
                jobIds.add(task.getJobId());
                it.remove();
            }
        }
        if (jobIds.isEmpty()) {
            return false;
        } else {
            configService.withTransaction(config -> jobIds.forEach(id -> configService.getConfig().getPendingJobs()
                    .removeIf(job -> job.getId().equals(id))));
            return true;
        }
    }

    public void cancelLastPendingServiceActivation() {
        if (lastActivateServiceJob == null) {
            return;
        }
        Iterator<CustomTimerTask> it = tasks.iterator();
        List<UUID> jobIds = new LinkedList<>();
        while (it.hasNext()) {
            CustomTimerTask task = it.next();
            if (task.getJobId().equals(lastActivateServiceJob.getId())) {
                task.cancel();
                jobIds.add(task.getJobId());
                it.remove();
            }
        }
        configService.withTransaction(config -> jobIds.forEach(id -> configService.getConfig().getPendingJobs()
                .removeIf(job -> job.getId().equals(id))));
    }

    private void retryFailedJobsInternal(boolean writeFile) {
        Iterator<Job> it = configService.getConfig().getRetryJobs().iterator();
        while (it.hasNext()) {
            Job job = it.next();
            log.info("Retrying previously errored job {}", job.getJobDescription());
            RuntimeException e = null;
            boolean result = true;
            try {
                result = runJob(job);
            } catch (RuntimeException ex) {
                e = ex;
                log.error("Retry of job failed, leaving in retry queue", e);
            }

            if (e == null && result) {
                log.info("Job succeeded, removing from queue");
                it.remove();
            }
        }

        if (writeFile) {
            configService.writeFile();
        }
    }

    @SuppressWarnings("unchecked")
    public static Job convertIfNecessary(Job incoming) {
        if (incoming == null) {
            return null;
        }

        if (incoming.getClass().equals(Job.class)) {
            // if this is the case then this class has been rehydrated from concentrate, and needs to be cast
            try {
                Class<? extends Job> targetClass = (Class<? extends Job>) Class.forName(incoming.getConcreteClass());
                Constructor<? extends Job> constructor = targetClass.getConstructor();
                Job job = constructor.newInstance();
                job.setJobDescription(incoming.getJobDescription());
                job.setJobLaunchTime(incoming.getJobLaunchTime());
                job.setId(incoming.getId());
                job.setConcreteClass(incoming.getConcreteClass());
                job.setData(incoming.getData());
                return job;
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
                log.error("This should not have happened, does your job class follow the established pattern?", e);
                return null;
            } catch (RuntimeException e) {
                log.error("Something funny happened", e);
                return null;
            }
        } else {
            return incoming;
        }
    }

    public boolean runJob(Job incoming) {
        boolean returnVal = false;
        log.info("Starting job {}", incoming.getJobDescription());
        Job job = convertIfNecessary(incoming);
        try {
            Boolean result = job.call(applicationContext);
            if (result != null && result) {
                returnVal = true;
                log.info("Job {} completed successfully", job.getJobDescription());
            } else {
                log.error("Job {} did not complete successfully", job.getJobDescription());
            }
        } catch (RuntimeException e) {
            log.error("Error running job", e);
        }
        return returnVal;
    }

    public int getActiveTimerTaskCount() {
        return tasks.size();
    }

    public void resetHallPassForTheWeekIfEligible() {
        log.info("Entering resetHallPassForTheWeek");

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        boolean isWeekend = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                .contains(now.getDayOfWeek());

        LocalDateTime fivePMOnFriday = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 17, 0);
        boolean afterFiveOnFriday = EnumSet.of(DayOfWeek.FRIDAY).contains(now.getDayOfWeek()) && now.isAfter(fivePMOnFriday);

        if (!(isWeekend || afterFiveOnFriday) && configService.isHallPassUsed()) {
            // we're locking back up for the week after a hall pass activation
            log.info("Locking back up for the week after hall pass activation");
            configService.withTransaction(config -> {
                config.setHallPassUsed(false);
            });
            SetDelayJob job = new SetDelayJob(null, null, Delay.TWO_HOURS);
            runJob(job);
        }
    }
}
