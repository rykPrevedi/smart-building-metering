package it.unimore.dipi.iot.server.raw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * @author Riccardo Prevedi
 * @created 29/08/2022 - 16:28
 * @project coap-smart-building
 */

public class SwitchRawActuator extends SmartObjectResource<Boolean>{

    private static final Logger logger = LoggerFactory.getLogger(SwitchRawActuator.class);

    private static final String LOG_DISPLAY_NAME = "SwitchActuator";

    private static final String RESOURCE_TYPE = "iot.actuator.switch";

    private Boolean isActive;

    public SwitchRawActuator() {
        super(UUID.randomUUID().toString(), RESOURCE_TYPE);
        this.isActive = true;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
        notifyUpdate(isActive);
    }

    @Override
    public Boolean loadUpdatedValue() {
        return isActive;
    }
}
