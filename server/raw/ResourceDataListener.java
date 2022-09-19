package it.unimore.dipi.iot.server.raw;

/**
 * @author Riccardo Prevedi
 * @created 28/08/2022 - 14:48
 * @project coap-smart-building
 */

@FunctionalInterface
public interface ResourceDataListener<T> {
    public void onDataChanged(SmartObjectResource<T> resource, T updatedValue);
}
