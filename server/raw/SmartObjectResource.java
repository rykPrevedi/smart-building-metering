package it.unimore.dipi.iot.server.raw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a prototype for every sensor type
 *
 * @author Riccardo Prevedi
 * @created 28/08/2022 - 14:50
 * @project coap-smart-building
 */


public abstract class SmartObjectResource<T> {

    private final static Logger logger = LoggerFactory.getLogger(SmartObjectResource.class);

    protected List<ResourceDataListener<T>> resourceListenerList;

    private String deviceId;

    private String type;

    public SmartObjectResource() {
        this.resourceListenerList = new ArrayList<>();
    }

    public SmartObjectResource(String deviceId, String type) {
        this.deviceId = deviceId;
        this.type = type;
        this.resourceListenerList = new ArrayList<>();
    }

    public abstract T loadUpdatedValue();

    public void addDataListener(ResourceDataListener<T> resourceDataListener) {
        if (this.resourceListenerList != null)
            this.resourceListenerList.add(resourceDataListener);
    }

    public void removeDataListener(ResourceDataListener<T> resourceDataListener) {
        if (this.resourceListenerList != null && this.resourceListenerList.contains(resourceDataListener))
            this.resourceListenerList.remove(resourceDataListener);
    }

    public void notifyUpdate(T updatedValue) {
        if (this.resourceListenerList != null && this.resourceListenerList.size() > 0) {
            this.resourceListenerList.forEach(listener -> {
                if (listener != null)
                    listener.onDataChanged(this, updatedValue);
            });
        } else
            logger.error("Empty or Null Resource Data Listener ! Nothing to notify ...");
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SmartObjectResource{");
        sb.append("resourceListenerList=").append(resourceListenerList);
        sb.append(", deviceId='").append(deviceId).append('\'');
        sb.append(", type='").append(type).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
