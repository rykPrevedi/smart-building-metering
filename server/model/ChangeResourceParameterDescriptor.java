package it.unimore.dipi.iot.server.model;

/**
 * @author Riccardo Prevedi
 * @created 10/09/2022 - 11:01
 * @project coap-smart-building
 */

public class ChangeResourceParameterDescriptor {

    //For any POST request templates
    public static final double LEVEL_LOW = 0.5;

    public static final double LEVEL_MEDIUM = 1.0;

    public static final double LEVEL_HIGH = 2.0;

    private double changedValue;

    public ChangeResourceParameterDescriptor() {
    }

    public ChangeResourceParameterDescriptor(double changedValue) {
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
        final StringBuffer sb = new StringBuffer("ChangeResourceParameterDescriptor{");
        sb.append("changedValue=").append(changedValue);
        sb.append('}');
        return sb.toString();
    }
}
