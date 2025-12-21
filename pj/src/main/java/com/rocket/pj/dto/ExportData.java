package com.rocket.pj.dto;

import com.rocket.pj.entity.Client;
import com.rocket.pj.entity.SystemConfig;
import java.util.List;

public class ExportData {
    private List<Client> clients;
    private SystemConfig systemConfig;
    private String serverPrivateKey;

    public ExportData() {
    }

    public ExportData(List<Client> clients, SystemConfig systemConfig, String serverPrivateKey) {
        this.clients = clients;
        this.systemConfig = systemConfig;
        this.serverPrivateKey = serverPrivateKey;
    }

    public List<Client> getClients() {
        return clients;
    }

    public void setClients(List<Client> clients) {
        this.clients = clients;
    }

    public SystemConfig getSystemConfig() {
        return systemConfig;
    }

    public void setSystemConfig(SystemConfig systemConfig) {
        this.systemConfig = systemConfig;
    }

    public String getServerPrivateKey() {
        return serverPrivateKey;
    }

    public void setServerPrivateKey(String serverPrivateKey) {
        this.serverPrivateKey = serverPrivateKey;
    }
}
