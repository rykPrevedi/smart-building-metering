package it.unimore.dipi.iot.server.raw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * This is the water consumption raw data generator.
 * The original water consumption value is generated in the range 0.1 and 0.5 l/s
 * The sensor values are updated every 5 sec
 *
 * @author Riccardo Prevedi
 * @created 28/08/2022 - 15:33
 * @project coap-smart-building
 */

public class WaterRawSensor extends SmartObjectResource<Double> {

    private static final Logger logger = LoggerFactory.getLogger(WaterRawSensor.class);

    private static final double MAX_WATER_CONSUMPTION_VALUE = 0.5;

    private static final double MIN_WATER_CONSUMPTION_VALUE = 0.1;

    private static final double MAX_WATER_CONSUMPTION_VARIATION = 0.5;

    private static final double MIN_WATER_CONSUMPTION_VARIATION = 0.1;

    public static final long UPDATE_PERIOD = 5000;

    private static final long TASK_DELAY_TIME = 5000;

    private static final String RESOURCE_TYPE = "iot.sensor.water";

    private static final String LOG_DISPLAY_NAME = "WaterConsumptionSensor";

    private Double waterConsumptionValue;

    private Boolean isActive;

    private Random random = null;

    private Timer updateTimer;


    public WaterRawSensor() {
        super(UUID.randomUUID().toString(), RESOURCE_TYPE); // Set the resourceId
        this.isActive = true;
        init();
    }

    public void init() {

        try {

            this.random = new Random(System.currentTimeMillis());
            this.waterConsumptionValue = MIN_WATER_CONSUMPTION_VALUE + random.nextDouble() * (MAX_WATER_CONSUMPTION_VALUE - MIN_WATER_CONSUMPTION_VALUE);

            startPeriodicEventValueUpdatedTask();

        } catch (Exception e) {
            logger.error("Error init the IoT Resource ! Msg: {}", e.getLocalizedMessage());
        }
    }

    public void startPeriodicEventValueUpdatedTask() {
        try {
            this.updateTimer = new Timer();
            this.updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {

                    if (isActive) {
                        double variation = MIN_WATER_CONSUMPTION_VARIATION + random.nextDouble() * MAX_WATER_CONSUMPTION_VARIATION * (random.nextDouble() > 0.5 ? 1 : -1);
                        waterConsumptionValue = waterConsumptionValue + variation > 0 ? waterConsumptionValue + variation : 0.0;
                    } else
                        waterConsumptionValue = 0.0;

                    notifyUpdate(waterConsumptionValue);

                }
            }, TASK_DELAY_TIME, UPDATE_PERIOD);
        } catch (Exception e) {
            logger.error("Error executing periodic resource value ! Msg: {}", e.getLocalizedMessage());
        }
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    @Override
    public Double loadUpdatedValue() {
        return waterConsumptionValue;
    }


    public static void main(String[] args) {

        WaterRawSensor rawSensor = new WaterRawSensor();
        rawSensor.setActive(true);
        logger.info("New {} Resource Created with Id: {} ! {} New Value: {}"
                , rawSensor.getType()
                , rawSensor.getDeviceId()
                , LOG_DISPLAY_NAME
                , rawSensor.loadUpdatedValue());

        rawSensor.addDataListener(new ResourceDataListener<Double>() {
            @Override
            public void onDataChanged(SmartObjectResource<Double> resource, Double updatedValue) {
                if (resource != null && updatedValue != null)
                    logger.info("Device: {} -> New Value Received: {}", resource.getDeviceId(), updatedValue);
                else
                    logger.error("onDataChanged Callback -> Null Resource or Updated Value");
            }
        });
    }
}
