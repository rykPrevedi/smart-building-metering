package it.unimore.dipi.iot.dto;

/**
 * @author Riccardo Prevedi
 * @created 10/09/2022 - 10:02
 * @project coap-smart-building
 */

public class ChangeResourceParameterRequestDescriptor {

    // For any POST request templates
    public static final double LEVEL_LOW = 0.5;

    public static final double LEVEL_MEDIUM = 1.0;

    public static final double LEVEL_HIGH = 2.0;

    private double changedValue;

    public ChangeResourceParameterRequestDescriptor() {
    }

    public ChangeResourceParameterRequestDescriptor(double changedValue) {
        this.changedValue = changedValue;
    }

    public double getChangedValue() {
        return changedValue;
    }

    public void setChangedValue(double changedValue) {
        this.changedValue = changedValue;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ChangeResourceParameterRequestDescriptor{");
        sb.append("changedValue=").append(changedValue);
        sb.append('}');
        return sb.toString();
    }
}
