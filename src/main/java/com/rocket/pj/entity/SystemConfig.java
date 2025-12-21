package com.rocket.pj.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    private Long id = 1L; // Singleton config

    private String wgInterfaceAddress;
    private String clientSubnetPrefix;
    private String clientDns;
    private int wgPort;
    private String endpointHost;
    private boolean isSetupComplete;

    public SystemConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWgInterfaceAddress() {
        return wgInterfaceAddress;
    }

    public void setWgInterfaceAddress(String wgInterfaceAddress) {
        this.wgInterfaceAddress = wgInterfaceAddress;
    }

    public String getClientSubnetPrefix() {
        return clientSubnetPrefix;
    }

    public void setClientSubnetPrefix(String clientSubnetPrefix) {
        this.clientSubnetPrefix = clientSubnetPrefix;
    }

    public String getClientDns() {
        return clientDns;
    }

    public void setClientDns(String clientDns) {
        this.clientDns = clientDns;
    }

    public int getWgPort() {
        return wgPort;
    }

    public void setWgPort(int wgPort) {
        this.wgPort = wgPort;
    }

    public String getEndpointHost() {
        return endpointHost;
    }

    public void setEndpointHost(String endpointHost) {
        this.endpointHost = endpointHost;
    }

    public boolean isSetupComplete() {
        return isSetupComplete;
    }

    public void setSetupComplete(boolean setupComplete) {
        isSetupComplete = setupComplete;
    }
}
