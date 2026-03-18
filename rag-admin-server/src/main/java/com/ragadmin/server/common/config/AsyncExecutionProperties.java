package com.ragadmin.server.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.async")
public class AsyncExecutionProperties {

    private final Application application = new Application();
    private final Scheduler scheduler = new Scheduler();

    public Application getApplication() {
        return application;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public static class Application {

        private int coreSize = 8;
        private int maxSize = 16;
        private int queueCapacity = 200;
        private int keepAliveSeconds = 60;

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }
    }

    public static class Scheduler {

        private int poolSize = 1;

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }
}
