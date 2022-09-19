package it.unimore.dipi.iot.server.coap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.dipi.iot.server.raw.ResourceDataListener;
import it.unimore.dipi.iot.server.raw.SmartObjectResource;
import it.unimore.dipi.iot.server.raw.SwitchRawActuator;
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
 * @author Riccardo Prevedi
 * @created 29/08/2022 - 17:06
 * @project coap-smart-building
 */

public class CoapSwitchActuatorResource extends CoapResource {

    private static final Logger logger = LoggerFactory.getLogger(CoapSwitchActuatorResource.class);

    private static final Number VERSION = 0.1;

    private static final String OBJECT_TITLE = "SwitchActuator";

    private ObjectMapper objectMapper;

    private SwitchRawActuator switchRawActuator;

    private Boolean isOn;

    private String deviceId;


    public CoapSwitchActuatorResource(String deviceId, String name, SwitchRawActuator switchRawActuator) {
        super(name);

        if (switchRawActuator != null && deviceId != null) {

            this.deviceId = deviceId;
            this.switchRawActuator = switchRawActuator;
            this.isOn = switchRawActuator.getActive();

            //Jackson Object Mapper + Ignore Null Fields in order to properly generate the SenML Payload
            this.objectMapper = new ObjectMapper();
            this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            setObservable(true); //Enable observing
            setObserveType(CoAP.Type.CON); //Configure the notification type to CONs

            //Specify Resource Attributes according to the CoRE Link-Format and the CoRE Interfaces
            getAttributes().setTitle(OBJECT_TITLE);
            getAttributes().setObservable(); //mark observable in the Link-Format
            getAttributes().addAttribute("rt", switchRawActuator.getType());
            getAttributes().addAttribute("if", CoreInterfaces.CORE_A.getValue());
            getAttributes().addAttribute("ct", Integer.toString(MediaTypeRegistry.APPLICATION_SENML_JSON));
            getAttributes().addAttribute("ct", Integer.toString(MediaTypeRegistry.TEXT_PLAIN));

            // TODO delete these code lines ?
            switchRawActuator.addDataListener(new ResourceDataListener<Boolean>() {
                @Override
                public void onDataChanged(SmartObjectResource<Boolean> resource, Boolean updatedValue) {
                    logger.info("Raw Resource Notification Callback ! New Value: {}", updatedValue);
                    isOn = updatedValue;
                    changed();
                }
            });

        } else
            logger.error("Error -> NULL Raw Reference !");
    }

    private Optional<String> getJsonSenmlResponse() {
        try {
            SenMLPack senMLPack = new SenMLPack();

            SenMLRecord senMLRecord = new SenMLRecord();
            senMLRecord.setBn(String.format("%s:%s", this.deviceId, this.getName()));
            senMLRecord.setVb(this.isOn);
            senMLRecord.setBver(VERSION);
            senMLRecord.setT(System.currentTimeMillis());

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
            exchange.respond(CoAP.ResponseCode.CONTENT, String.valueOf(isOn), MediaTypeRegistry.TEXT_PLAIN);
    }

    @Override
    public void handlePOST(CoapExchange exchange) {

        try {
            logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(exchange.advanced().getRequest()));
            logger.info("Received POST Request with body: {}", exchange.getRequestPayload());

            if (exchange.getRequestPayload() == null) {

                this.isOn = !isOn;
                this.switchRawActuator.setActive(this.isOn); //All listeners and all observers are notified
                logger.info("Resource Status Updated: {}", this.isOn);

                exchange.respond(CoAP.ResponseCode.CHANGED);

            } else
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST);

        } catch (Exception e) {
            logger.error("Error Handling POST -> {}", e.getLocalizedMessage());
            exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void handlePUT(CoapExchange exchange) {

        try {
            logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(exchange.advanced().getRequest()));
            logger.info("Received PUT Request with body: {}", exchange.getRequestText());

            if (exchange.getRequestPayload() != null) {

                Boolean submittedValue = Boolean.parseBoolean(new String(exchange.getRequestPayload()));

                if(submittedValue.equals(true) || submittedValue.equals(false)){

                    this.isOn = submittedValue;
                    this.switchRawActuator.setActive(this.isOn);
                    logger.info("Resource Status Updated: {}", this.isOn);

                    exchange.respond(CoAP.ResponseCode.CHANGED);

                } else
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            } else
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST);

        } catch (Exception e) {
            logger.error("Error Handling PUT -> {}", e.getLocalizedMessage());
            exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
}
