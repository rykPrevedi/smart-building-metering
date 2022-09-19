package it.unimore.dipi.iot.server.model;

/**
 * @author Riccardo Prevedi
 * @created 09/09/2022 - 11:57
 * @project coap-smart-building
 */

public class WaterCounterConfigurationModel extends GenericCounterConfigurationModel{

    private static final double MAX_WATER_CONSUMPTION = 2.0;

    private static final String MEASURE_UNIT =  "l/S";

    public WaterCounterConfigurationModel() {
        super(MAX_WATER_CONSUMPTION, MEASURE_UNIT);
    }

    public WaterCounterConfigurationModel(double maxValueConsumption, String providerSwitchResourceUri) {
        super(maxValueConsumption, providerSwitchResourceUri);
    }
}
