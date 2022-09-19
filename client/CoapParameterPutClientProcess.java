package it.unimore.dipi.iot.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.dipi.iot.dto.ChangeResourceParameterRequestDescriptor;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Riccardo Prevedi
 * @created 10/09/2022 - 10:48
 * @project coap-smart-building
 */

public class CoapParameterPutClientProcess {

    private static final Logger logger = LoggerFactory.getLogger(CoapParameterPutClientProcess.class);

    private static final String COAP_ENDPOINT = "coap://192.168.10.43:5783/water-provider/parameter";

    //private static final String COAP_ENDPOINT = "coap://192.168.10.43:5783/gas-provider/parameter";

    private static final double NEW_MAX_VALUE  = 1.0;

    public static void main(String[] args) {

        ObjectMapper objectMapper = new ObjectMapper();

        //Initialize coapClient
        CoapClient coapClient = new CoapClient(COAP_ENDPOINT);

        //Request Class is a generic CoAP message: in this case we want a PUT.
        //"Message ID", "Token" and other header's fields can be set
        Request request = new Request(CoAP.Code.PUT);

        //Set PUT request's payload
        ChangeResourceParameterRequestDescriptor newParameter = new ChangeResourceParameterRequestDescriptor(NEW_MAX_VALUE);

        try {
            String myJsonPayload = objectMapper.writeValueAsString(newParameter);
            logger.info("PUT Request Payload: {}", myJsonPayload);
            request.setPayload(myJsonPayload);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        //Set Request as Confirmable
        request.setConfirmable(true);

        logger.info("Request Pretty Print: \n{}", Utils.prettyPrint(request));

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


    }
}
