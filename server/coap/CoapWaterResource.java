package it.unimore.dipi.iot.server.coap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.dipi.iot.server.raw.ResourceDataListener;
import it.unimore.dipi.iot.server.raw.SmartObjectResource;
import it.unimore.dipi.iot.server.raw.WaterRawSensor;
import it.unimore.dipi.iot.utils.CoreInterfaces;
import it.unimore.dipi.iot.utils.SenMLPack;
import it.unimore.dipi.iot.utils.SenMLRecord;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * This is the CoAP resource.
 * The resource has attributes "rt", "obs", "if" and "ct" as standard CoRE Interface
 *
 * @author Riccardo Prevedi
 * @created 29/08/2022 - 09:42
 * @project coap-smart-building
 */

public class CoapWaterResource extends CoapResource {

    private static final Logger logger = LoggerFactory.getLogger(CoapWaterResource.class);

    private static final Number VERSION = 0.1;

    private static final String OBJECT_TITLE = "WaterConsumptionSensor";

    private Double updatedWaterValue = 0.0;

    private WaterRawSensor waterRawSensor;

    private String deviceId;

    private String UNIT = "l/s";

    private ObjectMapper objectMapper;


    public CoapWaterResource(String deviceId, String name, WaterRawSensor waterRawSensor) {
        super(name);

        if (deviceId != null && waterRawSensor != null) {
            this.deviceId = deviceId;
            this.waterRawSensor = waterRawSensor;

            //Jackson Object Mapper + Ignore Null Fields in order to properly generate the SenML Payload
            this.objectMapper = new ObjectMapper();
            this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            setObservable(true); //Enable observing
            setObserveType(CoAP.Type.CON); //Configure the notification type to CONs

            //Specify Resource Attributes according to the CoRE Link-Format and the CoRE Interfaces
            getAttributes().setTitle(OBJECT_TITLE);
            getAttributes().setObservable(); //mark observable in the Link-Format

            //Domain specific E.g. smartBuilding.floor.<floor_id>.system.<system_id>.device.<type>.<device_id>
            getAttributes().addAttribute("rt", waterRawSensor.getType());
            getAttributes().addAttribute("if", CoreInterfaces.CORE_S.getValue());
            getAttributes().addAttribute("ct", Integer.toString(MediaTypeRegistry.APPLICATION_SENML_JSON));
            getAttributes().addAttribute("ct", Integer.toString(MediaTypeRegistry.TEXT_PLAIN));

            this.waterRawSensor.addDataListener(new ResourceDataListener<Double>() {
                @Override
                public void onDataChanged(SmartObjectResource<Double> resource, Double updatedValue) {
                    updatedWaterValue = updatedValue;
                    changed();
                }
            });
        } else
            logger.error("Error -> NULL Raw Reference");
    }

    private Optional<String> getJsonSenmlResponse() {
        try {

            SenMLPack senMLPack = new SenMLPack();

            SenMLRecord senMLRecord = new SenMLRecord();
            senMLRecord.setBn(String.format("%s:%s", this.deviceId, this.getName()));
            senMLRecord.setBver(VERSION);
            senMLRecord.setU(UNIT);
            senMLRecord.setV(updatedWaterValue);
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
        //Do not consider requests made within 5 seconds of each other
        exchange.setMaxAge(WaterRawSensor.UPDATE_PERIOD);

        if (exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_SENML_JSON ||
                exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_JSON) {

            Optional<String> senmlPayload = getJsonSenmlResponse();

            if (senmlPayload.isPresent())
                exchange.respond(CoAP.ResponseCode.CONTENT, senmlPayload.get(), exchange.getRequestOptions().getAccept());
            else
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);

        } else
            exchange.respond(CoAP.ResponseCode.CONTENT, String.valueOf(updatedWaterValue), MediaTypeRegistry.TEXT_PLAIN);
    }
}
