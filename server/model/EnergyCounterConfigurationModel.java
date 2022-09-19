package it.unimore.dipi.iot.server.model;

/**
 * @author Riccardo Prevedi
 * @created 09/09/2022 - 11:57
 * @project coap-smart-building
 */

public class EnergyCounterConfigurationModel extends GenericCounterConfigurationModel{

    private static final double MAX_ENERGY_CONSUMPTION = 2.0;

    private static final String MEASURE_UNIT =  "kWh";

    public EnergyCounterConfigurationModel() {
        super(MAX_ENERGY_CONSUMPTION, MEASURE_UNIT);
    }

    public EnergyCounterConfigurationModel(double maxValueConsumption, String providerSwitchResourceUri) {
        super(maxValueConsumption, providerSwitchResourceUri);
    }
}
