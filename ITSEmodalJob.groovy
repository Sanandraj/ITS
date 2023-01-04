package ITS

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.navis.argo.ArgoIntegrationEntity
import com.navis.argo.ArgoIntegrationField

/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.GeneralReference
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.IntegrationServiceField
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.business.atoms.IntegrationServiceTypeEnum
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.springframework.http.*
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Requirements:-
 */

class ITSEmodalJob extends AbstractGroovyJobCodeExtension {
    static {
        generateSASToken();
    }

    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSEmodalJob begin!!!!!")
        List<IntegrationServiceMessage> integrationServiceMessageList = findIntegrationServiceMessageEntities()
        LOGGER.debug("integrationServiceMessageList" + integrationServiceMessageList.size())
        for (IntegrationServiceMessage serviceMessage : integrationServiceMessageList) {
            if (serviceMessage.getIsmIntegrationService() != null && serviceMessage.getIsmIntegrationService().getIntservUrl() != null) {


                try {
                    processIntegrationMessage(serviceMessage)
                } catch (HttpClientErrorException exception) {
                    LOGGER.debug("HttpClientErrorException log" + exception)
                    generateSASToken();
                    processIntegrationMessage(serviceMessage);
                } catch (Exception exception) {
                    LOGGER.debug("exception log " + exception.printStackTrace())
                    LOGGER.debug("ISM name " + serviceMessage.getIsmIntegrationService().getIntservName())
                    serviceMessage.setIsmUserString1("Failed")
                    serviceMessage.setIsmUserString3("false")
                }

            }
        }
    }


    private void processIntegrationMessage(IntegrationServiceMessage serviceMessage) throws HttpClientErrorException {
        String errorMessage = null
        String ismServiceURL = serviceMessage.getIsmIntegrationService().getIntservUrl();
        if ("ITS_CTR_NOTIFICATION".equalsIgnoreCase(serviceMessage.getIsmIntegrationService().getIntservName())) {
            errorMessage = invokeEndpoint(Boolean.TRUE, serviceMessage.getIsmMessagePayload(), MediaType.APPLICATION_JSON, ismServiceURL, "LRORsa80Oq33wD_ASjfdiqEcopA08tYeeFf7K60NyVaS-pTl9Z-QD3HnmctTgQunUv-l5Mq2IrnMn_-b-QBJ3GtsJAPNakFfs1Xktb5SrOk6P8dcOFQaEvb6z1FT7IiddD-xzJTDjZncWRkG5uETOK_rYrQ8Bpz007gVbENBWcUotC5eaVVMGgw1zGKD6BAtck1zIpmE52vSMl5us9ocAnk0hGs-e0YDW7CRZA5bI7rihmx3OqnXUjwzK18ztHWht2Ts6eJvd-19TTkxVgvqxPu42JoBzQdLEsYz0eXKb5kC9r0O7kIusNOM9rPQW-QSEcD566ZmbkPLIKAm-czE9V4kcxB7D8eSL2sIanakblnbX51_oLR8PalbOSpEGitg0_SHzrrekJyQEaNY9F1zzwepowv0ZZXokhRmM9rTcRCIFg880ouc8kBOO1_G92ND9kkinc2Ki12KRIQ0BIqbvvEoGHU2FgJnNlzV2r0FNZqh7TQudtkt3xEedzmBimtu44claBLScb5CdCELzsqts4Y7VrZj5Di9p-QRYlUd8FO0J_iBTBp4QSA4aOwSfwwy86tzI4ro_h2bJejmUY9-ifHqsqPpasOcGMnUAuKqBsE", serviceMessage.getIsmEntityClass()?.getKey())
        } else {
            errorMessage = invokeEndpoint(Boolean.FALSE, serviceMessage.getIsmMessagePayload(), MediaType.APPLICATION_JSON, ismServiceURL, ACCESS_TOKEN, serviceMessage.getIsmEntityClass()?.getKey())
        }
        if (errorMessage == null) {
            serviceMessage.setIsmUserString1("Succeeded")
            serviceMessage.setIsmUserString2(null)
            serviceMessage.setIsmUserString3("true")
        } else {
            serviceMessage.setIsmUserString1("Failed")
            serviceMessage.setIsmUserString2(errorMessage)
            serviceMessage.setIsmUserString3("true")
        }


    }

    private static String generateSASToken() {
        IntegrationService integrationService = findIntegrationServicebyName("EMODAL_315");
        GeneralReference generalReference = GeneralReference.findUniqueEntryById("ITS", "EMODAL", "AUTH_DETAILS");
        if (generalReference != null && generalReference.getRefValue1() && generalReference.getRefValue2() != null && generalReference.getRefValue3() != null
                && integrationService != null && integrationService.getIntservUrl() != null) {
            String resourceUri = generalReference.getRefValue1()
            String keyName = generalReference.getRefValue2()
            String key = generalReference.getRefValue3()
            long epoch = System.currentTimeMillis() / 1000L;
            int week = 60 * 60 * 24 * 7;
            String expiry = Long.toString(epoch + week);

            try {
                String stringToSign = URLEncoder.encode(resourceUri, "UTF-8") + "\n" + expiry;
                String signature = getHMAC256(key, stringToSign);
                // doTrustToCertificates()
                ACCESS_TOKEN = "SharedAccessSignature sr=" + URLEncoder.encode(resourceUri, "UTF-8") + "&sig=" +
                        URLEncoder.encode(signature, "UTF-8") + "&se=" + expiry + "&skn=" + keyName;
            } catch (UnsupportedEncodingException e) {

                e.printStackTrace();
            }
            LOGGER.debug("Access token" + ACCESS_TOKEN)
            return ACCESS_TOKEN;

        }
    }




    private
    static String invokeEndpoint(Boolean isBearerTokenAuth, String postDataInput, MediaType mediaType, String endPoint, String sasSignature, String msgType) {
        // doTrustToCertificates()
        String messageType;
        if (!isBearerTokenAuth && !StringUtils.isEmpty(msgType)) {
            if (msgType.equalsIgnoreCase(LogicalEntityEnum.BKG.getKey())) {
                messageType = "booking"
            } else if (msgType.equalsIgnoreCase(LogicalEntityEnum.UNIT.getKey())) {
                messageType = "unit"
            }
        }
        String errorMessage;
        RestTemplate restTemplate = new RestTemplate(getClientHttpRequestFactory());
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            boolean hasError(ClientHttpResponse httpResponse) throws IOException {
                return (
                        httpResponse.getStatusCode().series() == HttpStatus.Series.CLIENT_ERROR
                                || httpResponse.getStatusCode().series() == HttpStatus.Series.SERVER_ERROR);
            }

            @Override
            void handleError(ClientHttpResponse httpResponse) throws IOException {
                if (httpResponse.getStatusCode()
                        .series() == HttpStatus.Series.SERVER_ERROR) {
                    LOGGER.debug("server error series" + httpResponse.getStatusText())
                    throw new HttpServerErrorException(httpResponse.getStatusCode(), httpResponse.getStatusText());
                } else if (httpResponse.getStatusCode()
                        .series() == HttpStatus.Series.CLIENT_ERROR) {
                    LOGGER.debug("client error series" + httpResponse.getStatusText())
                    if (httpResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                        errorMessage = IOUtils.toString(httpResponse.getBody(), StandardCharsets.UTF_8.name());
                    } else {
                        throw new HttpClientErrorException(httpResponse.getStatusCode(), httpResponse.getStatusText());
                    }
                }
            }
        })

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(mediaType);
        // LOGGER.debug("messageType in http headers " + messageType)

        httpHeaders.set("charset", "utf-8");
        if (!isBearerTokenAuth) {
            httpHeaders.set("messagetype", messageType)
            httpHeaders.set("Authorization", sasSignature);
        } else {
            httpHeaders.set("Authorization", "Bearer " + sasSignature);

        }
        byte[] postData = postDataInput.toString().getBytes(StandardCharsets.UTF_8);
        HttpEntity httpEntity = new HttpEntity<>(postData, httpHeaders);
        //disableSslVerification()
        // doTrustToCertificates()
        ResponseEntity<String> response = restTemplate.exchange(endPoint, HttpMethod.POST, httpEntity, String.class);
        // LOGGER.debug("status code " + response.getStatusCode())
        // LOGGER.debug("errorMessage " + errorMessage)
        if (errorMessage == null) {
            String result;
            ObjectMapper om = new ObjectMapper();
            try {
                //LOGGER.debug("response.getBody() " + response.getBody())
                if(response.getBody() != null){
                    return (om.readTree((String) response.getBody()).path("access_token").asText());
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (errorMessage != null) {
            JsonObject convertedObject = new Gson().fromJson(errorMessage, JsonObject.class);
            if (convertedObject != null && convertedObject.get("details") != null && convertedObject.get("details").getAsJsonArray().size() > 0) {
                JsonObject jsonObject = convertedObject.get("details").getAsJsonArray().get(0).getAsJsonObject();
                return jsonObject.get("reason");
            }
        }
        //
        return errorMessage;


    }

    private static SimpleClientHttpRequestFactory getClientHttpRequestFactory() {
        // doTrustToCertificates()
        SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
        //Connect timeout
        clientHttpRequestFactory.setConnectTimeout(2000);
        //Read timeout
        clientHttpRequestFactory.setReadTimeout(5000);
        return clientHttpRequestFactory;
    }

    public static String getHMAC256(String key, String input) {
        // doTrustToCertificates()
        Mac sha256_HMAC = null;
        String hash = null;
        try {
            sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            hash = new String(java.util.Base64.getEncoder().encode(sha256_HMAC.doFinal(input.getBytes("UTF-8"))));

        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return hash;
    }


    private static IntegrationService findIntegrationServicebyName(String inName) {
        DomainQuery dq = QueryUtils.createDomainQuery("IntegrationService")
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_NAME, inName))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_TYPE, IntegrationServiceTypeEnum.WEB_SERVICE))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_ACTIVE, Boolean.TRUE))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_DIRECTION, IntegrationServiceDirectionEnum.OUTBOUND))
        return (IntegrationService) Roastery.getHibernateApi().getUniqueEntityByDomainQuery(dq)
    }

    private static List<IntegrationServiceMessage> findIntegrationServiceMessageEntities() {
        DomainQuery domainQuery = QueryUtils.createDomainQuery(ArgoIntegrationEntity.INTEGRATION_SERVICE_MESSAGE)
        domainQuery.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("ismIntegrationService.intservType"), IntegrationServiceTypeEnum.WEB_SERVICE));
        domainQuery.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("ismIntegrationService.intservDirection"), IntegrationServiceDirectionEnum.OUTBOUND));
        domainQuery.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("ismIntegrationService.intservActive"), "true"));
        domainQuery.addDqPredicate(PredicateFactory.in(MetafieldIdFactory.valueOf("ismIntegrationService.intservName"), ["EMODAL_315", "ITS_CTR_NOTIFICATION"]));
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_USER_STRING3, "false"))
        domainQuery.addDqOrdering(Ordering.asc(ArgoIntegrationField.ISM_CREATED));

        return HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
    }
    private static final Logger LOGGER = Logger.getLogger(this.class);
    public static String ACCESS_TOKEN;
}
