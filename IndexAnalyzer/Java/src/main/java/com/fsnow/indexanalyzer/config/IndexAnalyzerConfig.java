package com.fsnow.indexanalyzer.config;

/**
 * Configuration for IndexAnalyzer with caching options.
 * Use the builder pattern to create instances.
 */
public final class IndexAnalyzerConfig {
    
    private final boolean cacheEnabled;
    private final int cacheTTLMinutes;
    private final int connectionTimeoutMs;
    
    private IndexAnalyzerConfig(Builder builder) {
        this.cacheEnabled = builder.cacheEnabled;
        this.cacheTTLMinutes = builder.cacheTTLMinutes;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
    }
    
    /**
     * Creates a new builder for IndexAnalyzerConfig.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a default configuration with caching disabled.
     * 
     * @return A default configuration
     */
    public static IndexAnalyzerConfig defaultConfig() {
        return new Builder().build();
    }
    
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    public int getCacheTTLMinutes() {
        return cacheTTLMinutes;
    }
    
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
    
    /**
     * Builder for IndexAnalyzerConfig.
     */
    public static class Builder {
        private boolean cacheEnabled = false;
        private int cacheTTLMinutes = 5;
        private int connectionTimeoutMs = 30000;
        
        private Builder() {}
        
        /**
         * Enables or disables caching.
         * Default: false
         * 
         * @param enabled true to enable caching
         * @return this builder
         */
        public Builder cacheEnabled(boolean enabled) {
            this.cacheEnabled = enabled;
            return this;
        }
        
        /**
         * Sets the cache TTL (time-to-live) in minutes.
         * Default: 5 minutes
         * 
         * @param minutes The TTL in minutes (must be positive)
         * @return this builder
         */
        public Builder cacheTTLMinutes(int minutes) {
            if (minutes <= 0) {
                throw new IllegalArgumentException("Cache TTL must be positive");
            }
            this.cacheTTLMinutes = minutes;
            return this;
        }
        
        /**
         * Sets the MongoDB connection timeout in milliseconds.
         * Default: 30000 (30 seconds)
         * 
         * @param timeoutMs The timeout in milliseconds
         * @return this builder
         */
        public Builder connectionTimeoutMs(int timeoutMs) {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("Connection timeout must be positive");
            }
            this.connectionTimeoutMs = timeoutMs;
            return this;
        }
        
        /**
         * Builds the configuration.
         * 
         * @return A new IndexAnalyzerConfig instance
         */
        public IndexAnalyzerConfig build() {
            return new IndexAnalyzerConfig(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("IndexAnalyzerConfig{cacheEnabled=%s, cacheTTLMinutes=%d, " +
                           "connectionTimeoutMs=%d}",
                           cacheEnabled, cacheTTLMinutes, 
                           connectionTimeoutMs);
    }
}