package io.stargate.sdk.rest;

import java.util.ArrayList;
import java.util.List;

public class TableOptions {
    
    /*
     * Defines the Time To Live (TTL), which determines the time period (in seconds) 
     * to expire data. If the value is >0, TTL is enabled for the entire table and an
     *  expiration timestamp is added to each column. The maximum value is 630720000 
     * (20 years). A new TTL timestamp is calculated each time the data is updated 
     * and the row is removed after the data expires.
     */
    private int defaultTimeToLive = 0;
    
    private List<ClusteringExpression> clusteringExpression = new ArrayList<>();
    
    
    
   

    /**
     * Getter accessor for attribute 'defaultTimeToLive'.
     *
     * @return
     *       current value of 'defaultTimeToLive'
     */
    public int getDefaultTimeToLive() {
        return defaultTimeToLive;
    }

    /**
     * Setter accessor for attribute 'defaultTimeToLive'.
     * @param defaultTimeToLive
     * 		new value for 'defaultTimeToLive '
     */
    public void setDefaultTimeToLive(int defaultTimeToLive) {
        this.defaultTimeToLive = defaultTimeToLive;
    }

    /**
     * Getter accessor for attribute 'clusteringExpression'.
     *
     * @return
     *       current value of 'clusteringExpression'
     */
    public List<ClusteringExpression> getClusteringExpression() {
        return clusteringExpression;
    }

    /**
     * Setter accessor for attribute 'clusteringExpression'.
     * @param clusteringExpression
     * 		new value for 'clusteringExpression '
     */
    public void setClusteringExpression(List<ClusteringExpression> clusteringExpression) {
        this.clusteringExpression = clusteringExpression;
    }
}
