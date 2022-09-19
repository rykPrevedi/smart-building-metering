package it.unimore.dipi.iot;

import it.unimore.dipi.iot.utils.CoreInterfaces;
import org.eclipse.californium.core.*;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author Riccardo Prevedi
 * @created 12/09/2022 - 14:46
 * @project coap-smart-building
 */

public class CoapDataCollectorAndManagerProcess {

    private static final Logger logger = LoggerFactory.getLogger(CoapDataCollectorAndManagerProcess.class);

    private static final String TARGET_RD_ADDRESS = "192.168.56.101";

    private static final int TARGET_RD_PORT = 5683;

    private static final String OBSERVABLE_CORE_ATTRIBUTE = "obs";

    private static final String INTERFACE_CORE_ATTRIBUTE = "if";

    private static final String CONTENT_TYPE_ATTRIBUTE = "ct";

    private static final String EP_LOOKUP_URI = "/rd-lookup/ep";

    private static final String SECTOR_RD_ATTRIBUTE = "d";

    private static final String WELL_KNOWN_CORE_URI = "/.well-known/core";

    private static List<String> targetEndpointList;

    private static Map<String, List<String>> targetEndpointMap;

    private static final Double waterPrice = 0.00236; //Price H2O actually in Italy (€/l)

    private static final Double energyPrice = 0.276; //Price energy actually in Italy (€/kWh)

    private static final Double gasPrice = 1.049988; //Price CH4 actually in Italy (€/m3)

    private static final long TIME_FOR_COST_CALCULATING = 60 * 1000; //1min


    public static void main(String[] args) {

        targetEndpointMap = new HashMap<>();
        targetEndpointList = new ArrayList<>();

        //Init Coap Client
        CoapClient coapClient = new CoapClient();

        //it's been discovered any endpoint and its relative sector reference (floor)
        //it's filled the map: <key_floor, value_endpoint_list>
        discoverTargetEndpoint(coapClient);

        targetEndpointMap.forEach((floor, endpoints) -> {

            logger.info("Starting the discovery for the {} resources ...", floor);

            List<String> targetObservableFloorResourceList = new ArrayList<>();
            Map<String, CoapObserveRelation> observingRelationMap = new HashMap<>();

            //Start Resource Discovery (water, energy and gas value consumptions)
            //The resources hosted by the smart object (the endpoint) are discovered using /.well-known/core resource
            endpoints.stream().forEach(endpoint -> {
                discoverTargetObservableResource(coapClient, endpoint, targetObservableFloorResourceList);
            });

            List<Double> waterValueList = new ArrayList<>();
            List<Double> energyValueList = new ArrayList<>();
            List<Double> gasValueList = new ArrayList<>();

            //Start observing each resource
            targetObservableFloorResourceList.forEach(targetResourceUrl -> {
                startObservingTargetResource(coapClient
                        , targetResourceUrl
                        , observingRelationMap
                        , floor
                        , waterValueList
                        , energyValueList
                        , gasValueList);
            });

            //Sleep and then cancel registration
            try {
                Thread.sleep(TIME_FOR_COST_CALCULATING);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            observingRelationMap.forEach((uri, relation) -> {
                logger.info("Cancelling Observation for target Uri: {}", uri);
                relation.proactiveCancel();
            });

            double totalWaterCostConsumptionValue = getAverage(waterValueList) * (TIME_FOR_COST_CALCULATING / 1000.0) * waterPrice;
            logger.info("Floor: {} - Raw Material cost: {} €_l - Time passed: {}s - Average Cost: {}€"
                    , floor
                    , waterPrice
                    , (TIME_FOR_COST_CALCULATING / 1000)
                    , totalWaterCostConsumptionValue);

            double totalEnergyCostConsumptionValue = getAverage(energyValueList) * ((TIME_FOR_COST_CALCULATING / 1000.0) / 3600.0) * energyPrice;
            logger.info("Floor: {} - Raw Material cost: {} €_kWh - Time passed: {}s - Average Cost: {}€"
                    , floor
                    , energyPrice
                    , (TIME_FOR_COST_CALCULATING / 1000)
                    , totalEnergyCostConsumptionValue);

            double totalGasCostConsumptionValue = getAverage(gasValueList) * (TIME_FOR_COST_CALCULATING / 1000.0) * gasPrice;
            logger.info("Floor: {} - Raw Material cost: {} €_m3 - Time passed: {}s - Average Cost: {}€"
                    , floor
                    , gasPrice
                    , (TIME_FOR_COST_CALCULATING / 1000)
                    , totalGasCostConsumptionValue);
        });
    }


    private static double getAverage(List<Double> list) {
        return list.stream()
                .mapToDouble(d -> d)
                .average().orElse(0);
    }

    /**
     * This method allows to observe the target resources,
     * the observations are added in the respective supply list.
     */
    private static void startObservingTargetResource(CoapClient coapClient
            , String targetUrl
            , Map<String, CoapObserveRelation> observeRelationMap
            , String floor
            , List<Double> waterValueList
            , List<Double> energyValueList
            , List<Double> gasValueList
    ) {

        logger.info("OBSERVING ... for the {} on {}", targetUrl, floor);


        Request request = Request.newGet().setURI(targetUrl).setObserve();
        request.setConfirmable(true);

        CoapObserveRelation relation = coapClient.observe(request, new CoapHandler() {
            @Override
            public void onLoad(CoapResponse coapResponse) {

                if (targetUrl.endsWith("H2O")) {

                    Double value = Double.parseDouble(coapResponse.getResponseText());
                    logger.info("Notification -> Resource Target: {} -> Body: {}", targetUrl, value);
                    waterValueList.add(value);

                } else if (targetUrl.endsWith("gas")) {

                    Double value = Double.parseDouble(coapResponse.getResponseText());
                    logger.info("Notification -> Resource Target: {} -> Body: {}", targetUrl, value);
                    gasValueList.add(value);

                } else if (targetUrl.endsWith("EN")) {

                    Double value = Double.parseDouble(coapResponse.getResponseText());
                    logger.info("Notification -> Resource Target: {} -> Body: {}", targetUrl, value);
                    energyValueList.add(value);
                }
            }

            @Override
            public void onError() {
                logger.error("OBSERVING {} FAILED", targetUrl);
            }
        });

        observeRelationMap.put(targetUrl, relation);
    }


    /**
     * GET Request for Resource Discovery -> standard resource /.well-known/core
     * This method allows to discover the smart object interesting resources, the resources are filtered to provide only water, gas and energy consumes
     */
    private static void discoverTargetObservableResource(CoapClient coapClient, String endpoint, List<String> targetResourceList) {

        Request request = new Request(CoAP.Code.GET);

        request.setURI(String.format("%s%s", endpoint, WELL_KNOWN_CORE_URI));

        request.setConfirmable(true);

        //logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(request));

        //Synchronously send the GET message (blocking call)
        CoapResponse coapResp = null;

        try {

            coapResp = coapClient.advanced(request);

            if (coapResp != null) {

                logger.info("Response Pretty Print:\n{}", Utils.prettyPrint(coapResp));

                if (coapResp.getOptions().getContentFormat() == MediaTypeRegistry.APPLICATION_LINK_FORMAT) {

                    Set<WebLink> links = LinkFormat.parse(coapResp.getResponseText());

                    links.forEach(link -> {

                        if (link.getURI() != null
                                && !link.getURI().equals(WELL_KNOWN_CORE_URI)
                                && link.getAttributes() != null
                                && link.getAttributes().getCount() > 0) {

                            //If the resource is a core.s or core.a
                            //and it is observable save the target url reference
                            //and it's not a presence sensor
                            if (link.getAttributes().containsAttribute(OBSERVABLE_CORE_ATTRIBUTE)
                                    && link.getAttributes().containsAttribute(INTERFACE_CORE_ATTRIBUTE)
                                    && (link.getAttributes().getAttributeValues(INTERFACE_CORE_ATTRIBUTE).get(0).equals(CoreInterfaces.CORE_S.getValue()))
                                    && !link.getURI().endsWith("presence-inside")) {

                                boolean supportSenml = false;

                                if (link.getAttributes().containsAttribute(CONTENT_TYPE_ATTRIBUTE))
                                    supportSenml = link.getAttributes().getAttributeValues(CONTENT_TYPE_ATTRIBUTE).contains("110");

                                logger.info("Target resource found ! URI: {}} (Senml: {})", link.getURI(), supportSenml);

                                //E.g. coap://<node_ip>:<node_port>/<resource_uri>
                                String targetResourceUrl = String.format("%s%s", endpoint, link.getURI());

                                targetResourceList.add(targetResourceUrl);

                                logger.info("Target Resource URL: {} correctly saved !", targetResourceUrl);

                            }
                        }
                    });
                } else {
                    logger.error("CoRE Link Format Response not found !");
                }
            }
        } catch (ConnectorException | IOException e) {
            e.printStackTrace();
        }
    }


    //Even if the endpoint is unreachable
    private static void discoverTargetEndpoint(CoapClient coapClient) {

        Request request = new Request(CoAP.Code.GET);

        request.setURI(String.format("coap://%s:%d%s"
                , TARGET_RD_ADDRESS
                , TARGET_RD_PORT
                , EP_LOOKUP_URI));

        request.setConfirmable(true);

        logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(request));

        CoapResponse response = null;

        try {

            response = coapClient.advanced(request);

            logger.info("Response Pretty Print:\n{}", Utils.prettyPrint(response));

            if (response.getOptions().getContentFormat() == MediaTypeRegistry.APPLICATION_LINK_FORMAT) {

                Set<WebLink> links = LinkFormat.parse(response.getResponseText());

                links.forEach(link -> {

                    if (link.getURI() != null && link.getAttributes() != null && link.getAttributes().getCount() > 0) {

                        if (link.getAttributes().containsAttribute(SECTOR_RD_ATTRIBUTE)) {

                            //Mapping endpoints on the floor
                            String d = link.getAttributes().getAttributeValues(SECTOR_RD_ATTRIBUTE).get(0);
                            targetEndpointList.add(link.getURI());
                            targetEndpointMap.put(d, targetEndpointList);

                            logger.info("Target Endpoint URL: {} Correctly saved ! ", link.getURI());

                        } else
                            logger.info("Endpoint {} does not match filtering parameters ....", link.getURI());
                    }
                });

            } else {
                logger.error("CoRE Link Format Response not found !");
            }

        } catch (ConnectorException |
                IOException e) {
            e.printStackTrace();
        }
    }
}