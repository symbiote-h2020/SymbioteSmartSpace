/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.h2020.symbiote.ssp.rap.odata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import eu.h2020.symbiote.ssp.rap.RapConfig;
import eu.h2020.symbiote.ssp.rap.exceptions.CustomODataApplicationException;
import eu.h2020.symbiote.ssp.rap.interfaces.RapCommunicationHandler;
import eu.h2020.symbiote.ssp.rap.messages.plugin.RapPluginOkResponse;
import eu.h2020.symbiote.ssp.rap.messages.plugin.RapPluginResponse;
import eu.h2020.symbiote.ssp.rap.messages.resourceAccessNotification.SuccessfulAccessInfoMessage;
import eu.h2020.symbiote.ssp.resources.db.ResourcesRepository;
import eu.h2020.symbiote.ssp.resources.db.ResourceInfo;
import static eu.h2020.symbiote.ssp.rap.odata.RapEntityCollectionProcessor.setErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

import eu.h2020.symbiote.ssp.resources.db.SessionsRepository;
import org.apache.commons.io.IOUtils;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.processor.EntityProcessor;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author Luca Tomaselli <l.tomaselli@nextworks.it>
 */

@Component
public class RapEntityProcessor implements EntityProcessor{
    
    private static final Logger log = LoggerFactory.getLogger(RapEntityProcessor.class);
    
    @Autowired
    private ResourcesRepository resourcesRepo;
    
    @Autowired
    private SessionsRepository sessionsRepo;

    @Autowired
    private RapCommunicationHandler communicationHandler;
    
    @Autowired
    private RestTemplate restTemplate;

    private OData odata;

    private StorageHelper storageHelper;


    @Override
    public void init(OData odata, ServiceMetadata sm) {
        this.odata = odata;
    //    this.serviceMetadata = sm;
        storageHelper = new StorageHelper(resourcesRepo, sessionsRepo,
                communicationHandler, restTemplate, RapConfig.JSON_PROPERTY_CLASS_NAME);
    }

    @Override
    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        try {
            InputStream stream = null;
            CustomODataApplicationException customOdataException = null;

            ObjectMapper map = new ObjectMapper();
            map.configure(SerializationFeature.INDENT_OUTPUT, true);

            // 1st retrieve the requested EntitySet from the uriInfo
            List<UriResource> resourceParts = uriInfo.getUriResourceParts();
            UriResource uriResource = resourceParts.get(0); // the first segment is the EntitySet
            if (!(uriResource instanceof UriResourceEntitySet)) {
                customOdataException = new CustomODataApplicationException(null,"Only EntitySet is supported", 
                        HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
                //throw customOdataException;
                    RapEntityCollectionProcessor.setErrorResponse(response, customOdataException, responseFormat);
                    return;
            }

            UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
            List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
            ResourceInfo resource = storageHelper.getResourceInfo(keyPredicates);
            if (resource == null) {
                log.error("Entity not found");
                customOdataException = new CustomODataApplicationException(null, "Entity not found.", 
                                                                        HttpStatusCode.NO_CONTENT.getStatusCode(), Locale.ROOT);
                RapEntityCollectionProcessor.setErrorResponse(response, customOdataException, responseFormat);
                return;
            }

            String symbioteId = resource.getSymIdResource();
            if(symbioteId == null && symbioteId.isEmpty()) {
                symbioteId = resource.getSspIdResource();
            }
            if (symbioteId == null || symbioteId.isEmpty()) {
                log.error("No valid symbioteId for entity");
                customOdataException = new CustomODataApplicationException(null, "No valid symbioteId for entity.",
                        HttpStatusCode.NO_CONTENT.getStatusCode(), Locale.ROOT);
                RapEntityCollectionProcessor.setErrorResponse(response, customOdataException, responseFormat);
                return;
            }
            if(!storageHelper.checkAccessPolicies(request, symbioteId)) {
                log.error("Access policy check error" );
                customOdataException = new CustomODataApplicationException(resource.getSymIdResource(), "Access policy check error",
                        HttpStatusCode.UNAUTHORIZED.getStatusCode(), Locale.ROOT);
                setErrorResponse(response, customOdataException, responseFormat);
                return;
            }

            try {
                map.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
                String json = map.writeValueAsString(resource);
                stream = new ByteArrayInputStream(json.getBytes("UTF-8"));
            } catch (JsonProcessingException e) {
                log.error(e.getMessage());
            } catch (UnsupportedEncodingException ex) {
                log.error(ex.getMessage());
            }

            if(customOdataException == null && stream != null)
                communicationHandler.sendSuccessfulAccessMessage(resource.getSymIdResource(),SuccessfulAccessInfoMessage.AccessType.NORMAL.name());

            // 4th: configure the response object: set the body, headers and status code
            response.setContent(stream);
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.UNAUTHORIZED.getStatusCode(), Locale.ROOT);
        }
    }
    
    @Override
    public void createEntity(ODataRequest odr, ODataResponse odr1, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        CustomODataApplicationException customOdataException = null;
        String symbioteId = null;
        try {
            String body = null;
            ArrayList<String> typeNameList = new ArrayList();
            // 1st retrieve the requested EntitySet from the uriInfo
            List<UriResource> resourceParts = uriInfo.getUriResourceParts();
            int segmentCount = resourceParts.size();

            UriResource uriResource = resourceParts.get(0); // the first segment is the EntitySet
            if (!(uriResource instanceof UriResourceEntitySet)) {
                customOdataException = new CustomODataApplicationException(null,"Only EntitySet is supported", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
                RapEntityCollectionProcessor.setErrorResponse(response, customOdataException, responseFormat);
                return;
            }

            InputStream requestInputStream = request.getBody();
            UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
            EdmEntitySet startEdmEntitySet = uriResourceEntitySet.getEntitySet();
            EdmEntityType startEntityType = startEdmEntitySet.getEntityType();


            EdmEntityType targetEntityType;
            String typeName = startEntityType.getName();
            typeNameList.add(typeName);

            if (segmentCount > 1) {
                for (int i = 1; i < segmentCount; i++) {
                    UriResource segment = resourceParts.get(i);
                    if (segment instanceof UriResourceNavigation) {
                        UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) segment;
                        EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                        targetEntityType = edmNavigationProperty.getType();
                        String typeNameEntity = targetEntityType.getName();
                        typeNameList.add(typeNameEntity);
                    }
                }
            }

            List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
            try { 
                body = IOUtils.toString(requestInputStream, "UTF-8");
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(RapEntityProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }        
            ArrayList<ResourceInfo> resourceInfoList;
            try {
                resourceInfoList = storageHelper.getResourceInfoList(typeNameList,keyPredicates);
                for(ResourceInfo resourceInfo: resourceInfoList){
                    String symbioteIdTemp = resourceInfo.getSymIdResource();
                    if(symbioteIdTemp != null && !symbioteIdTemp.isEmpty()) {
                        symbioteId = symbioteIdTemp;
                        break;
                    } else {
                        symbioteIdTemp = resourceInfo.getSspIdResource();
                        if(symbioteIdTemp != null && !symbioteIdTemp.isEmpty()) {
                            symbioteId = symbioteIdTemp;
                            break;
                        }
                    }
                }
            } catch(ODataApplicationException odataExc){
                log.error(odataExc.getMessage(), odataExc);
                customOdataException = new CustomODataApplicationException(null,"Entity not found.", HttpStatusCode.NO_CONTENT.getStatusCode(), Locale.ROOT);
                RapEntityCollectionProcessor.setErrorResponse(response, customOdataException, responseFormat);
                return;
            }

            if(!storageHelper.checkAccessPolicies(request, symbioteId)) {
                log.error("Access policy check error" );
                customOdataException = new CustomODataApplicationException(symbioteId, "Access policy check error",
                        HttpStatusCode.UNAUTHORIZED.getStatusCode(), Locale.ROOT);
                setErrorResponse(response, customOdataException, responseFormat);
                return;
            }
            RapPluginResponse rapPluginResponse = storageHelper.setService(resourceInfoList, body);
            if(customOdataException == null && rapPluginResponse instanceof RapPluginOkResponse)
                communicationHandler.sendSuccessfulAccessMessage(symbioteId, SuccessfulAccessInfoMessage.AccessType.NORMAL.name());

            // 4th: configure the response object: set the body, headers and status code
            //response.setContent(serializerResult.getContent());
            response.setContent(new ByteArrayInputStream(rapPluginResponse.getContent().getBytes(StandardCharsets.UTF_8)));
            response.setStatusCode(rapPluginResponse.getResponseCode());
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (ODataApplicationException odataExc) {
            log.error("Resource ID has not been served. Cause:\n" + odataExc.getMessage(), odataExc);
            customOdataException = new CustomODataApplicationException(symbioteId, odataExc.getMessage(),
                    odataExc.getStatusCode(), odataExc.getLocale());
            //throw customOdataException;
            setErrorResponse(response, customOdataException, responseFormat);
            return;
        } catch (Exception ex) {
            log.error("Generic Error", ex);
            customOdataException = new CustomODataApplicationException(symbioteId, ex.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ROOT);
            setErrorResponse(response, customOdataException, responseFormat);
            return;
        }
    }

    @Override
    public void deleteEntity(ODataRequest odr, ODataResponse odr1, UriInfo ui) throws ODataApplicationException, ODataLibraryException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
