package it.unimore.dipi.iot.server.raw;

import it.unimore.dipi.iot.server.model.GenericCounterConfigurationModel;

import java.util.UUID;

/**
 * @author Riccardo Prevedi
 * @created 09/09/2022 - 12:14
 * @project coap-smart-building
 */

public class ProviderRawConfigurationParameter extends SmartObjectResource<GenericCounterConfigurationModel> {

    private GenericCounterConfigurationModel genericCounterConfigurationModel;

    private static final String RESOURCE_TYPE = "iot.config.provider";

    public ProviderRawConfigurationParameter(GenericCounterConfigurationModel genericCounterConfigurationModel) {
        super(UUID.randomUUID().toString(), RESOURCE_TYPE);
        this.genericCounterConfigurationModel = genericCounterConfigurationModel;
    }

    @Override
    public GenericCounterConfigurationModel loadUpdatedValue() {
        return genericCounterConfigurationModel;
    }

    public GenericCounterConfigurationModel getGenericCounterConfigurationModel() {
        return genericCounterConfigurationModel;
    }

    public void setGenericCounterConfigurationModel(GenericCounterConfigurationModel genericCounterConfigurationModel) {
        this.genericCounterConfigurationModel = genericCounterConfigurationModel;
        notifyUpdate(genericCounterConfigurationModel);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ProviderRawConfigurationParameter{");
        sb.append("genericCounterConfigurationModel=").append(genericCounterConfigurationModel);
        sb.append('}');
        return sb.toString();
    }
}
