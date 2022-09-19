package it.unimore.dipi.iot.server.coap.presence;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.dipi.iot.server.raw.ResourceDataListener;
import it.unimore.dipi.iot.server.raw.SmartObjectResource;
import it.unimore.dipi.iot.server.raw.presence.PresenceRawSensor;
import it.unimore.dipi.iot.utils.CoreInterfaces;
import it.unimore.dipi.iot.utils.SenMLPack;
import it.unimore.dipi.iot.utils.SenMLRecord;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The resource counts people exit.
 * Increasing its value every POST with null body that received
 *
 * @author Riccardo Prevedi
 * @created 11/09/2022 - 17:20
 * @project coap-smart-building
 */

public class CoapPresenceExitResource extends CoapResource {

    private static final Logger logger = LoggerFactory.getLogger(CoapPresenceExitResource.class);

    private static final Number VERSION = 0.1;

    private static final String OBJECT_TITLE = "ZonePresenceOutgoingCounterActuator";

    private static final String RESOURCE_TYPE = "iot.actuator.presence";

    private static final String UNIT = "count";

    private PresenceRawSensor presenceRawSensor;

    private Integer updatedPresenceValue = 0;

    private String deviceId;

    private ObjectMapper objectMapper;


    public CoapPresenceExitResource(String deviceId, String name, PresenceRawSensor presenceRawSensor) {
        super(name);

        if (presenceRawSensor != null && deviceId != null) {

            this.deviceId = deviceId;
            this.presenceRawSensor = presenceRawSensor;

            //Jackson Object Mapper + Ignore Null Fields in order to properly generate the SenML Payload
            this.objectMapper = new ObjectMapper();
            this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            setObservable(true); //Enable observing
            setObserveType(CoAP.Type.CON); //Configure the notification type to CONs

            //Specify Resource Attributes according to the CoRE Link-Format and the CoRE Interfaces
            getAttributes().setTitle(OBJECT_TITLE);
            getAttributes().setObservable(); //mark observable in the Link-Format
            getAttributes().addAttribute("rt", RESOURCE_TYPE);
            getAttributes().addAttribute("if", CoreInterfaces.CORE_A.getValue());
            getAttributes().addAttribute("ct", Integer.toString(MediaTypeRegistry.APPLICATION_SENML_JSON));
            getAttributes().addAttribute("ct", Integer.toString(MediaTypeRegistry.TEXT_PLAIN));

        } else
            logger.error("Error -> NULL Raw Reference !");
    }

    private Optional<String> getJsonSenmlResponse() {
        try {
            SenMLPack senMLPack = new SenMLPack();

            SenMLRecord senMLRecord = new SenMLRecord();
            senMLRecord.setBn(String.format("%s:%s", this.deviceId, this.getName()));
            senMLRecord.setV(this.updatedPresenceValue);
            senMLRecord.setU(UNIT);
            senMLRecord.setBver(VERSION);

            senMLPack.add(senMLRecord);

            return Optional.of(this.objectMapper.writeValueAsString(senMLPack));

        } catch (Exception e) {
            logger.error("Error Generating SenML Record ! Msg: {}", e.getLocalizedMessage());
            return Optional.empty();
        }
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        if (exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_JSON ||
                exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_SENML_JSON) {

            Optional<String> senmlPayload = getJsonSenmlResponse();

            if (senmlPayload.isPresent())
                exchange.respond(CoAP.ResponseCode.CONTENT, senmlPayload.get(), exchange.getRequestOptions().getAccept());
            else
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        } else
            exchange.respond(CoAP.ResponseCode.CONTENT, String.valueOf(updatedPresenceValue), MediaTypeRegistry.TEXT_PLAIN);
    }

    @Override
    public void handlePOST(CoapExchange exchange) {

        try {
            logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(exchange.advanced().getRequest()));
            logger.info("Received POST Request with body: {}", exchange.getRequestPayload());

            if (exchange.getRequestPayload() == null) {

                presenceRawSensor.increasePeopleOut();
                updatedPresenceValue = presenceRawSensor.getPeopleOut();

                exchange.respond(CoAP.ResponseCode.CHANGED);

            } else
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST);

        } catch (Exception e) {
            logger.error("Error Handling POST -> {}", e.getLocalizedMessage());
            exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

}
