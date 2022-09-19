package it.unimore.dipi.iot.server;

import it.unimore.dipi.iot.server.coap.presence.CoapPresenceEntryResource;
import it.unimore.dipi.iot.server.coap.presence.CoapPresenceExitResource;
import it.unimore.dipi.iot.server.coap.presence.CoapPresenceInsideResource;
import it.unimore.dipi.iot.server.raw.ResourceDataListener;
import it.unimore.dipi.iot.server.raw.SmartObjectResource;
import it.unimore.dipi.iot.server.raw.presence.PresenceRawSensor;
import it.unimore.dipi.iot.utils.CoreInterfaces;
import org.eclipse.californium.core.*;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Riccardo Prevedi
 * @created 11/09/2022 - 18:23
 * @project coap-smart-building
 */

public class CoapEndpointPresenceProcess extends CoapServer {

    private static final Logger logger = LoggerFactory.getLogger(CoapEndpointPresenceProcess.class);

    private static final String RD_ENDPOINT_NAME = "presenceCoapEndpoint";

    private static final String CONTENT_TYPE_ATTRIBUTE = "ct";

    private static final String RD_SECTOR_NAME = "firstFloor";

    private static final String SECTOR_RD_ATTRIBUTE = "d";

    private static final String INTERFACE_CORE_ATTRIBUTE = "if";

    private static final String WELL_KNOWN_CORE_URI = "/.well-known/core";

    private static final String RD_COAP_ENDPOINT_BASE_URL = "coap://192.168.56.101:5683/rd";

    private static final String EP_LOOKUP_URI = "/rd-lookup/ep";

    private static final String TARGET_LISTENING_IP_ADDRESS = "192.168.43.172";

    private static final int TARGET_COAP_PORT = 5883;

    private static List<String> targetEndpointList;

    private static List<String> targetResourceList;

    private CoapClient client = new CoapClient();

    private Boolean activeSupply = false;

    public CoapEndpointPresenceProcess() {
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

        //Presence sensor ID
        String deviceId = String.format("dipi:iot:%s", UUID.randomUUID().toString());

        PresenceRawSensor presenceRawSensor = new PresenceRawSensor();

        CoapPresenceEntryResource entryResource = new CoapPresenceEntryResource(deviceId, "presence-entry", presenceRawSensor);
        CoapPresenceExitResource exitResource = new CoapPresenceExitResource(deviceId, "presence-exit", presenceRawSensor);
        CoapPresenceInsideResource insideResource = new CoapPresenceInsideResource(deviceId, "presence-inside", presenceRawSensor);

        this.add(entryResource);
        this.add(exitResource);
        this.add(insideResource);

        //Observe Internal Presence Number
        presenceRawSensor.addDataListener(new ResourceDataListener<Integer>() {
            @Override
            public void onDataChanged(SmartObjectResource<Integer> resource, Integer updatedValue) {

                if (targetResourceList != null && targetResourceList.size() > 0) {
                    if (updatedValue <= 0 && activeSupply) {
                        activeSupply = false;
                        targetResourceList.forEach(targetUri -> {
                            makingPut(client, targetUri, false);
                        });
                    } else if(updatedValue > 0 && !activeSupply) {
                        activeSupply = true;
                        targetResourceList.forEach(targetUri -> {
                            makingPut(client, targetUri, true);
                        });
                    }
                } else
                    logger.info("No Device Discovered at {} zone -> No updating devices status... ", RD_SECTOR_NAME);
            }
        });
    }

    private static void makingPut(CoapClient client, String resourceUrl, Boolean bool) {

        //Request Class is a generic CoAP message: in this case we want a PUT.
        //"Message ID", "Token" and other header's fields can be set
        Request request = new Request(CoAP.Code.PUT);

        //Shaping the smart object resource URI
        request.setURI(resourceUrl);

        //Set PUT request's payload
        String myPayload = Boolean.toString(bool);
        logger.info("PUT Request Payload: {}", myPayload);
        request.setPayload(myPayload);

        //Set Request as Confirmable
        request.setConfirmable(true);

        logger.info("Request Pretty Print: \n{}", Utils.prettyPrint(request));

        //Synchronously send the PUT request (blocking call)
        CoapResponse coapResp = null;

        try {

            coapResp = client.advanced(request);

            //Pretty print for the received response
            logger.info("Response Pretty Print: \n{}", Utils.prettyPrint(coapResp));

            //The "CoapResponse" message contains the response.
            String text = coapResp.getResponseText();
            logger.info("Payload: {}", text);
            logger.info("Message ID: " + coapResp.advanced().getMID());
            logger.info("Token: " + coapResp.advanced().getTokenString());

        } catch (ConnectorException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void discoverTargetSwitchableResource(CoapClient coapClient, String endpoint) {

        //Request Class is a generic CoAP message: in this case we want a GET.
        //"Message ID", "Token" and other header's fields can be set
        Request request = new Request(CoAP.Code.GET);

        //Shaping the address interface for Resource Discovery
        //coap://192.168.10.43:5883/.well-known/core
        request.setURI(String.format("%s%s", endpoint, WELL_KNOWN_CORE_URI));

        //Set Request as Confirmable
        request.setConfirmable(true);

        //logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(request));

        //Synchronously send the GET message (blocking call)
        CoapResponse coapResp = null;

        try {

            coapResp = coapClient.advanced(request);

            if (coapResp != null) {

                //logger.info("Response Pretty Print:\n{}", Utils.prettyPrint(coapResp));

                if (coapResp.getOptions().getContentFormat() == MediaTypeRegistry.APPLICATION_LINK_FORMAT) {

                    Set<WebLink> links = LinkFormat.parse(coapResp.getResponseText());

                    links.forEach(link -> {

                        if (link.getURI() != null && !link.getURI().equals(WELL_KNOWN_CORE_URI) && link.getAttributes() != null && link.getAttributes().getCount() > 0) {

                            //If the resource is core.a, and it is observable, and its URI ends with "switch"
                            //save the target url reference
                            if (link.getAttributes().containsAttribute(INTERFACE_CORE_ATTRIBUTE)
                                    && link.getAttributes().getAttributeValues(INTERFACE_CORE_ATTRIBUTE).get(0).equals(CoreInterfaces.CORE_A.getValue())
                                    && link.getURI().endsWith("switch")) {

                                boolean supportSenml = false;

                                if (link.getAttributes().containsAttribute(CONTENT_TYPE_ATTRIBUTE))
                                    supportSenml = link.getAttributes().getAttributeValues(CONTENT_TYPE_ATTRIBUTE).contains("110");

                                logger.info("Target Switchable resource found ! URI: {} (Senml: {}) (Floor: {})"
                                        , link.getURI()
                                        , supportSenml
                                        , RD_SECTOR_NAME);

                                //E.g. coap://<node_ip>:<node_port>/<resource_uri>
                                String targetResourceUrl = String.format("%s%s", endpoint, link.getURI());

                                targetResourceList.add(targetResourceUrl);

                                logger.info("Target Switchable Resource URL: {} correctly saved !", targetResourceUrl);

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

        //Request Class is a generic CoAP message: in this case we want a GET.
        //"Message ID", "Token" and other header's fields can be set
        Request request = new Request(CoAP.Code.GET);

        //Shaping the address interface for LookUp Endpoint
        request.setURI(RD_COAP_ENDPOINT_BASE_URL.replace("/rd", EP_LOOKUP_URI));

        //Set Request as Confirmable
        request.setConfirmable(true);

        logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(request));

        //Synchronously send the GET message (blocking call)
        CoapResponse response = null;

        try {

            response = coapClient.advanced(request);

            if (response != null) {

                logger.info("Response Pretty Print:\n{}", Utils.prettyPrint(response));

                if (response.getOptions().getContentFormat() == MediaTypeRegistry.APPLICATION_LINK_FORMAT) {

                    Set<WebLink> links = LinkFormat.parse(response.getResponseText());

                    links.forEach(link -> {

                        if (link.getURI() != null && link.getAttributes() != null && link.getAttributes().getCount() > 0) {

                            //The Endpoint discovered must have the same "sector" attribute
                            //of the presence sensor registration
                            if (link.getAttributes().containsAttribute(SECTOR_RD_ATTRIBUTE)
                                    && link.getAttributes().getAttributeValues(SECTOR_RD_ATTRIBUTE).get(0).equals(RD_SECTOR_NAME)) {

                                targetEndpointList.add(link.getURI());

                                logger.info("Target Endpoint URL: {} Correctly saved ! ", link.getURI());

                            } else
                                logger.info("Endpoint {} does not match filtering parameters ....", link.getURI());
                        }
                    });

                } else {
                    logger.error("CoRE Link Format Response not found !");
                }
            }
        } catch (ConnectorException |
                IOException e) {
            e.printStackTrace();
        }
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

            //Initialize coapClient
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
            CoapResponse coapResp = null;

            try {

                coapResp = coapClient.advanced(request);

                //Pretty print for the received response
                logger.info("Response Pretty Print: \n{}", Utils.prettyPrint(coapResp));

                //The "CoapResponse" message contains the response.
                String text = coapResp.getResponseText();
                logger.info("Payload: {}", text);
                logger.info("Message ID: " + coapResp.advanced().getMID());
                logger.info("Token: " + coapResp.advanced().getTokenString());

            } catch (ConnectorException | IOException e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {

        CoapEndpointPresenceProcess coapEndpointPresenceProcess = new CoapEndpointPresenceProcess();
        coapEndpointPresenceProcess.start();

        coapEndpointPresenceProcess.getRoot().getChildren().stream().forEach(resource -> {
            logger.info("Resource {} -> URI: {} (Observable: {})", resource.getName(), resource.getURI(), resource.isObservable());
        });

        registerToCoapResourceDirectory(coapEndpointPresenceProcess.getRoot()
                , RD_ENDPOINT_NAME
                , RD_SECTOR_NAME
                , TARGET_LISTENING_IP_ADDRESS
                , TARGET_COAP_PORT);

        targetEndpointList = new ArrayList<>();
        targetResourceList = new ArrayList<>();

        CoapClient coapClient = new CoapClient();

        discoverTargetEndpoint(coapClient);

        targetEndpointList.stream().forEach(endpoint -> {
            discoverTargetSwitchableResource(coapClient, endpoint);
        });
    }
}
