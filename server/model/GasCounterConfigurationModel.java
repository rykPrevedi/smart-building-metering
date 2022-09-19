package it.unimore.dipi.iot.server.model;

/**
 * @author Riccardo Prevedi
 * @created 09/09/2022 - 11:57
 * @project coap-smart-building
 */

public class GasCounterConfigurationModel extends GenericCounterConfigurationModel{

    private static final double MAX_GAS_CONSUMPTION = 0.02;

    private static final String MEASURE_UNIT =  "m3/s";

    public GasCounterConfigurationModel() {
        super(MAX_GAS_CONSUMPTION, MEASURE_UNIT);
    }

    public GasCounterConfigurationModel(double maxValueConsumption, String providerSwitchResourceUri) {
        super(maxValueConsumption, providerSwitchResourceUri);
    }
}
