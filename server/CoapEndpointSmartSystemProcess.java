package it.unimore.dipi.iot.server;

import it.unimore.dipi.iot.server.coap.*;
import it.unimore.dipi.iot.server.model.EnergyCounterConfigurationModel;
import it.unimore.dipi.iot.server.model.GasCounterConfigurationModel;
import it.unimore.dipi.iot.server.model.WaterCounterConfigurationModel;
import it.unimore.dipi.iot.server.raw.*;
import org.eclipse.californium.core.*;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.server.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


/**
 * @author Riccardo Prevedi
 * @created 23/08/2022 - 08:49
 * @project coap-smart-building
 */

public class CoapEndpointSmartSystemProcess extends CoapServer {

    private static final Logger logger = LoggerFactory.getLogger(CoapEndpointSmartSystemProcess.class);

    private static final String TARGET_OBSERVING_URL = "coap://192.168.43.172:5883/presence-inside"; //configured without endpoint lookup

    private static final String RD_ENDPOINT_NAME = "node1"; //Smart building system

    private static final String RD_SECTOR_NAME = "firstFloor";  //Smart building floor

    private static final String RD_COAP_ENDPOINT_BASE_URL = "coap://192.168.56.101:5683/rd";

    private static final String TARGET_LISTENING_IP_ADDRESS = "192.168.43.172";

    private static final int TARGET_COAP_PORT = 5783;

    private static Timer timer;

    private static final int POWER_OFF_TIME = 5000;    // 5sec

    private static Boolean arePeopleInside = true;


    public CoapEndpointSmartSystemProcess() {
        super();

        // explicitly bind to each address to avoid the wildcard address reply problem
        // (default interface address instead of original destination)
        for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
            if (!addr.isLinkLocalAddress()) {
                CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
                builder.setInetSocketAddress(new InetSocketAddress(addr, TARGET_COAP_PORT));
                this.addEndpoint(builder.build());
            }
        }

        timer = new Timer();

        //SystemId
        String deviceId = String.format("dipi:iot:%s", UUID.randomUUID().toString());

        this.add(createWaterProviderResource(deviceId));
        this.add(createEnergyProviderResource(deviceId));
        this.add(createGasProviderResource(deviceId));

    }

    /**
     * This method is used to recognize if people are present in the zone
     * For less complexity of code it's taken the presence sensor URL as known yet.
     */
    private static void observingPresenceSensor(CoapClient coapClient) {

        logger.info("OBSERVING ... {}", TARGET_OBSERVING_URL);

        //Create the CoAP Request
        Request request = Request.newGet().setURI(TARGET_OBSERVING_URL).setObserve();
        request.setConfirmable(true);

        CoapObserveRelation relation = coapClient.observe(request, new CoapHandler() {
            @Override
            public void onLoad(CoapResponse coapResponse) {

                int presenceNumber = Integer.parseInt(coapResponse.getResponseText());
                arePeopleInside = presenceNumber > 0;
                //logger.info("Notification Response Pretty print:\n{}", Utils.prettyPrint(coapResponse));
                //logger.info("NOTIFICATION Presence inside the floor: {}", presenceNumber);
            }

            @Override
            public void onError() {
                logger.error("OBSERVING FAILED");
            }
        });

    }


    /**
     * This method is used to keep track of consumption values that exceed the maximum configurable value
     *
     * @param configurationParameter max conf value
     * @param value                  value provided from the sensor
     */
    private static Boolean isSwitchOffRequired(ProviderRawConfigurationParameter configurationParameter, Double value) {
        return value > configurationParameter.getGenericCounterConfigurationModel().getMaxValueConsumption();
    }

    /**
     * This method switches the state of the switch
     * When the consumption exceeds the maximum configurable value for the supply
     * And after a certain period of time if people are present.
     */
    private static void changeSwitchStatus(SwitchRawActuator rawActuator, Double updatedValue, ProviderRawConfigurationParameter configurationParameter) {
        rawActuator.setActive(false);
        logger.info("Switching-OFF the Supply ! Max Value Consumption Level: {} -> Value Reached: {}"
                , configurationParameter.loadUpdatedValue().getMaxValueConsumption()
                , updatedValue);

        changingSwitchStatusTimerTask(rawActuator);
    }

    private static void changingSwitchStatusTimerTask(SwitchRawActuator rawActuator){

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (arePeopleInside) {
                    rawActuator.setActive(true);
                    logger.info("People inside -> Turning-ON the {} resource ! Time passed: {}ms", rawActuator.getType(), POWER_OFF_TIME);
                } else
                    logger.info("NO People inside -> the {} resource still off ! Time passed: {}ms", rawActuator.getType(), POWER_OFF_TIME);
            }
        }, new Date(System.currentTimeMillis() + POWER_OFF_TIME));

    }


    /**
     * Create the water provider resource
     *
     * @param deviceId the ID of the system
     */
    private CoapResource createWaterProviderResource(String deviceId) {

        CoapResource waterProviderResource = new CoapResource("water-provider");

        //INIT Emulated Physical Sensors and Actuators
        WaterRawSensor nodeWaterRawSensor = new WaterRawSensor();
        SwitchRawActuator nodeWaterSwitchRawActuator = new SwitchRawActuator();
        ProviderRawConfigurationParameter nodeWaterParameter = new ProviderRawConfigurationParameter(new WaterCounterConfigurationModel());

        //Create Server's Resources
        CoapWaterResource nodeWaterResource = new CoapWaterResource(deviceId, "H2O", nodeWaterRawSensor);
        CoapSwitchActuatorResource nodeWaterSwitchResource = new CoapSwitchActuatorResource(deviceId, "switch", nodeWaterSwitchRawActuator);
        CoapProviderConfigurationParameterResource nodeWaterParameterResource = new CoapProviderConfigurationParameterResource(deviceId, "parameter", nodeWaterParameter);

        waterProviderResource.add(nodeWaterResource);
        waterProviderResource.add(nodeWaterSwitchResource);
        waterProviderResource.add(nodeWaterParameterResource);

        //Handle Emulated Resource notification
        nodeWaterSwitchRawActuator.addDataListener(new ResourceDataListener<Boolean>() {
            @Override
            public void onDataChanged(SmartObjectResource<Boolean> resource, Boolean updatedValue) {
                logger.info("[WATER-PROVIDER-BEHAVIOUR] -> Updated Switch Value: {}", updatedValue);
                logger.info("[WATER-PROVIDER-BEHAVIOUR] -> Updating water sensor configuration ...");
                nodeWaterRawSensor.setActive(updatedValue);

                if(!updatedValue)
                    changingSwitchStatusTimerTask(nodeWaterSwitchRawActuator);
            }
        });

        nodeWaterRawSensor.addDataListener(new ResourceDataListener<Double>() {
            @Override
            public void onDataChanged(SmartObjectResource<Double> resource, Double updatedValue) {
                if (nodeWaterSwitchRawActuator.getActive() && isSwitchOffRequired(nodeWaterParameter, updatedValue) && !arePeopleInside) {
                    changeSwitchStatus(nodeWaterSwitchRawActuator, updatedValue, nodeWaterParameter);
                }
            }
        });

        return waterProviderResource;
    }


    /**
     * Create the energy provider resource
     *
     * @param deviceId the ID of the system
     */
    private CoapResource createEnergyProviderResource(String deviceId) {

        CoapResource energyProviderResource = new CoapResource("energy-provider");

        //INIT Emulated Physical Sensors and Actuators
        EnergyRawSensor nodeEnergyRawSensor = new EnergyRawSensor();
        SwitchRawActuator nodeEnergySwitchRawActuator = new SwitchRawActuator();
        ProviderRawConfigurationParameter nodeEnergyParameter = new ProviderRawConfigurationParameter(new EnergyCounterConfigurationModel());


        //Create Server's Resources
        CoapEnergyResource nodeEnergyResource = new CoapEnergyResource(deviceId, "EN", nodeEnergyRawSensor);
        CoapSwitchActuatorResource nodeEnergySwitchResource = new CoapSwitchActuatorResource(deviceId, "switch", nodeEnergySwitchRawActuator);
        CoapProviderConfigurationParameterResource nodeEnergyParameterResource = new CoapProviderConfigurationParameterResource(deviceId, "parameter", nodeEnergyParameter);

        energyProviderResource.add(nodeEnergyResource);
        energyProviderResource.add(nodeEnergySwitchResource);
        energyProviderResource.add(nodeEnergyParameterResource);

        //Handle Emulated Resource notification
        nodeEnergySwitchRawActuator.addDataListener(new ResourceDataListener<Boolean>() {
            @Override
            public void onDataChanged(SmartObjectResource<Boolean> resource, Boolean updatedValue) {
                logger.info("[ENERGY-PROVIDER-BEHAVIOUR] -> Updated Switch Value: {}", updatedValue);
                logger.info("[ENERGY-PROVIDER-BEHAVIOUR] -> Updating energy sensor configuration ...");
                nodeEnergyRawSensor.setActive(updatedValue);

                if(!updatedValue)
                    changingSwitchStatusTimerTask(nodeEnergySwitchRawActuator);
            }
        });

        nodeEnergyRawSensor.addDataListener(new ResourceDataListener<Double>() {
            @Override
            public void onDataChanged(SmartObjectResource<Double> resource, Double updatedValue) {
                if (nodeEnergySwitchRawActuator.getActive() && isSwitchOffRequired(nodeEnergyParameter, updatedValue) && !arePeopleInside) {
                    changeSwitchStatus(nodeEnergySwitchRawActuator, updatedValue, nodeEnergyParameter);
                }
            }
        });

        return energyProviderResource;

    }

    /**
     * Create the Gas provider resource
     *
     * @param deviceId the ID of the system
     */
    private CoapResource createGasProviderResource(String deviceId) {

        CoapResource gasProviderResource = new CoapResource("gas-provider");

        //INIT Emulated Physical Sensors and Actuators
        GasRawSensor nodeGasRawSensor = new GasRawSensor();
        SwitchRawActuator nodeGasSwitchRawActuator = new SwitchRawActuator();
        ProviderRawConfigurationParameter nodeGasParameter = new ProviderRawConfigurationParameter(new GasCounterConfigurationModel());


        //Create Server's Resources
        CoapGasResource nodeGasResource = new CoapGasResource(deviceId, "gas", nodeGasRawSensor);
        CoapSwitchActuatorResource nodeGasSwitchResource = new CoapSwitchActuatorResource(deviceId, "switch", nodeGasSwitchRawActuator);
        CoapProviderConfigurationParameterResource nodeGasParameterResource = new CoapProviderConfigurationParameterResource(deviceId, "parameter", nodeGasParameter);


        gasProviderResource.add(nodeGasResource);
        gasProviderResource.add(nodeGasSwitchResource);
        gasProviderResource.add(nodeGasParameterResource);

        //Handle Emulated Resource notification
        nodeGasSwitchRawActuator.addDataListener(new ResourceDataListener<Boolean>() {
            @Override
            public void onDataChanged(SmartObjectResource<Boolean> resource, Boolean updatedValue) {
                logger.info("[GAS-PROVIDER-BEHAVIOUR] -> Updated Switch Value: {}", updatedValue);
                logger.info("[GAS-PROVIDER-BEHAVIOUR] -> Updating gas sensor configuration ...");
                nodeGasRawSensor.setActive(updatedValue);

                if(!updatedValue)
                    changingSwitchStatusTimerTask(nodeGasSwitchRawActuator);
            }
        });

        nodeGasRawSensor.addDataListener(new ResourceDataListener<Double>() {
            @Override
            public void onDataChanged(SmartObjectResource<Double> resource, Double updatedValue) {
                if (nodeGasSwitchRawActuator.getActive() && isSwitchOffRequired(nodeGasParameter, updatedValue) && !arePeopleInside) {
                    changeSwitchStatus(nodeGasSwitchRawActuator, updatedValue, nodeGasParameter);
                }
            }
        });


        return gasProviderResource;

    }

    private static void registerToCoapResourceDirectory(Resource rootResource, String endPointName, String sectorName, String sourceIpAddress, int sourcePort) {

        try {
            //coap://192.168.10.19:5683/rd?ep=myEndpointName&d=mySectorName&base=coap://<sourceIp>:<sourcePort>
            String finalRdUrl = String.format("%s?ep=%s&d=%s&base=coap://%s:%d"
                    , RD_COAP_ENDPOINT_BASE_URL
                    , endPointName
                    , sectorName
                    , sourceIpAddress
                    , sourcePort);

            logger.info("Registering to Resource Directory: {}", finalRdUrl);

            //Init coap client
            CoapClient coapClient = new CoapClient(finalRdUrl);

            //Request Class is a generic CoAP message: in this case we want a GET.
            //"Message ID", "Token" and other header's fields can be set
            Request request = new Request(CoAP.Code.POST);

            //If the POST request has a payload it can be set with the following command
            request.setPayload(LinkFormat.serializeTree(rootResource));

            //Set Request as Confirmable
            request.setConfirmable(true);

            logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(request));

            //Synchronously send the POST request (blocking call)
            CoapResponse response = null;

            try {

                response = coapClient.advanced(request);

                logger.info("Response Pretty Print:\n{}", Utils.prettyPrint(response));

                //The "CoapResponse" message contains the response.
                String text = response.getResponseText();
                logger.info("Payload: {}", text);
                logger.info("Message ID: " + response.advanced().getMID());
                logger.info("Token: " + response.advanced().getTokenString());

            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {

        CoapEndpointSmartSystemProcess smartSystemEndpointProcess = new CoapEndpointSmartSystemProcess();
        smartSystemEndpointProcess.start();

        logger.info("Coap Server Started ! Available resources: ");

        smartSystemEndpointProcess.getRoot().getChildren().stream().forEach(resource -> {
            logger.info("Resource {} -> URI: {} (Observable: {})", resource.getName(), resource.getURI(), resource.isObservable());
            if (!resource.getURI().equals("/.well-known")) {
                resource.getChildren().stream().forEach(childResource -> {
                    logger.info("\t Resource {} -> URI: {} (Observable: {})", childResource.getName(), childResource.getURI(), childResource.isObservable());
                });
            }
        });

        //Resource rootResource, String endPointName, String sectorName, String sourceIpAddress, int sourcePort
        registerToCoapResourceDirectory(smartSystemEndpointProcess.getRoot()
                , RD_ENDPOINT_NAME
                , RD_SECTOR_NAME
                , TARGET_LISTENING_IP_ADDRESS
                , TARGET_COAP_PORT);

        CoapClient coapClient = new CoapClient();

        observingPresenceSensor(coapClient);
    }
}
