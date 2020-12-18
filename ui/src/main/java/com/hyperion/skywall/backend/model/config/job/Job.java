package com.hyperion.skywall.backend.model.config.job;

import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Job {

    private LocalDateTime jobLaunchTime;
    private String jobDescription;
    private String concreteClass;
    private UUID id;
    protected Map<String, Object> data;

    public Job() {
        data = new HashMap<>();
        concreteClass = getClass().getCanonicalName();
        id = UUID.randomUUID();
    }

    public Job(LocalDateTime jobLaunchTime, String jobDescription) {
        this();
        this.jobLaunchTime = jobLaunchTime;
        this.jobDescription = jobDescription;
    }

    public LocalDateTime getJobLaunchTime() {
        return jobLaunchTime;
    }

    public String getJobDescription() {
        return jobDescription;
    }

    public void setJobLaunchTime(LocalDateTime jobLaunchTime) {
        this.jobLaunchTime = jobLaunchTime;
    }

    public void setJobDescription(String jobDescription) {
        this.jobDescription = jobDescription;
    }

    protected <T> T get(String key, Class<T> tClass) {
        return tClass.cast(data.get(key));
    }

    public String getConcreteClass() {
        return concreteClass;
    }

    public void setConcreteClass(String concreteClass) {
        this.concreteClass = concreteClass;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return Objects.equals(id, job.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Job dehydrateJob() {
        Job job = new Job(getJobLaunchTime(), getJobDescription());
        job.setConcreteClass(getConcreteClass());
        job.setId(getId());
        job.setData(getData());
        return job;
    }

    public Boolean call(ApplicationContext applicationContext) {
        throw new UnsupportedOperationException("Concrete classes contain the actual logic of interest and are what should be run");
    }
}
