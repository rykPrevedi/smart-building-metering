package it.unimore.dipi.iot.server.model;

/**
 * @author Riccardo Prevedi
 * @created 09/09/2022 - 11:49
 * @project coap-smart-building
 */

public abstract class GenericCounterConfigurationModel {

    private double maxValueConsumption;

    private String rawMaterialMeasureUnit;


    public GenericCounterConfigurationModel() {
    }

    public GenericCounterConfigurationModel(double maxValueConsumption, String rawMaterialMeasureUnit) {
        this.maxValueConsumption = maxValueConsumption;
        this.rawMaterialMeasureUnit = rawMaterialMeasureUnit;
    }

    public double getMaxValueConsumption() {
        return maxValueConsumption;
    }

    public void setMaxValueConsumption(double maxValueConsumption) {
        this.maxValueConsumption = maxValueConsumption;
    }

    public String getRawMaterialMeasureUnit() {
        return rawMaterialMeasureUnit;
    }

    public void setRawMaterialMeasureUnit(String rawMaterialMeasureUnit) {
        this.rawMaterialMeasureUnit = rawMaterialMeasureUnit;
    }


    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("GenericCounterConfigurationModel{");
        sb.append("maxValueConsumption=").append(maxValueConsumption);
        sb.append(", rawMaterialMeasureUnit='").append(rawMaterialMeasureUnit).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
