package it.unimore.dipi.iot.server.coap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.dipi.iot.server.model.ChangeResourceParameterDescriptor;
import it.unimore.dipi.iot.server.model.GenericCounterConfigurationModel;
import it.unimore.dipi.iot.server.raw.ProviderRawConfigurationParameter;
import it.unimore.dipi.iot.server.raw.ResourceDataListener;
import it.unimore.dipi.iot.server.raw.SmartObjectResource;
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
 * @created 09/09/2022 - 12:22
 * @project coap-smart-building
 */

public class CoapProviderConfigurationParameterResource extends CoapResource {

    private static final Logger logger = LoggerFactory.getLogger(CoapProviderConfigurationParameterResource.class);

    private static final String OBJECT_TITLE = "ProviderConfiguration";

    private static final Number SOFTWARE_VERSION = 0.1;

    private ProviderRawConfigurationParameter providerRawConfigurationParameter;

    private GenericCounterConfigurationModel configurationModel;

    private ObjectMapper objectMapper;

    private String deviceId;

    public CoapProviderConfigurationParameterResource(String deviceId, String name, ProviderRawConfigurationParameter providerRawConfigurationParameter) {
        super(name);

        if (deviceId != null && providerRawConfigurationParameter != null) {

            this.deviceId = deviceId;
            this.providerRawConfigurationParameter = providerRawConfigurationParameter;
            this.configurationModel = providerRawConfigurationParameter.getGenericCounterConfigurationModel();

            //Jackson Object Mapper + Ignore Null Fields in order to properly generate the SenML Payload
            this.objectMapper = new ObjectMapper();
            this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            //Set Observability
            setObservable(true);
            setObserveType(CoAP.Type.CON);

            //Set Resource Attributes
            getAttributes().setObservable();
            getAttributes().setTitle(OBJECT_TITLE);
            getAttributes().addAttribute("rt", providerRawConfigurationParameter.getType());
            getAttributes().addAttribute("if", CoreInterfaces.CORE_P.getValue());
            getAttributes().addAttribute("ct", Integer.toString(MediaTypeRegistry.TEXT_PLAIN));
            getAttributes().addAttribute("ct", Integer.toString(MediaTypeRegistry.APPLICATION_SENML_JSON));

            providerRawConfigurationParameter.addDataListener(new ResourceDataListener<GenericCounterConfigurationModel>() {
                @Override
                public void onDataChanged(SmartObjectResource<GenericCounterConfigurationModel> resource, GenericCounterConfigurationModel updatedValue) {
                    configurationModel = updatedValue;
                    changed();
                }
            });
        } else
            logger.error("Error -> NULL Raw Reference");
    }

    private Optional<String> getJsonSenmlPayload() {

        try {

            SenMLPack senMLPack = new SenMLPack();

            SenMLRecord baseRecord = new SenMLRecord();
            baseRecord.setBn(String.format("%s:%s", this.deviceId, this.getName()));
            baseRecord.setBver(SOFTWARE_VERSION);

            SenMLRecord maxConsRecord = new SenMLRecord();
            maxConsRecord.setN("max_consumption_value");
            maxConsRecord.setU(this.configurationModel.getRawMaterialMeasureUnit());
            maxConsRecord.setV(this.configurationModel.getMaxValueConsumption());

            senMLPack.add(baseRecord);
            senMLPack.add(maxConsRecord);

            return Optional.of(this.objectMapper.writeValueAsString(senMLPack));

        } catch (Exception e) {
            logger.error("Error generating SenML Record ! Msg: {}", e.getLocalizedMessage());
            return Optional.empty();
        }
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        try {
            if (exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_JSON
                    || exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_SENML_JSON) {

                Optional<String> senmlPayload = getJsonSenmlPayload();

                if (senmlPayload.isPresent())
                    exchange.respond(CoAP.ResponseCode.CONTENT, senmlPayload.get(), exchange.getRequestOptions().getAccept());
                else
                    exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);

            } else
                exchange.respond(CoAP.ResponseCode.CONTENT, this.objectMapper.writeValueAsString(configurationModel));

        } catch (Exception e) {
            e.printStackTrace();
            exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void handlePUT(CoapExchange exchange) {
        try {
            logger.info("Request Pretty Print:\n{}", Utils.prettyPrint(exchange.advanced().getRequest()));
            logger.info("Received PUT Request with body: {}", exchange.getRequestText());

            if (exchange.getRequestPayload() != null) {

                //The request value comes as String
                String receivedPayload = new String(exchange.getRequestPayload());

                //Deserializing DTO into proper inner class
                ChangeResourceParameterDescriptor changedParameter = this.objectMapper.readValue(receivedPayload, ChangeResourceParameterDescriptor.class);

                //Setting configuration model
                // changing Max Value Consumption parameter
                this.configurationModel.setMaxValueConsumption(changedParameter.getChangedValue());

                //Updating raw resource
                this.providerRawConfigurationParameter.setGenericCounterConfigurationModel(configurationModel);
                logger.info("Resource Status Updated: {}", this.configurationModel);

                exchange.respond(CoAP.ResponseCode.CHANGED);

            } else {
                logger.warn("Received a PUT request with an empty body !");
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            }

        } catch (Exception e) {
            logger.error("Error handling PUT request: {}", e.getLocalizedMessage());
            exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
}
